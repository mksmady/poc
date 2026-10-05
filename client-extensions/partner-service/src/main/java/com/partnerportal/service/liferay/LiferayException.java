package com.partnerportal.service.liferay;

public class LiferayException extends RuntimeException {

	public LiferayException(String method, String path, int status, String body) {
		super(method + " " + path + " returned " + status + ": " + _truncate(body));

		_status = status;
		_body = body;
	}

	public String getBody() {
		return _body;
	}

	public int getStatus() {
		return _status;
	}

	public boolean isNotFound() {
		return _status == 404;
	}

	private static String _truncate(String body) {
		if ((body == null) || (body.length() <= 500)) {
			return body;
		}

		return body.substring(0, 500) + "...";
	}

	private final String _body;
	private final int _status;

}
