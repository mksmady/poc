package com.partnerportal.service.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Field errors returned as 422 {@code {status, title, errors: [{step, field,
 * message}]}} so the wizard can show each one next to its field.
 */
public class ValidationException extends RuntimeException {

	public ValidationException(List<Map<String, String>> errors) {
		super("Some information is missing or not valid");

		_errors = Collections.unmodifiableList(new ArrayList<>(errors));
	}

	public static Map<String, String> error(
		String step, String field, String message) {

		Map<String, String> error = new LinkedHashMap<>();

		error.put("step", step);
		error.put("field", field);
		error.put("message", message);

		return error;
	}

	public List<Map<String, String>> getErrors() {
		return _errors;
	}

	private final List<Map<String, String>> _errors;

}
