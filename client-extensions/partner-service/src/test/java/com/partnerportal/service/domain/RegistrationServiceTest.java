package com.partnerportal.service.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.partnerportal.service.api.ApiException;
import com.partnerportal.service.api.ValidationException;
import com.partnerportal.service.liferay.Entries;
import com.partnerportal.service.liferay.Fields;
import com.partnerportal.service.support.FakeUserDirectory;
import com.partnerportal.service.support.TestContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;

/**
 * US-002 registration wizard API on in-memory fakes.
 */
public class RegistrationServiceTest {

	public static final byte[] PDF = "%PDF-1.7 test".getBytes(
		StandardCharsets.US_ASCII);

	public static RegistrationDraft completeDraft() {
		RegistrationDraft draft = new RegistrationDraft();

		draft.questionnaire.put("businessType", "reseller");

		draft.company.city = "Berlin";
		draft.company.companyName = "Acme GmbH";
		draft.company.country = "de";
		draft.company.employeeBand = "10-49";
		draft.company.postalCode = "10115";
		draft.company.registrationNumber = "HRB 12345";
		draft.company.street = "Main 1";
		draft.company.vatId = "DE123456789";
		draft.company.website = "https://acme.example";

		RegistrationDraft.Contact contact = new RegistrationDraft.Contact();

		contact.email = "ana@acme.example";
		contact.fullName = "Ana Lopez";
		contact.role = "primary";

		draft.contacts.add(contact);

		draft.compliance.gdprConsent = true;
		draft.compliance.sanctionsDeclaration = true;

		RegistrationDraft.PosLocation posLocation =
			new RegistrationDraft.PosLocation();

		posLocation.categories.add("networking");
		posLocation.city = "Berlin";
		posLocation.country = "DE";
		posLocation.name = "Berlin store";
		posLocation.posType = "store";
		posLocation.street = "Main 1";

		draft.posLocations.add(posLocation);

		return draft;
	}

	@Test
	public void testBlankRowsAreNotStored() {
		String session = _session(_service.open(_invite()));

		// What the wizard sends from the first step: its starting empty
		// contact (role preselected) and empty location

		RegistrationDraft draft = new RegistrationDraft();

		draft.questionnaire.put("businessType", "reseller");

		RegistrationDraft.Contact contact = new RegistrationDraft.Contact();

		contact.role = "primary";

		draft.contacts.add(contact);
		draft.posLocations.add(new RegistrationDraft.PosLocation());

		RegistrationDraft saved = _draft(_service.saveDraft(session, draft));

		Assertions.assertTrue(saved.contacts.isEmpty());
		Assertions.assertTrue(saved.posLocations.isEmpty());
		Assertions.assertEquals("reseller", saved.questionnaire.get("businessType"));
		Assertions.assertTrue(
			_ctx.repository.children.get(ChildType.CONTACT).isEmpty());
	}

	@Test
	public void testConcurrentOpensRecordOneChange() throws Exception {
		String token = _invite();

		int threads = 4;

		CountDownLatch start = new CountDownLatch(1);

		ExecutorService executorService = Executors.newFixedThreadPool(
			threads);

		try {
			List<Future<Map<String, Object>>> futures = new ArrayList<>();

			for (int i = 0; i < threads; i++) {
				futures.add(
					executorService.submit(
						() -> {
							start.await();

							return _service.open(token);
						}));
			}

			start.countDown();

			for (Future<Map<String, Object>> future : futures) {
				Assertions.assertEquals(
					"registrationInProgress",
					future.get(
						10, TimeUnit.SECONDS
					).get(
						"status"
					));
			}
		}
		finally {
			executorService.shutdownNow();
		}

		Assertions.assertEquals(
			Arrays.asList("invitationSent", "registrationInProgress"),
			_ctx.repository.historyOf(
				Entries.id(_application())
			).stream(
			).map(
				row -> row.path(
					"toStatus"
				).asText()
			).collect(
				Collectors.toList()
			));
	}

	@Test
	public void testStartedRowNeedsRequiredFieldsAndNothingIsSaved() {
		String session = _session(_service.open(_invite()));

		RegistrationDraft draft = completeDraft();

		draft.contacts.add(0, new RegistrationDraft.Contact());

		RegistrationDraft.Contact started = new RegistrationDraft.Contact();

		started.phone = "+49 30 1234";
		started.role = "finance";

		draft.contacts.add(started);

		RegistrationDraft.PosLocation pos = new RegistrationDraft.PosLocation();

		pos.categories.add("security");

		draft.posLocations.add(pos);

		ValidationException validationException = Assertions.assertThrows(
			ValidationException.class,
			() -> _service.saveDraft(session, draft));

		// Indexes are the rows the partner sees, empty row 0 included

		Assertions.assertEquals(
			Arrays.asList(
				"contacts.contacts[2].fullName", "contacts.contacts[2].email",
				"pos.posLocations[1].name"),
			_fields(validationException.getErrors()));

		Assertions.assertNull(
			Entries.text(_application(), "companyName"),
			"a refused save must not write the application");
		Assertions.assertTrue(
			_ctx.repository.children.get(ChildType.CONTACT).isEmpty());
	}

	@Test
	public void testTransitionIfCurrentSkipsWhenStatusMoved() {
		_invite();

		long applicationId = Entries.id(_application());

		Assertions.assertTrue(
			_ctx.statusService.transitionIfCurrent(
				applicationId, ApplicationStatus.INVITATION_SENT,
				ApplicationStatus.REGISTRATION_IN_PROGRESS, 0, "first"));
		Assertions.assertFalse(
			_ctx.statusService.transitionIfCurrent(
				applicationId, ApplicationStatus.INVITATION_SENT,
				ApplicationStatus.REGISTRATION_IN_PROGRESS, 0, "second"));
		Assertions.assertEquals(
			2, _ctx.repository.historyOf(applicationId).size());
	}

	@Test
	public void testDraftIsRestoredOnReturn() {
		String token = _invite();

		String session = _session(_service.open(token));

		RegistrationDraft draft = completeDraft();

		RegistrationDraft.Contact finance = new RegistrationDraft.Contact();

		finance.email = "fin@acme.example";
		finance.fullName = "Fin Ance";
		finance.role = "finance";

		draft.contacts.add(finance);

		_service.saveDraft(session, draft);

		Map<String, Object> reopened = _service.open(token);

		RegistrationDraft restored = _draft(reopened);

		Assertions.assertEquals("Acme GmbH", restored.company.companyName);
		Assertions.assertEquals("DE", restored.company.country);
		Assertions.assertEquals("reseller", restored.questionnaire.get("businessType"));
		Assertions.assertEquals(2, restored.contacts.size());
		Assertions.assertEquals(
			Arrays.asList("networking"),
			restored.posLocations.get(0).categories);
		Assertions.assertTrue(restored.compliance.gdprConsent);

		// Removing a contact keeps the other one's id

		Long primaryId = restored.contacts.get(0).id;

		restored.contacts.remove(1);

		RegistrationDraft saved = _draft(
			_service.saveDraft(_session(reopened), restored));

		Assertions.assertEquals(1, saved.contacts.size());
		Assertions.assertEquals(primaryId, saved.contacts.get(0).id);
		Assertions.assertEquals(
			1, _ctx.repository.children.get(ChildType.CONTACT).size());
	}

	@Test
	public void testExistingCompanyByVatId() {
		Map<String, Object> fields = new LinkedHashMap<>();

		fields.put("applicationStatus", "authorized");
		fields.put("city", "Munich");
		fields.put("companyName", "Acme Holding GmbH");
		fields.put("country", "DE");
		fields.put("vatId", "DE 123.456.789");
		fields.put(Fields.APPLICATION_ACCOUNT, 777L);

		_ctx.repository.createApplication(fields);

		String session = _session(_service.open(_invite()));

		RegistrationDraft draft = completeDraft();

		draft.company.companyName = "Typed by partner";

		RegistrationDraft saved = _draft(_service.saveDraft(session, draft));

		Assertions.assertTrue(saved.existingCompany);
		Assertions.assertEquals("Acme Holding GmbH", saved.company.companyName);
		Assertions.assertEquals("Munich", saved.company.city);

		ObjectNode application = _application();

		Assertions.assertEquals(
			777L, Entries.longValue(application, Fields.APPLICATION_ACCOUNT));
		Assertions.assertFalse(Entries.bool(application, "watchlistMatch"));
	}

	@Test
	public void testExpiredLinkIsRefused() {
		String token = _invite();

		_ctx.advance(Duration.ofDays(31));

		_assertInvalidLink(() -> _service.open(token));
	}

	@Test
	public void testFirstOpenMovesToRegistrationInProgress() {
		String token = _invite();

		Map<String, Object> result = _service.open(token);

		Assertions.assertEquals("registrationInProgress", result.get("status"));
		Assertions.assertEquals(false, result.get("readOnly"));
		Assertions.assertEquals(10, result.get("maxUploadMb"));
		Assertions.assertNotNull(result.get("session"));

		_service.open(token);

		Assertions.assertEquals(
			2, _ctx.repository.historyOf(Entries.id(_application())).size());
	}

	@Test
	public void testForgedSessionIsUnauthorized() {
		ApiException apiException = Assertions.assertThrows(
			ApiException.class, () -> _service.session("not-a-session"));

		Assertions.assertEquals(
			HttpStatus.UNAUTHORIZED, apiException.getHttpStatus());
	}

	@Test
	public void testInvalidTokenIsRefused() {
		_invite();

		_assertInvalidLink(() -> _service.open("wrong-token"));
		_assertInvalidLink(() -> _service.open(""));
	}

	@Test
	public void testResendRevokesTheOldLinkAndItsSessions() {
		String token = _invite();

		String session = _session(_service.open(token));

		_ctx.invitationService.resend(
			Entries.id(_invitation()),
			_ctx.userDirectory.getUser(FakeUserDirectory.AM));

		_assertInvalidLink(() -> _service.open(token));
		_assertInvalidLink(() -> _service.session(session));

		String newToken = _lastToken();

		Assertions.assertEquals(
			"registrationInProgress", _service.open(newToken).get("status"));
	}

	@Test
	public void testSaveDraftRejectsValuesOutsideTheLists() {
		String session = _session(_service.open(_invite()));

		RegistrationDraft draft = completeDraft();

		draft.contacts.get(0).role = "boss";
		draft.posLocations.get(0).categories.add("weapons");
		draft.company.street = new String(new char[300]).replace('\0', 'x');

		ValidationException validationException = Assertions.assertThrows(
			ValidationException.class,
			() -> _service.saveDraft(session, draft));

		Assertions.assertEquals(
			Arrays.asList(
				"company.street", "contacts.contacts[0].role",
				"pos.posLocations[0].categories"),
			_fields(validationException.getErrors()));
	}

	@Test
	public void testSubmitMovesToApprovalPendingAndLocksTheWizard() {
		String token = _invite();

		String session = _session(_service.open(token));

		_service.saveDraft(session, completeDraft());
		_service.uploadDocument(session, "tradeRegister", null, "tr.pdf", PDF);
		_service.uploadDocument(
			session, "vatCertificate", "scan", "vat.pdf", PDF);

		Map<String, Object> result = _service.submit(session);

		Assertions.assertEquals("approvalPending", result.get("status"));
		Assertions.assertEquals(true, result.get("readOnly"));

		ObjectNode application = _application();

		Assertions.assertNotNull(Entries.text(application, "submittedAt"));
		Assertions.assertEquals(
			Arrays.asList(
				"invitationSent", "registrationInProgress", "submitted",
				"approvalPending"),
			_ctx.repository.historyOf(
				Entries.id(application)
			).stream(
			).map(
				row -> row.path(
					"toStatus"
				).asText()
			).collect(
				Collectors.toList()
			));
		Assertions.assertEquals(
			"used", Entries.picklistKey(_invitation(), "state"));

		List<String> recipients = _ctx.mailer.byTemplate(
			"PM_SUBMITTED"
		).stream(
		).map(
			MailMessage::getTo
		).collect(
			Collectors.toList()
		);

		Assertions.assertEquals(
			Arrays.asList("ana@acme.example", "am@example.com"), recipients);

		// AC11: read-only afterwards, also through the link

		ApiException apiException = Assertions.assertThrows(
			ApiException.class,
			() -> _service.saveDraft(session, completeDraft()));

		Assertions.assertEquals(HttpStatus.CONFLICT, apiException.getHttpStatus());
		Assertions.assertEquals(true, _service.open(token).get("readOnly"));
	}

	@Test
	public void testSubmitReportsEveryMissingItem() {
		String session = _session(_service.open(_invite()));

		RegistrationDraft draft = completeDraft();

		draft.contacts.get(0).role = "technical";
		draft.compliance.sanctionsDeclaration = false;
		draft.posLocations.clear();

		_service.saveDraft(session, draft);
		_service.uploadDocument(session, "tradeRegister", null, "tr.pdf", PDF);

		ValidationException validationException = Assertions.assertThrows(
			ValidationException.class, () -> _service.submit(session));

		Assertions.assertEquals(
			Arrays.asList(
				"contacts.contacts", "compliance.sanctionsDeclaration",
				"pos.posLocations", "documents.vatCertificate"),
			_fields(validationException.getErrors()));
		Assertions.assertEquals(
			"registrationInProgress",
			_ctx.repository.statusOf(Entries.id(_application())));
	}

	@Test
	public void testTerminatedMatchSetsWatchlistSilently() {
		Map<String, Object> fields = new LinkedHashMap<>();

		fields.put("applicationStatus", "terminated");
		fields.put("registrationNumber", "hrb-12345");

		_ctx.repository.createApplication(fields);

		String session = _session(_service.open(_invite()));

		Map<String, Object> result = _service.saveDraft(
			session, completeDraft());

		Assertions.assertFalse(_draft(result).existingCompany);
		Assertions.assertFalse(result.toString().contains("watchlist"));
		Assertions.assertTrue(
			Entries.bool(_application(), "watchlistMatch"));
	}

	@Test
	public void testUploadChecksTypeAndSize() {
		String session = _session(_service.open(_invite()));

		ApiException apiException = Assertions.assertThrows(
			ApiException.class,
			() -> _service.uploadDocument(
				session, "tradeRegister", null, "fake.pdf",
				"MZ executable".getBytes(StandardCharsets.US_ASCII)));

		Assertions.assertEquals(
			HttpStatus.UNSUPPORTED_MEDIA_TYPE, apiException.getHttpStatus());

		apiException = Assertions.assertThrows(
			ApiException.class,
			() -> _service.uploadDocument(
				session, "signedContract", null, "c.pdf", PDF));

		Assertions.assertEquals(
			HttpStatus.BAD_REQUEST, apiException.getHttpStatus());

		_ctx.repository.settings.put("maxUploadMb", 1);

		byte[] large = new byte[1024 * 1024 + 1];

		System.arraycopy(PDF, 0, large, 0, PDF.length);

		apiException = Assertions.assertThrows(
			ApiException.class,
			() -> _service.uploadDocument(
				session, "tradeRegister", null, "big.pdf", large));

		Assertions.assertEquals(
			HttpStatus.PAYLOAD_TOO_LARGE, apiException.getHttpStatus());
		Assertions.assertEquals(0, _ctx.documentStore.uploads);

		RegistrationDraft draft = _draft(
			_service.uploadDocument(
				session, "tradeRegister", "page 1", "../Extract.PDF", PDF));

		Assertions.assertEquals(1, draft.documents.size());
		Assertions.assertEquals(
			"tradeRegister", draft.documents.get(0).documentType);
		Assertions.assertTrue(_ctx.documentStore.temporary.isEmpty());

		_service.deleteDocument(
			_session(_service.session(session)), draft.documents.get(0).id);

		Assertions.assertTrue(
			_ctx.repository.children.get(ChildType.DOCUMENT).isEmpty());
	}

	private void _assertInvalidLink(
		org.junit.jupiter.api.function.Executable executable) {

		ApiException apiException = Assertions.assertThrows(
			ApiException.class, executable);

		Assertions.assertEquals(HttpStatus.NOT_FOUND, apiException.getHttpStatus());
		Assertions.assertEquals(
			RegistrationService.INVALID_LINK_MESSAGE, apiException.getMessage());
	}

	private ObjectNode _application() {
		return _ctx.repository.findApplicationByInvitation(
			Entries.id(_invitation()));
	}

	private RegistrationDraft _draft(Map<String, Object> result) {
		return (RegistrationDraft)result.get("draft");
	}

	private List<String> _fields(List<Map<String, String>> errors) {
		return errors.stream(
		).map(
			error -> error.get("step") + "." + error.get("field")
		).collect(
			Collectors.toList()
		);
	}

	private ObjectNode _invitation() {
		return _ctx.repository.invitations.values(
		).iterator(
		).next();
	}

	private String _invite() {
		_ctx.invitationService.create(
			"ana@acme.example", "Ana", "Lopez",
			_ctx.userDirectory.getUser(FakeUserDirectory.AM));

		return _lastToken();
	}

	private String _lastToken() {
		String link = _ctx.mailer.byTemplate(
			"PM_INVITE"
		).get(
			_ctx.mailer.byTemplate(
				"PM_INVITE"
			).size() - 1
		).getValues(
		).get(
			"LINK"
		);

		return link.substring(link.indexOf("?t=") + 3);
	}

	private String _session(Map<String, Object> result) {
		return (String)result.get("session");
	}

	private final TestContext _ctx = new TestContext();
	private final RegistrationService _service = _ctx.registrationService;

}
