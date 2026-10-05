package com.partnerportal.service.support;

import com.partnerportal.service.domain.DocumentStore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class FakeDocumentStore implements DocumentStore {

	@Override
	public void deleteTemporary(long documentId) {
		temporary.remove(documentId);
	}

	@Override
	public long uploadTemporary(
		String fileName, String contentType, byte[] bytes) {

		long id = _ids.incrementAndGet();

		temporary.put(id, fileName + " " + contentType);
		uploads++;

		return id;
	}

	/**
	 * Temporary uploads not yet deleted, as "fileName contentType".
	 */
	public final Map<Long, String> temporary = new LinkedHashMap<>();
	public int uploads;

	private final AtomicLong _ids = new AtomicLong(5000);

}
