package com.partnerportal.service.api;

import org.springframework.http.HttpStatus;

/**
 * An error whose message is safe to show to the caller.
 */
public class ApiException extends RuntimeException {

	public static ApiException badRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, message);
	}

	public static ApiException conflict(String message) {
		return new ApiException(HttpStatus.CONFLICT, message);
	}

	public static ApiException forbidden(String message) {
		return new ApiException(HttpStatus.FORBIDDEN, message);
	}

	public static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, message);
	}

	public ApiException(HttpStatus httpStatus, String message) {
		super(message);

		_httpStatus = httpStatus;
	}

	public HttpStatus getHttpStatus() {
		return _httpStatus;
	}

	private final HttpStatus _httpStatus;

}
