package com.partnerportal.service.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.partnerportal.service.api.ApiException;
import com.partnerportal.service.api.ValidationException;
import com.partnerportal.service.bootstrap.RoleBootstrap;
import com.partnerportal.service.domain.RegistrationDraft.Company;
import com.partnerportal.service.domain.RegistrationDraft.Contact;
import com.partnerportal.service.domain.RegistrationDraft.Document;
import com.partnerportal.service.domain.RegistrationDraft.PosLocation;
import com.partnerportal.service.domain.RegistrationSessions.Session;
import com.partnerportal.service.liferay.Entries;
import com.partnerportal.service.liferay.Fields;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * US-002: the guest registration wizard behind {@code /public/registration}.
 * Guests never reach Liferay (CLAUDE.md rule 5): every read and write here
 * goes through the headless-server app after the invitation token or the
 * registration session has been checked.
 */
@Service
public class RegistrationService {

	public static final Set<String> CONTACT_ROLES = Collections.unmodifiableSet(
		new HashSet<>(
			Arrays.asList("primary", "finance", "technical", "other")));

	public static final Set<ApplicationStatus> EDITABLE = Collections.unmodifiableSet(
		EnumSet.of(
			ApplicationStatus.REGISTRATION_IN_PROGRESS,
			ApplicationStatus.REVISION_REQUESTED));

	public static final String INVALID_LINK_MESSAGE =
		"This link is no longer valid. Ask your account manager for a new " +
			"invitation.";

	public static final int MAX_TEXT_LENGTH = 280;

	public static final Set<String> POS_TYPES = Collections.unmodifiableSet(
		new HashSet<>(Arrays.asList("store", "warehouse", "online", "other")));

	/**
	 * After submit the link opens the wizard read-only (AC11) while the
	 * application is in review.
	 */
	public static final Set<ApplicationStatus> READ_ONLY = Collections.unmodifiableSet(
		EnumSet.of(
			ApplicationStatus.SUBMITTED, ApplicationStatus.APPROVAL_PENDING,
			ApplicationStatus.UNDER_LEGAL_REVIEW,
			ApplicationStatus.CONTRACT_PENDING));

	public static final List<String> REQUIRED_DOCUMENT_TYPES =
		Collections.unmodifiableList(
			Arrays.asList("tradeRegister", "vatCertificate"));

	public static final String SESSION_ENDED_MESSAGE =
		"Your session has ended. Open the link from your invitation email " +
			"again.";

	/**
	 * Document types a partner may upload; {@code signedContract} is legal's.
	 */
	public static final Set<String> UPLOAD_DOCUMENT_TYPES =
		Collections.unmodifiableSet(
			new LinkedHashSet<>(
				Arrays.asList(
					"tradeRegister", "vatCertificate", "bankLetter",
					"certificate", "other")));

	public RegistrationService(
		PartnerRepository partnerRepository, StatusService statusService,
		SettingsService settingsService, TokenService tokenService,
		RegistrationSessions registrationSessions, DocumentStore documentStore,
		ReviewRouter reviewRouter, Mailer mailer, UserDirectory userDirectory,
		AlertService alertService, Links links, Clock clock,
		@Value(
			"${partner.registration.categories:networking=Networking,security=Security,storage=Storage,servers=Servers,software=Software,services=Services}"
		)
		String categories) {

		_partnerRepository = partnerRepository;
		_statusService = statusService;
		_settingsService = settingsService;
		_tokenService = tokenService;
		_registrationSessions = registrationSessions;
		_documentStore = documentStore;
		_reviewRouter = reviewRouter;
		_mailer = mailer;
		_userDirectory = userDirectory;
		_alertService = alertService;
		_links = links;
		_clock = clock;
		_categories = _parseCategories(categories);
	}

	public void deleteDocument(String sessionToken, long documentId) {
		Context context = _context(sessionToken, true);

		_ownChild(ChildType.DOCUMENT, context, documentId);

		_partnerRepository.deleteChild(ChildType.DOCUMENT, documentId);
	}

	public Map<String, String> getCategories() {
		return _categories;
	}

	/**
	 * AC1: checks the link and starts a session. The first valid open moves
	 * the application to {@code registrationInProgress}.
	 */
	public Map<String, Object> open(String token) {
		if ((token == null) || token.isEmpty() || (token.length() > 100)) {
			throw _invalidLink();
		}

		String tokenHash = _tokenService.hash(token);

		ObjectNode invitation = _partnerRepository.findInvitationByTokenHash(
			tokenHash);

		if ((invitation == null) ||
			!_tokenService.matches(
				token, Entries.text(invitation, "tokenHash"))) {

			throw _invalidLink();
		}

		ObjectNode application = _partnerRepository.findApplicationByInvitation(
			Entries.id(invitation));

		if (application == null) {
			throw _invalidLink();
		}

		ApplicationStatus applicationStatus = _statusService.getStatus(
			application);

		Mode mode = _mode(invitation, applicationStatus);

		if (mode == Mode.INVALID) {
			throw _invalidLink();
		}

		if (applicationStatus == ApplicationStatus.INVITATION_SENT) {

			// Two opens at once (two tabs, or the element mounted twice) must
			// record one change

			_statusService.transitionIfCurrent(
				Entries.id(application), ApplicationStatus.INVITATION_SENT,
				ApplicationStatus.REGISTRATION_IN_PROGRESS, 0,
				"Partner opened the registration link");

			application = _partnerRepository.getApplication(
				Entries.id(application));
		}

		Context context = new Context(invitation, application, mode);

		Map<String, Object> result = _response(context);

		ObjectNode invitee = _objectMapper.createObjectNode();

		invitee.put("email", Entries.text(invitation, "email"));
		invitee.put("firstName", Entries.text(invitation, "firstName"));
		invitee.put("lastName", Entries.text(invitation, "lastName"));

		result.put("categories", _categories);
		result.put("expiresAt", Entries.text(invitation, "expiresAt"));
		result.put("invitee", invitee);
		result.put("maxUploadMb", _settingsService.get().getMaxUploadMb());

		return result;
	}

	/**
	 * AC3: stores the draft. Validation is light here (types, lengths, picklist
	 * keys); completeness is checked on submit. Empty contact and location
	 * rows are not stored. A started row needs the fields Liferay requires
	 * on the child object. Everything is checked before the first write, so a
	 * refused save changes nothing.
	 */
	public Map<String, Object> saveDraft(
		String sessionToken, RegistrationDraft draft) {

		Context context = _context(sessionToken, true);

		if (draft == null) {
			throw ApiException.badRequest("Nothing to save");
		}

		List<Map<String, String>> errors = new ArrayList<>();

		_checkDraft(draft, errors);

		if (errors.isEmpty()) {

			// Indexes in errors are the rows as the partner sees them, so
			// check before the empty rows are dropped

			_checkStoredRequired(draft, errors);

			draft.contacts.removeIf(RegistrationService::_isBlankContact);
			draft.posLocations.removeIf(RegistrationService::_isBlankPos);
		}

		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}

		long applicationId = Entries.id(context.application);

		_partnerRepository.patchApplication(
			applicationId, _applicationFields(draft, context.application));

		_syncContacts(applicationId, draft.contacts);
		_syncPosLocations(applicationId, draft.posLocations);

		context.application = _partnerRepository.getApplication(applicationId);

		return _response(context);
	}

	public Map<String, Object> session(String sessionToken) {
		return _response(_context(sessionToken, false));
	}

	/**
	 * AC10: validates everything, moves the application to {@code submitted},
	 * sends {@code PM_SUBMITTED} and routes it to review.
	 */
	public Map<String, Object> submit(String sessionToken) {
		Context context = _context(sessionToken, true);

		long applicationId = Entries.id(context.application);

		RegistrationDraft draft = _draft(context.application);

		List<Map<String, String>> errors = validateForSubmit(draft);

		if (!errors.isEmpty()) {
			throw new ValidationException(errors);
		}

		_statusService.transition(
			applicationId, ApplicationStatus.SUBMITTED, 0,
			"Submitted by the partner",
			Collections.singletonMap(
				"submittedAt", Entries.dateTime(Instant.now(_clock))));

		_partnerRepository.patchInvitation(
			Entries.id(context.invitation),
			Collections.singletonMap("state", InvitationService.STATE_USED));

		ObjectNode application = _partnerRepository.getApplication(
			applicationId);

		_sendSubmitted(context.invitation, application);

		try {
			_reviewRouter.routeSubmitted(applicationId);
		}
		catch (RuntimeException runtimeException) {
			_alertService.alert(
				"Submitted application not routed",
				"Application " + applicationId +
					" was submitted but could not be moved to review. Move " +
						"it from submitted manually.",
				runtimeException);
		}

		context.application = _partnerRepository.getApplication(applicationId);
		context.invitation = _partnerRepository.getInvitation(
			Entries.id(context.invitation));
		context.mode = Mode.READ_ONLY;

		return _response(context);
	}

	/**
	 * AC8: stores one document. The type is sniffed from the content; the
	 * size limit is {@code PartnerSettings.maxUploadMb}.
	 */
	public Map<String, Object> uploadDocument(
		String sessionToken, String documentType, String note,
		String fileName, byte[] bytes) {

		Context context = _context(sessionToken, true);

		if (!UPLOAD_DOCUMENT_TYPES.contains(documentType)) {
			throw ApiException.badRequest("Choose a document type");
		}

		if ((bytes == null) || (bytes.length == 0)) {
			throw ApiException.badRequest("The file is empty");
		}

		PartnerSettings partnerSettings = _settingsService.get();

		if (bytes.length > partnerSettings.getMaxUploadBytes()) {
			throw new ApiException(
				HttpStatus.PAYLOAD_TOO_LARGE,
				"The file is larger than " + partnerSettings.getMaxUploadMb() +
					" MB");
		}

		FileTypes fileType = FileTypes.sniff(bytes);

		if (fileType == null) {
			throw new ApiException(
				HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				"Upload a PDF, JPG or PNG file");
		}

		if ((note != null) && (note.length() > 2000)) {
			throw ApiException.badRequest("The note is too long");
		}

		String safeFileName = fileType.fileName(fileName);

		long temporaryId = _documentStore.uploadTemporary(
			safeFileName, fileType.getContentType(), bytes);

		try {
			Map<String, Object> fields = new LinkedHashMap<>();

			fields.put("documentType", documentType);
			fields.put("file", temporaryId);
			fields.put("note", _trimToNull(note));
			fields.put(
				ChildType.DOCUMENT.applicationField(),
				Entries.id(context.application));

			_partnerRepository.createChild(ChildType.DOCUMENT, fields);
		}
		finally {
			try {
				_documentStore.deleteTemporary(temporaryId);
			}
			catch (RuntimeException runtimeException) {
				_log.warn(
					"Unable to delete temporary upload " + temporaryId,
					runtimeException);
			}
		}

		return _response(context);
	}

	/**
	 * Every rule a submitted application must meet (AC4 to AC8).
	 */
	public List<Map<String, String>> validateForSubmit(RegistrationDraft draft) {
		List<Map<String, String>> errors = new ArrayList<>();

		_checkDraft(draft, errors);

		if (_isBlank(draft.questionnaire.get("businessType"))) {
			errors.add(
				ValidationException.error(
					"questionnaire", "businessType",
					"Tell us what kind of business you are."));
		}

		Company company = draft.company;

		_require(
			errors, "company", "companyName", company.companyName,
			"Enter the legal company name.");
		_require(
			errors, "company", "registrationNumber", company.registrationNumber,
			"Enter the company registration number.");
		_require(
			errors, "company", "vatId", company.vatId, "Enter the VAT ID.");
		_require(
			errors, "company", "street", company.street,
			"Enter the street address.");
		_require(errors, "company", "city", company.city, "Enter the city.");
		_require(
			errors, "company", "postalCode", company.postalCode,
			"Enter the postal code.");
		_require(
			errors, "company", "website", company.website,
			"Enter the company website.");
		_require(
			errors, "company", "employeeBand", company.employeeBand,
			"Choose the number of employees.");

		if (!_isCountry(company.country)) {
			errors.add(
				ValidationException.error(
					"company", "country", "Choose the country."));
		}

		boolean primary = false;

		for (int i = 0; i < draft.contacts.size(); i++) {
			Contact contact = draft.contacts.get(i);

			_require(
				errors, "contacts", "contacts[" + i + "].fullName",
				contact.fullName, "Enter the contact's full name.");

			if (!_isEmail(contact.email)) {
				errors.add(
					ValidationException.error(
						"contacts", "contacts[" + i + "].email",
						"Enter a valid email address."));
			}

			if ("primary".equals(contact.role)) {
				primary = true;
			}
		}

		if (!primary) {
			errors.add(
				ValidationException.error(
					"contacts", "contacts",
					"Add at least one primary contact."));
		}

		if (!draft.compliance.sanctionsDeclaration) {
			errors.add(
				ValidationException.error(
					"compliance", "sanctionsDeclaration",
					"Confirm the sanctions declaration."));
		}

		if (!draft.compliance.gdprConsent) {
			errors.add(
				ValidationException.error(
					"compliance", "gdprConsent",
					"Give your consent to the processing of your data."));
		}

		if (draft.posLocations.isEmpty()) {
			errors.add(
				ValidationException.error(
					"pos", "posLocations", "Add at least one location."));
		}

		for (int i = 0; i < draft.posLocations.size(); i++) {
			PosLocation posLocation = draft.posLocations.get(i);

			String prefix = "posLocations[" + i + "].";

			_require(
				errors, "pos", prefix + "name", posLocation.name,
				"Enter the location name.");
			_require(
				errors, "pos", prefix + "street", posLocation.street,
				"Enter the street address.");
			_require(
				errors, "pos", prefix + "city", posLocation.city,
				"Enter the city.");

			if (!_isCountry(posLocation.country)) {
				errors.add(
					ValidationException.error(
						"pos", prefix + "country", "Choose the country."));
			}

			if (_isBlank(posLocation.posType)) {
				errors.add(
					ValidationException.error(
						"pos", prefix + "posType",
						"Choose the location type."));
			}

			if (posLocation.categories.isEmpty()) {
				errors.add(
					ValidationException.error(
						"pos", prefix + "categories",
						"Choose at least one product category."));
			}
		}

		Set<String> documentTypes = new HashSet<>();

		for (Document document : draft.documents) {
			documentTypes.add(document.documentType);
		}

		for (String requiredType : REQUIRED_DOCUMENT_TYPES) {
			if (!documentTypes.contains(requiredType)) {
				errors.add(
					ValidationException.error(
						"documents", requiredType,
						"tradeRegister".equals(requiredType) ?
							"Upload the trade register extract." :
								"Upload the VAT certificate."));
			}
		}

		return errors;
	}

	public enum Mode {

		EDIT, INVALID, READ_ONLY

	}

	private static boolean _isBlank(String value) {
		return (value == null) || value.trim().isEmpty();
	}

	/**
	 * A contact row the partner has not started; the role alone does not
	 * count, the wizard preselects it.
	 */
	private static boolean _isBlankContact(Contact contact) {
		return _isBlank(contact.email) && _isBlank(contact.fullName) &&
			   _isBlank(contact.jobTitle) && _isBlank(contact.phone);
	}

	private static boolean _isBlankPos(PosLocation posLocation) {
		return _isBlank(posLocation.city) && _isBlank(posLocation.country) &&
			   _isBlank(posLocation.name) && _isBlank(posLocation.posType) &&
			   _isBlank(posLocation.postalCode) &&
			   _isBlank(posLocation.street) &&
			   ((posLocation.categories == null) ||
				posLocation.categories.isEmpty());
	}

	/**
	 * Fields required by the ApplicationContact and POSLocation objects in
	 * {@code docs/data-model.md}; Liferay refuses a row without them.
	 */
	private static void _checkStoredRequired(
		RegistrationDraft draft, List<Map<String, String>> errors) {

		for (int i = 0; i < draft.contacts.size(); i++) {
			Contact contact = draft.contacts.get(i);

			if (_isBlankContact(contact)) {
				continue;
			}

			_require(
				errors, "contacts", "contacts[" + i + "].fullName",
				contact.fullName,
				"Enter the contact's full name, or remove the contact.");
			_require(
				errors, "contacts", "contacts[" + i + "].email", contact.email,
				"Enter the contact's email, or remove the contact.");
		}

		for (int i = 0; i < draft.posLocations.size(); i++) {
			PosLocation posLocation = draft.posLocations.get(i);

			if (_isBlankPos(posLocation)) {
				continue;
			}

			_require(
				errors, "pos", "posLocations[" + i + "].name", posLocation.name,
				"Enter the location name, or remove the location.");
		}
	}

	private static boolean _isCountry(String value) {
		return (value != null) && _COUNTRY_PATTERN.matcher(value).matches();
	}

	private static boolean _isEmail(String value) {
		return (value != null) && _EMAIL_PATTERN.matcher(value.trim()).matches();
	}

	/**
	 * VAT and registration numbers compared without spaces, dots, dashes or
	 * case.
	 */
	private static String _normalizeId(String value) {
		if (value == null) {
			return null;
		}

		String normalized = value.replaceAll(
			"[\\s.\\-/]", ""
		).toUpperCase(
			Locale.ROOT
		);

		return normalized.isEmpty() ? null : normalized;
	}

	private static Map<String, String> _parseCategories(String categories) {
		Map<String, String> map = new LinkedHashMap<>();

		if (categories == null) {
			return map;
		}

		for (String part : categories.split(",")) {
			String[] keyAndLabel = part.split("=", 2);

			String key = keyAndLabel[0].trim();

			if (key.isEmpty()) {
				continue;
			}

			map.put(
				key,
				(keyAndLabel.length > 1) ? keyAndLabel[1].trim() : key);
		}

		return Collections.unmodifiableMap(map);
	}

	private static void _require(
		List<Map<String, String>> errors, String step, String field,
		String value, String message) {

		if (_isBlank(value)) {
			errors.add(ValidationException.error(step, field, message));
		}
	}

	private static String _trimToNull(String value) {
		if (value == null) {
			return null;
		}

		String trimmed = value.trim();

		return trimmed.isEmpty() ? null : trimmed;
	}

	private Map<String, Object> _applicationFields(
		RegistrationDraft draft, ObjectNode application) {

		Map<String, Object> fields = new LinkedHashMap<>();

		Company company = draft.company;

		CompanyMatch companyMatch = _lookupCompany(
			Entries.id(application), company.vatId,
			company.registrationNumber);

		if (companyMatch.existing != null) {

			// AC9: the company step is read-only; its data comes from the
			// authorized application of the same company

			ObjectNode existing = companyMatch.existing;

			for (String field : _COMPANY_FIELDS) {
				fields.put(field, Entries.text(existing, field));
			}

			fields.put("existingCompany", true);
			fields.put(
				Fields.APPLICATION_ACCOUNT,
				Entries.longValue(existing, Fields.APPLICATION_ACCOUNT));
		}
		else {
			fields.put("city", _trimToNull(company.city));
			fields.put("companyName", _trimToNull(company.companyName));
			fields.put(
				"country",
				(company.country == null) ? null :
					company.country.trim(
					).toUpperCase(
						Locale.ROOT
					));
			fields.put("employeeBand", _trimToNull(company.employeeBand));
			fields.put("postalCode", _trimToNull(company.postalCode));
			fields.put(
				"registrationNumber", _trimToNull(company.registrationNumber));
			fields.put("street", _trimToNull(company.street));
			fields.put("vatId", _trimToNull(company.vatId));
			fields.put("website", _trimToNull(company.website));

			if (Entries.bool(application, "existingCompany")) {
				fields.put("existingCompany", false);
				fields.put(Fields.APPLICATION_ACCOUNT, 0);
			}
		}

		if (companyMatch.terminated) {

			// Silent for the partner; the AM sees the flag in review

			fields.put("watchlistMatch", true);
		}

		fields.put("certifications", _trimToNull(draft.compliance.certifications));
		fields.put(
			"complianceNotes", _trimToNull(draft.compliance.complianceNotes));
		fields.put("gdprConsent", draft.compliance.gdprConsent);
		fields.put(
			"sanctionsDeclaration", draft.compliance.sanctionsDeclaration);

		try {
			fields.put(
				"questionnaire",
				_objectMapper.writeValueAsString(draft.questionnaire));
		}
		catch (JsonProcessingException jsonProcessingException) {
			throw new IllegalStateException(jsonProcessingException);
		}

		Set<String> requestedCategories = new LinkedHashSet<>();

		for (PosLocation posLocation : draft.posLocations) {
			requestedCategories.addAll(posLocation.categories);
		}

		fields.put("requestedCategories", String.join(",", requestedCategories));

		return fields;
	}

	private void _checkDraft(
		RegistrationDraft draft, List<Map<String, String>> errors) {

		if (draft.company == null) {
			draft.company = new Company();
		}

		if (draft.compliance == null) {
			draft.compliance = new RegistrationDraft.Compliance();
		}

		if (draft.contacts == null) {
			draft.contacts = new ArrayList<>();
		}

		if (draft.documents == null) {
			draft.documents = new ArrayList<>();
		}

		if (draft.posLocations == null) {
			draft.posLocations = new ArrayList<>();
		}

		if (draft.questionnaire == null) {
			draft.questionnaire = new LinkedHashMap<>();
		}

		if ((draft.contacts.size() > 20) || (draft.posLocations.size() > 50) ||
			(draft.questionnaire.size() > 30)) {

			errors.add(
				ValidationException.error(
					null, null, "Too many entries in the draft."));

			return;
		}

		Company company = draft.company;

		_checkLength(errors, "company", "companyName", company.companyName);
		_checkLength(
			errors, "company", "registrationNumber",
			company.registrationNumber);
		_checkLength(errors, "company", "vatId", company.vatId);
		_checkLength(errors, "company", "street", company.street);
		_checkLength(errors, "company", "city", company.city);
		_checkLength(errors, "company", "postalCode", company.postalCode);
		_checkLength(errors, "company", "website", company.website);
		_checkLength(errors, "company", "employeeBand", company.employeeBand);

		if (!_isBlank(company.country) && !_isCountry(company.country)) {
			errors.add(
				ValidationException.error(
					"company", "country", "Choose the country."));
		}

		for (Map.Entry<String, String> answer :
				draft.questionnaire.entrySet()) {

			_checkLength(
				errors, "questionnaire", answer.getKey(), answer.getValue());
		}

		for (int i = 0; i < draft.contacts.size(); i++) {
			Contact contact = draft.contacts.get(i);

			String prefix = "contacts[" + i + "].";

			_checkLength(
				errors, "contacts", prefix + "fullName", contact.fullName);
			_checkLength(errors, "contacts", prefix + "email", contact.email);
			_checkLength(errors, "contacts", prefix + "phone", contact.phone);
			_checkLength(
				errors, "contacts", prefix + "jobTitle", contact.jobTitle);

			if ((contact.role != null) &&
				!CONTACT_ROLES.contains(contact.role)) {

				errors.add(
					ValidationException.error(
						"contacts", prefix + "role", "Choose a role."));
			}
		}

		for (int i = 0; i < draft.posLocations.size(); i++) {
			PosLocation posLocation = draft.posLocations.get(i);

			String prefix = "posLocations[" + i + "].";

			_checkLength(errors, "pos", prefix + "name", posLocation.name);
			_checkLength(errors, "pos", prefix + "street", posLocation.street);
			_checkLength(errors, "pos", prefix + "city", posLocation.city);
			_checkLength(
				errors, "pos", prefix + "postalCode", posLocation.postalCode);

			if (!_isBlank(posLocation.country) &&
				!_isCountry(posLocation.country)) {

				errors.add(
					ValidationException.error(
						"pos", prefix + "country", "Choose the country."));
			}

			if ((posLocation.posType != null) &&
				!POS_TYPES.contains(posLocation.posType)) {

				errors.add(
					ValidationException.error(
						"pos", prefix + "posType",
						"Choose the location type."));
			}

			if (posLocation.categories == null) {
				posLocation.categories = new ArrayList<>();
			}

			for (String category : posLocation.categories) {
				if (!_categories.containsKey(category)) {
					errors.add(
						ValidationException.error(
							"pos", prefix + "categories",
							"Choose categories from the list."));

					break;
				}
			}
		}

		if ((draft.compliance.certifications != null) &&
			(draft.compliance.certifications.length() > 4000)) {

			errors.add(
				ValidationException.error(
					"compliance", "certifications", "This text is too long."));
		}

		if ((draft.compliance.complianceNotes != null) &&
			(draft.compliance.complianceNotes.length() > 4000)) {

			errors.add(
				ValidationException.error(
					"compliance", "complianceNotes", "This text is too long."));
		}
	}

	private void _checkLength(
		List<Map<String, String>> errors, String step, String field,
		String value) {

		if ((value != null) && (value.length() > MAX_TEXT_LENGTH)) {
			errors.add(
				ValidationException.error(
					step, field,
					"Use at most " + MAX_TEXT_LENGTH + " characters."));
		}
	}

	/**
	 * Checks the session against the invitation and application as they are
	 * now, so a resend, an expiry or a submit ends write access at once.
	 */
	private Context _context(String sessionToken, boolean write) {
		Session session = _registrationSessions.verify(sessionToken);

		if (session == null) {
			throw new ApiException(
				HttpStatus.UNAUTHORIZED,
				SESSION_ENDED_MESSAGE);
		}

		ObjectNode invitation;
		ObjectNode application;

		try {
			invitation = _partnerRepository.getInvitation(
				session.invitationId);
			application = _partnerRepository.getApplication(
				session.applicationId);
		}
		catch (RuntimeException runtimeException) {
			throw _invalidLink();
		}

		if (!session.isFor(Entries.text(invitation, "tokenHash")) ||
			(Entries.longValue(application, Fields.APPLICATION_INVITATION) !=
				session.invitationId)) {

			throw _invalidLink();
		}

		Mode mode = _mode(invitation, _statusService.getStatus(application));

		if (mode == Mode.INVALID) {
			throw _invalidLink();
		}

		if (write && (mode != Mode.EDIT)) {
			throw ApiException.conflict(
				"This application has been submitted and can no longer be " +
					"changed.");
		}

		return new Context(invitation, application, mode);
	}

	private RegistrationDraft _draft(ObjectNode application) {
		RegistrationDraft draft = new RegistrationDraft();

		Company company = draft.company;

		company.city = Entries.text(application, "city");
		company.companyName = Entries.text(application, "companyName");
		company.country = Entries.text(application, "country");
		company.employeeBand = Entries.text(application, "employeeBand");
		company.postalCode = Entries.text(application, "postalCode");
		company.registrationNumber = Entries.text(
			application, "registrationNumber");
		company.street = Entries.text(application, "street");
		company.vatId = Entries.text(application, "vatId");
		company.website = Entries.text(application, "website");

		draft.compliance.certifications = Entries.text(
			application, "certifications");
		draft.compliance.complianceNotes = Entries.text(
			application, "complianceNotes");
		draft.compliance.gdprConsent = Entries.bool(application, "gdprConsent");
		draft.compliance.sanctionsDeclaration = Entries.bool(
			application, "sanctionsDeclaration");

		draft.existingCompany = Entries.bool(application, "existingCompany");

		String questionnaire = Entries.text(application, "questionnaire");

		if (!_isBlank(questionnaire)) {
			try {
				draft.questionnaire = _objectMapper.readValue(
					questionnaire, new TypeReference<Map<String, String>>() {
					});
			}
			catch (JsonProcessingException jsonProcessingException) {
				_log.warn(
					"Unreadable questionnaire on application " +
						Entries.id(application),
					jsonProcessingException);
			}
		}

		long applicationId = Entries.id(application);

		for (ObjectNode entry :
				_partnerRepository.findChildren(
					ChildType.CONTACT, applicationId)) {

			Contact contact = new Contact();

			contact.email = Entries.text(entry, "email");
			contact.fullName = Entries.text(entry, "fullName");
			contact.id = Entries.id(entry);
			contact.jobTitle = Entries.text(entry, "jobTitle");
			contact.phone = Entries.text(entry, "phone");
			contact.role = Entries.picklistKey(entry, "role");

			draft.contacts.add(contact);
		}

		for (ObjectNode entry :
				_partnerRepository.findChildren(ChildType.POS, applicationId)) {

			PosLocation posLocation = new PosLocation();

			String categories = Entries.text(entry, "categories");

			if (!_isBlank(categories)) {
				for (String category : categories.split(",")) {
					if (!category.trim().isEmpty()) {
						posLocation.categories.add(category.trim());
					}
				}
			}

			posLocation.city = Entries.text(entry, "city");
			posLocation.country = Entries.text(entry, "country");
			posLocation.id = Entries.id(entry);
			posLocation.name = Entries.text(entry, "name");
			posLocation.posType = Entries.picklistKey(entry, "posType");
			posLocation.postalCode = Entries.text(entry, "postalCode");
			posLocation.street = Entries.text(entry, "street");

			draft.posLocations.add(posLocation);
		}

		for (ObjectNode entry :
				_partnerRepository.findChildren(
					ChildType.DOCUMENT, applicationId)) {

			Document document = new Document();

			document.documentType = Entries.picklistKey(entry, "documentType");
			document.id = Entries.id(entry);
			document.note = Entries.text(entry, "note");

			JsonNode file = entry.get("file");

			if ((file != null) && file.isObject()) {
				document.fileName = Entries.text(file, "name");
			}

			if (document.fileName == null) {
				document.fileName = "Document " + document.id;
			}

			draft.documents.add(document);
		}

		return draft;
	}

	private ApiException _invalidLink() {
		return ApiException.notFound(INVALID_LINK_MESSAGE);
	}

	/**
	 * AC9: a VAT or registration number that matches an authorized partner
	 * means existing-company mode; a match on a terminated partner sets the
	 * watchlist flag.
	 */
	private CompanyMatch _lookupCompany(
		long applicationId, String vatId, String registrationNumber) {

		CompanyMatch companyMatch = new CompanyMatch();

		String normalizedVatId = _normalizeId(vatId);
		String normalizedRegistrationNumber = _normalizeId(registrationNumber);

		if ((normalizedVatId == null) &&
			(normalizedRegistrationNumber == null)) {

			return companyMatch;
		}

		for (ObjectNode other :
				_partnerRepository.findApplicationsByStatus(
					EnumSet.of(
						ApplicationStatus.AUTHORIZED,
						ApplicationStatus.TERMINATED))) {

			if (Entries.id(other) == applicationId) {
				continue;
			}

			boolean match =
				((normalizedVatId != null) &&
				 normalizedVatId.equals(
					 _normalizeId(Entries.text(other, "vatId")))) ||
				((normalizedRegistrationNumber != null) &&
				 normalizedRegistrationNumber.equals(
					 _normalizeId(Entries.text(other, "registrationNumber"))));

			if (!match) {
				continue;
			}

			if (_statusService.getStatus(other) ==
					ApplicationStatus.TERMINATED) {

				companyMatch.terminated = true;
			}
			else if ((companyMatch.existing == null) &&
					 (Entries.longValue(other, Fields.APPLICATION_ACCOUNT) !=
						 0)) {

				companyMatch.existing = other;
			}
		}

		return companyMatch;
	}

	private Mode _mode(
		ObjectNode invitation, ApplicationStatus applicationStatus) {

		String state = Entries.picklistKey(invitation, "state");

		if (InvitationService.STATE_ACTIVE.equals(state)) {
			Instant expiresAt = Entries.instant(invitation, "expiresAt");

			if ((expiresAt == null) ||
				!Instant.now(
					_clock
				).isBefore(
					expiresAt
				)) {

				return Mode.INVALID;
			}

			if ((applicationStatus == ApplicationStatus.INVITATION_SENT) ||
				EDITABLE.contains(applicationStatus)) {

				return Mode.EDIT;
			}

			if (READ_ONLY.contains(applicationStatus)) {
				return Mode.READ_ONLY;
			}

			return Mode.INVALID;
		}

		if (InvitationService.STATE_USED.equals(state) &&
			READ_ONLY.contains(applicationStatus)) {

			return Mode.READ_ONLY;
		}

		return Mode.INVALID;
	}

	private ObjectNode _ownChild(
		ChildType childType, Context context, long childId) {

		for (ObjectNode child :
				_partnerRepository.findChildren(
					childType, Entries.id(context.application))) {

			if (Entries.id(child) == childId) {
				return child;
			}
		}

		throw ApiException.notFound("Not found");
	}

	private Map<String, Object> _response(Context context) {
		Map<String, Object> result = new LinkedHashMap<>();

		result.put(
			"session",
			_registrationSessions.issue(
				Entries.id(context.invitation), Entries.id(context.application),
				Entries.text(context.invitation, "tokenHash")));
		result.put("readOnly", context.mode != Mode.EDIT);
		result.put(
			"status", _statusService.getStatus(context.application).key());
		result.put("draft", _draft(context.application));

		return result;
	}

	private void _sendSubmitted(ObjectNode invitation, ObjectNode application) {
		String companyName = Entries.text(application, "companyName");
		String partnerName =
			Entries.text(invitation, "firstName") + " " +
				Entries.text(invitation, "lastName");

		List<MailMessage> mailMessages = new ArrayList<>();

		mailMessages.add(
			MailMessage.of(
				"PM_SUBMITTED", Entries.text(invitation, "email")
			).value(
				"COMPANY_NAME", companyName
			).value(
				"MESSAGE",
				"Thank you. We will review your application and contact you " +
					"by email."
			).value(
				"PARTNER_NAME", partnerName
			));

		Set<Long> internalUserIds = new LinkedHashSet<>();

		long amUserId = Entries.longValue(
			application, Fields.APPLICATION_ASSIGNED_AM);

		if (amUserId == 0) {
			amUserId = Entries.longValue(
				invitation, Fields.INVITATION_INVITED_BY);
		}

		if (amUserId != 0) {
			internalUserIds.add(amUserId);
		}

		try {
			if ("legal".equals(
					Entries.picklistKey(application, "revisionRequestedBy"))) {

				for (UserInfo legal :
						_userDirectory.getUsersWithRole(RoleBootstrap.ROLE_LEGAL)) {

					internalUserIds.add(legal.getId());
				}
			}

			for (long userId : internalUserIds) {
				UserInfo userInfo = _userDirectory.getUser(userId);

				mailMessages.add(
					MailMessage.of(
						"PM_SUBMITTED", userInfo.getEmailAddress()
					).value(
						"COMPANY_NAME", companyName
					).value(
						"MESSAGE",
						"Review the application: " +
							_links.application(Entries.id(application))
					).value(
						"PARTNER_NAME", userInfo.getName()
					));
			}
		}
		catch (RuntimeException runtimeException) {
			_alertService.alert(
				"Submitted notification recipients unavailable",
				"Could not look up the reviewers of application " +
					Entries.id(application),
				runtimeException);
		}

		for (MailMessage mailMessage : mailMessages) {
			try {
				_mailer.send(mailMessage);
			}
			catch (RuntimeException runtimeException) {
				_alertService.alert(
					"Submitted email failed",
					"PM_SUBMITTED could not be sent for application " +
						Entries.id(application) + " to " + mailMessage.getTo(),
					runtimeException);
			}
		}
	}

	private void _syncContacts(long applicationId, List<Contact> contacts) {
		List<Map<String, Object>> rows = new ArrayList<>();

		for (Contact contact : contacts) {
			Map<String, Object> fields = new LinkedHashMap<>();

			fields.put("email", _trimToNull(contact.email));
			fields.put("fullName", _trimToNull(contact.fullName));
			fields.put("jobTitle", _trimToNull(contact.jobTitle));
			fields.put("phone", _trimToNull(contact.phone));
			fields.put("role", contact.role);

			rows.add(fields);
		}

		_syncChildren(
			ChildType.CONTACT, applicationId, rows,
			contacts.stream(
			).map(
				contact -> contact.id
			).collect(
				Collectors.toList()
			));
	}

	/**
	 * Makes the children match the draft: rows with a known id are updated,
	 * new rows created and rows the partner removed deleted. Ids that are not
	 * this application's children are treated as new rows.
	 */
	private void _syncChildren(
		ChildType childType, long applicationId,
		List<Map<String, Object>> rows, List<Long> ids) {

		Set<Long> existingIds = new HashSet<>();

		for (ObjectNode child :
				_partnerRepository.findChildren(childType, applicationId)) {

			existingIds.add(Entries.id(child));
		}

		Set<Long> keptIds = new HashSet<>();

		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> fields = rows.get(i);

			Long id = ids.get(i);

			if ((id != null) && existingIds.contains(id) && keptIds.add(id)) {
				_partnerRepository.patchChild(childType, id, fields);
			}
			else {
				fields.put(childType.applicationField(), applicationId);

				_partnerRepository.createChild(childType, fields);
			}
		}

		for (long existingId : existingIds) {
			if (!keptIds.contains(existingId)) {
				_partnerRepository.deleteChild(childType, existingId);
			}
		}
	}

	private void _syncPosLocations(
		long applicationId, List<PosLocation> posLocations) {

		List<Map<String, Object>> rows = new ArrayList<>();

		for (PosLocation posLocation : posLocations) {
			Map<String, Object> fields = new LinkedHashMap<>();

			fields.put(
				"categories",
				String.join(",", new LinkedHashSet<>(posLocation.categories)));
			fields.put("city", _trimToNull(posLocation.city));
			fields.put(
				"country",
				(posLocation.country == null) ? null :
					posLocation.country.trim(
					).toUpperCase(
						Locale.ROOT
					));
			fields.put("name", _trimToNull(posLocation.name));
			fields.put("posType", posLocation.posType);
			fields.put("postalCode", _trimToNull(posLocation.postalCode));
			fields.put("street", _trimToNull(posLocation.street));

			rows.add(fields);
		}

		_syncChildren(
			ChildType.POS, applicationId, rows,
			posLocations.stream(
			).map(
				posLocation -> posLocation.id
			).collect(
				Collectors.toList()
			));
	}

	private static final String[] _COMPANY_FIELDS = {
		"city", "companyName", "country", "employeeBand", "postalCode",
		"registrationNumber", "street", "vatId", "website"
	};

	private static final Pattern _COUNTRY_PATTERN = Pattern.compile(
		"^[A-Za-z]{2}$");

	private static final Pattern _EMAIL_PATTERN = Pattern.compile(
		"^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private static final Log _log = LogFactory.getLog(
		RegistrationService.class);

	private static final ObjectMapper _objectMapper = new ObjectMapper();

	private final AlertService _alertService;
	private final Map<String, String> _categories;
	private final Clock _clock;
	private final DocumentStore _documentStore;
	private final Links _links;
	private final Mailer _mailer;
	private final PartnerRepository _partnerRepository;
	private final RegistrationSessions _registrationSessions;
	private final ReviewRouter _reviewRouter;
	private final SettingsService _settingsService;
	private final StatusService _statusService;
	private final TokenService _tokenService;
	private final UserDirectory _userDirectory;

	private static class CompanyMatch {

		private ObjectNode existing;
		private boolean terminated;

	}

	private static class Context {

		private Context(
			ObjectNode invitation, ObjectNode application, Mode mode) {

			this.invitation = invitation;
			this.application = application;
			this.mode = mode;
		}

		private ObjectNode application;
		private ObjectNode invitation;
		private Mode mode;

	}

}
