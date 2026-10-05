package com.partnerportal.service.api;

import com.partnerportal.service.domain.RegistrationDraft;
import com.partnerportal.service.domain.RegistrationService;
import com.partnerportal.service.domain.RegistrationServiceTest;
import com.partnerportal.service.support.FakeUserDirectory;
import com.partnerportal.service.support.TestContext;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

/**
 * US-002 public endpoints, called directly as the wizard calls them.
 */
public class RegistrationControllerTest {

	@Test
	public void testFullWizardThroughTheEndpoints() throws Exception {
		RegistrationController.OpenRequest openRequest =
			new RegistrationController.OpenRequest();

		openRequest.token = _invite();

		Map<String, Object> result = _controller.open(openRequest);

		RegistrationController.DraftRequest draftRequest =
			new RegistrationController.DraftRequest();

		draftRequest.draft = RegistrationServiceTest.completeDraft();
		draftRequest.session = (String)result.get("session");

		result = _controller.saveDraft(draftRequest);

		for (String documentType : new String[] {"tradeRegister", "vatCertificate"}) {
			result = _controller.uploadDocument(
				(String)result.get("session"), documentType, null,
				new MockMultipartFile(
					"file", documentType + ".pdf", "application/octet-stream",
					RegistrationServiceTest.PDF));
		}

		RegistrationController.SessionRequest sessionRequest =
			new RegistrationController.SessionRequest();

		sessionRequest.session = (String)result.get("session");

		result = _controller.submit(sessionRequest);

		Assertions.assertEquals("approvalPending", result.get("status"));
		Assertions.assertEquals(true, result.get("readOnly"));
	}

	@Test
	public void testInvalidLinkIsNotFoundWithTheStoryMessage() {
		RegistrationController.OpenRequest openRequest =
			new RegistrationController.OpenRequest();

		openRequest.token = "nope";

		ApiException apiException = Assertions.assertThrows(
			ApiException.class, () -> _controller.open(openRequest));

		ResponseEntity<Map<String, Object>> response =
			new ApiExceptionHandler().handle(apiException);

		Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		Assertions.assertEquals(
			RegistrationService.INVALID_LINK_MESSAGE,
			response.getBody().get("title"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSubmitValidationIsUnprocessableWithFieldErrors() {
		RegistrationController.OpenRequest openRequest =
			new RegistrationController.OpenRequest();

		openRequest.token = _invite();

		RegistrationController.SessionRequest sessionRequest =
			new RegistrationController.SessionRequest();

		sessionRequest.session = (String)_controller.open(
			openRequest
		).get(
			"session"
		);

		ValidationException validationException = Assertions.assertThrows(
			ValidationException.class, () -> _controller.submit(sessionRequest));

		ResponseEntity<Map<String, Object>> response =
			new ApiExceptionHandler().handle(validationException);

		Assertions.assertEquals(
			HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());

		List<Map<String, String>> errors =
			(List<Map<String, String>>)response.getBody().get("errors");

		Assertions.assertTrue(
			errors.stream(
			).anyMatch(
				error -> "company".equals(error.get("step")) &&
					"companyName".equals(error.get("field"))
			));
	}

	@Test
	public void testUploadOfAnotherTypeIsRefused() {
		RegistrationController.OpenRequest openRequest =
			new RegistrationController.OpenRequest();

		openRequest.token = _invite();

		String session = (String)_controller.open(
			openRequest
		).get(
			"session"
		);

		ApiException apiException = Assertions.assertThrows(
			ApiException.class,
			() -> _controller.uploadDocument(
				session, "tradeRegister", null,
				new MockMultipartFile(
					"file", "x.pdf", "application/pdf",
					"<html>".getBytes())));

		Assertions.assertEquals(
			HttpStatus.UNSUPPORTED_MEDIA_TYPE, apiException.getHttpStatus());
	}

	private String _invite() {
		_ctx.invitationService.create(
			"ana@acme.example", "Ana", "Lopez",
			_ctx.userDirectory.getUser(FakeUserDirectory.AM));

		String link = _ctx.mailer.last(
		).getValues(
		).get(
			"LINK"
		);

		return link.substring(link.indexOf("?t=") + 3);
	}

	private final TestContext _ctx = new TestContext();
	private final RegistrationController _controller =
		new RegistrationController(_ctx.registrationService);

}
