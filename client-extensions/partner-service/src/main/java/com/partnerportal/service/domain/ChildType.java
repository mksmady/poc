package com.partnerportal.service.domain;

import com.partnerportal.service.liferay.Fields;
import com.partnerportal.service.liferay.ObjectPaths;

/**
 * The child objects of PartnerApplication and their relationship fields.
 */
public enum ChildType {

	COMMENT(ObjectPaths.COMMENTS, Fields.COMMENT_APPLICATION),
	CONTACT(ObjectPaths.CONTACTS, Fields.CONTACT_APPLICATION),
	DOCUMENT(ObjectPaths.DOCUMENTS, Fields.DOCUMENT_APPLICATION),
	POS(ObjectPaths.POS_LOCATIONS, Fields.POS_APPLICATION);

	public String applicationField() {
		return _applicationField;
	}

	public String path() {
		return _path;
	}

	ChildType(String path, String applicationField) {
		_path = path;
		_applicationField = applicationField;
	}

	private final String _applicationField;
	private final String _path;

}
