package com.partnerportal.service.api;

import com.partnerportal.service.domain.IllegalTransitionException;
import com.partnerportal.service.liferay.LiferayException;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Maps domain errors to JSON {@code {status, title}} responses, the same shape
 * Liferay's headless APIs use.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	public static ResponseEntity<Map<String, Object>> problem(
		HttpStatus httpStatus, String title) {

		Map<String, Object> body = new LinkedHashMap<>();

		body.put("status", httpStatus.value());
		body.put("title", title);

		return ResponseEntity.status(
			httpStatus
		).body(
			body
		);
	}

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Map<String, Object>> handle(
		ApiException apiException) {

		return problem(apiException.getHttpStatus(), apiException.getMessage());
	}

	@ExceptionHandler(IllegalTransitionException.class)
	public ResponseEntity<Map<String, Object>> handle(
		IllegalTransitionException illegalTransitionException) {

		return problem(
			HttpStatus.CONFLICT, illegalTransitionException.getMessage());
	}

	@ExceptionHandler(LiferayException.class)
	public ResponseEntity<Map<String, Object>> handle(
		LiferayException liferayException) {

		_log.error("Liferay call failed", liferayException);

		if (liferayException.isNotFound()) {
			return problem(HttpStatus.NOT_FOUND, "Not found");
		}

		return problem(
			HttpStatus.BAD_GATEWAY, "The portal could not complete the request");
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> handle(
		MaxUploadSizeExceededException maxUploadSizeExceededException) {

		return problem(
			HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large to upload");
	}

	@ExceptionHandler(ValidationException.class)
	public ResponseEntity<Map<String, Object>> handle(
		ValidationException validationException) {

		ResponseEntity<Map<String, Object>> responseEntity = problem(
			HttpStatus.UNPROCESSABLE_ENTITY, validationException.getMessage());

		responseEntity.getBody(
		).put(
			"errors", validationException.getErrors()
		);

		return responseEntity;
	}

	private static final Log _log = LogFactory.getLog(
		ApiExceptionHandler.class);

}
