package com.partnerportal.service.domain;

/**
 * Temporary file storage for attachment uploads. DXP 2024.Q1 attachment
 * fields take the id of an existing document and copy it into the object's
 * private repository, so a file is uploaded (viewable by owner only), attached,
 * then the temporary copy is deleted.
 */
public interface DocumentStore {

	void deleteTemporary(long documentId);

	long uploadTemporary(String fileName, String contentType, byte[] bytes);

}
