package com.partnerportal.service.liferay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import com.liferay.client.extension.util.spring.boot.LiferayOAuth2AccessTokenManager;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WebClientLiferayClient implements LiferayClient {

	public WebClientLiferayClient(
		LiferayOAuth2AccessTokenManager liferayOAuth2AccessTokenManager,
		ObjectMapper objectMapper,
		@Value("${partner.headless.server.oauth.erc}") String oAuthERC,
		@Value("${com.liferay.lxc.dxp.server.protocol}") String protocol,
		@Value("${com.liferay.lxc.dxp.mainDomain}") String mainDomain) {

		_liferayOAuth2AccessTokenManager = liferayOAuth2AccessTokenManager;
		_objectMapper = objectMapper;
		_oAuthERC = oAuthERC;
		_baseURL = protocol + "://" + mainDomain;

		_webClient = WebClient.builder(
		).codecs(
			configurer -> configurer.defaultCodecs(
			).maxInMemorySize(
				32 * 1024 * 1024
			)
		).build();
	}

	@Override
	public void delete(String path) {
		_exchange(HttpMethod.DELETE, path, null);
	}

	@Override
	public JsonNode get(String path) {
		return _exchange(HttpMethod.GET, path, null);
	}

	@Override
	public JsonNode patch(String path, Object body) {
		return _exchange(HttpMethod.PATCH, path, body);
	}

	@Override
	public JsonNode post(String path, Object body) {
		return _exchange(HttpMethod.POST, path, body);
	}

	@Override
	public JsonNode postMultipart(
		String path, String fileName, String contentType, byte[] bytes,
		String jsonPartName, String json) {

		MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();

		multipartBodyBuilder.part(
			"file",
			new ByteArrayResource(bytes) {

				@Override
				public String getFilename() {
					return fileName;
				}

			}
		).contentType(
			MediaType.parseMediaType(contentType)
		);
		multipartBodyBuilder.part(
			jsonPartName, json
		).contentType(
			MediaType.APPLICATION_JSON
		);

		return _exchange(
			HttpMethod.POST, path,
			BodyInserters.fromMultipartData(multipartBodyBuilder.build()));
	}

	@Override
	public JsonNode put(String path, Object body) {
		return _exchange(HttpMethod.PUT, path, body);
	}

	private JsonNode _exchange(HttpMethod method, String path, Object body) {
		URI uri = URI.create(_baseURL + path);

		WebClient.RequestBodySpec requestBodySpec = _webClient.method(
			method
		).uri(
			uri
		).header(
			HttpHeaders.AUTHORIZATION,
			_liferayOAuth2AccessTokenManager.getAuthorization(_oAuthERC)
		).accept(
			MediaType.APPLICATION_JSON
		);

		if (body instanceof BodyInserter) {
			requestBodySpec.body((BodyInserter<?, ? super ClientHttpRequest>)body);
		}
		else if (body != null) {
			requestBodySpec.contentType(MediaType.APPLICATION_JSON);
			requestBodySpec.bodyValue(body);
		}

		Response response = requestBodySpec.exchangeToMono(
			clientResponse -> clientResponse.bodyToMono(
				String.class
			).defaultIfEmpty(
				""
			).map(
				responseBody -> new Response(
					clientResponse.rawStatusCode(), responseBody)
			)
		).block(
			_TIMEOUT
		);

		if ((response == null) || (response.status < 200) ||
			(response.status >= 300)) {

			throw new LiferayException(
				method.name(), path,
				(response == null) ? 0 : response.status,
				(response == null) ? "" : response.body);
		}

		if (response.body.isEmpty()) {
			return NullNode.getInstance();
		}

		try {
			return _objectMapper.readTree(response.body);
		}
		catch (Exception exception) {
			throw new LiferayException(
				method.name(), path, response.status,
				"Unparseable response: " + response.body);
		}
	}

	private static final Duration _TIMEOUT = Duration.ofSeconds(30);

	private final String _baseURL;
	private final LiferayOAuth2AccessTokenManager
		_liferayOAuth2AccessTokenManager;
	private final String _oAuthERC;
	private final ObjectMapper _objectMapper;
	private final WebClient _webClient;

	private static class Response {

		Response(int status, String body) {
			this.status = status;
			this.body = body;
		}

		final String body;
		final int status;

	}

}
