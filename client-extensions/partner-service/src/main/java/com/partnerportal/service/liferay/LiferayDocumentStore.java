package com.partnerportal.service.liferay;

import com.fasterxml.jackson.databind.JsonNode;

import com.partnerportal.service.domain.DocumentStore;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Uploads to the Documents and Media root of the upload site, viewable by
 * the owner (the headless-server service account) only.
 */
@Component
public class LiferayDocumentStore implements DocumentStore {

	public LiferayDocumentStore(
		LiferayClient liferayClient,
		@Value("${partner.upload.site:guest}") String siteFriendlyURLPath) {

		_liferayClient = liferayClient;
		_siteFriendlyURLPath = siteFriendlyURLPath;
	}

	@Override
	public void deleteTemporary(long documentId) {
		_liferayClient.delete(
			"/o/headless-delivery/v1.0/documents/" + documentId);
	}

	@Override
	public long uploadTemporary(
		String fileName, String contentType, byte[] bytes) {

		String title = "pm-upload-" + UUID.randomUUID() + "-" + fileName;

		JsonNode document = _liferayClient.postMultipart(
			"/o/headless-delivery/v1.0/sites/" + _getSiteId() + "/documents",
			fileName, contentType, bytes, "document",
			"{\"title\": \"" + title.replace("\"", "") +
				"\", \"viewableBy\": \"Owner\"}");

		return document.path(
			"id"
		).asLong();
	}

	private synchronized long _getSiteId() {
		if (_siteId == 0) {
			_siteId = _liferayClient.get(
				"/o/headless-admin-user/v1.0/sites/by-friendly-url-path/" +
					Urls.segment(_siteFriendlyURLPath)
			).path(
				"id"
			).asLong();
		}

		return _siteId;
	}

	private final LiferayClient _liferayClient;
	private long _siteId;
	private final String _siteFriendlyURLPath;

}
