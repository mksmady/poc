package com.partnerportal.service.api;

import com.partnerportal.service.domain.RegistrationDraft;
import com.partnerportal.service.domain.RegistrationService;

import java.io.IOException;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * US-002 guest API for {@code partner-registration}. These are the only
 * routes without Liferay OAuth ({@code liferay.oauth.urls.excludes}). The
 * registration session travels in the request body, not in the
 * {@code Authorization} header, which the OAuth resource server would reject.
 * Every response carries a renewed {@code session}.
 */
@RequestMapping("/public/registration")
@RestController
public class RegistrationController {

	public RegistrationController(RegistrationService registrationService) {
		_registrationService = registrationService;
	}

	@PostMapping("/documents/delete")
	public Map<String, Object> deleteDocument(
		@RequestBody DocumentRequest documentRequest) {

		_registrationService.deleteDocument(
			documentRequest.session, documentRequest.documentId);

		return _registrationService.session(documentRequest.session);
	}

	@PostMapping("/session")
	public Map<String, Object> open(@RequestBody OpenRequest openRequest) {
		return _registrationService.open(openRequest.token);
	}

	@PutMapping("/draft")
	public Map<String, Object> saveDraft(
		@RequestBody DraftRequest draftRequest) {

		return _registrationService.saveDraft(
			draftRequest.session, draftRequest.draft);
	}

	@PostMapping("/state")
	public Map<String, Object> state(
		@RequestBody SessionRequest sessionRequest) {

		return _registrationService.session(sessionRequest.session);
	}

	@PostMapping("/submit")
	public Map<String, Object> submit(
		@RequestBody SessionRequest sessionRequest) {

		return _registrationService.submit(sessionRequest.session);
	}

	@PostMapping("/documents")
	public Map<String, Object> uploadDocument(
			@RequestParam String session, @RequestParam String documentType,
			@RequestParam(required = false) String note,
			@RequestParam MultipartFile file)
		throws IOException {

		return _registrationService.uploadDocument(
			session, documentType, note, file.getOriginalFilename(),
			file.getBytes());
	}

	public static class DocumentRequest {

		public long documentId;
		public String session;

	}

	public static class DraftRequest {

		public RegistrationDraft draft;
		public String session;

	}

	public static class OpenRequest {

		public String token;

	}

	public static class SessionRequest {

		public String session;

	}

	private final RegistrationService _registrationService;

}
