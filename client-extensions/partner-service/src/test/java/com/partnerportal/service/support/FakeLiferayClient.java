package com.partnerportal.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import com.partnerportal.service.liferay.LiferayClient;
import com.partnerportal.service.liferay.LiferayException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records calls and answers GETs from canned responses keyed by path.
 */
public class FakeLiferayClient implements LiferayClient {

	@Override
	public void delete(String path) {
		calls.add(new Call("DELETE", path, null));
	}

	@Override
	public JsonNode get(String path) {
		calls.add(new Call("GET", path, null));

		JsonNode response = responses.get(path);

		if (response == null) {
			throw new LiferayException("GET", path, 404, "not found");
		}

		return response;
	}

	@Override
	public JsonNode patch(String path, Object body) {
		return _write("PATCH", path, body);
	}

	@Override
	public JsonNode post(String path, Object body) {
		return _write("POST", path, body);
	}

	@Override
	public JsonNode postMultipart(
		String path, String fileName, String contentType, byte[] bytes,
		String jsonPartName, String json) {

		return _write("POST", path, Map.of("fileName", fileName, jsonPartName, json));
	}

	@Override
	public JsonNode put(String path, Object body) {
		return _write("PUT", path, body);
	}

	public void respond(String path, String json) {
		try {
			responses.put(
				path, InMemoryPartnerRepository.OBJECT_MAPPER.readTree(json));
		}
		catch (Exception exception) {
			throw new IllegalArgumentException(exception);
		}
	}

	public final List<Call> calls = new ArrayList<>();
	public final Map<String, JsonNode> responses = new LinkedHashMap<>();

	public static class Call {

		public Call(String method, String path, Object body) {
			this.method = method;
			this.path = path;
			this.body = InMemoryPartnerRepository.OBJECT_MAPPER.valueToTree(
				body);
		}

		public final JsonNode body;
		public final String method;
		public final String path;

	}

	private JsonNode _write(String method, String path, Object body) {
		calls.add(new Call(method, path, body));

		JsonNode response = responses.get(method + " " + path);

		if (response == null) {
			return NullNode.getInstance();
		}

		return response;
	}

}
