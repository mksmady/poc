package com.partnerportal.service.liferay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.partnerportal.service.domain.ApplicationStatus;
import com.partnerportal.service.domain.ChildType;
import com.partnerportal.service.domain.PartnerRepository;
import com.partnerportal.service.domain.PartnerSettings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.stereotype.Repository;

@Repository
public class LiferayPartnerRepository implements PartnerRepository {

	/**
	 * Quotes a value for an OData filter.
	 */
	public static String quote(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	public LiferayPartnerRepository(LiferayClient liferayClient) {
		_liferayClient = liferayClient;
	}

	@Override
	public long addStatusHistory(
		long applicationId, ApplicationStatus from, ApplicationStatus to,
		long actorUserId, String reason, Instant changedAt) {

		Map<String, Object> body = new LinkedHashMap<>();

		if (from != null) {
			body.put("fromStatus", from.key());
		}

		body.put("toStatus", to.key());
		body.put("reason", reason);
		body.put("changedAt", Entries.dateTime(changedAt));
		body.put(Fields.STATUS_HISTORY_APPLICATION, applicationId);

		if (actorUserId > 0) {
			body.put(Fields.STATUS_HISTORY_ACTOR, actorUserId);
		}

		return Entries.id(
			_liferayClient.post(ObjectPaths.STATUS_HISTORIES, body));
	}

	@Override
	public ObjectNode createApplication(Map<String, Object> fields) {
		return _object(_liferayClient.post(ObjectPaths.APPLICATIONS, fields));
	}

	@Override
	public ObjectNode createChild(
		ChildType childType, Map<String, Object> fields) {

		return _object(_liferayClient.post(childType.path(), fields));
	}

	@Override
	public ObjectNode createInvitation(Map<String, Object> fields) {
		return _object(_liferayClient.post(ObjectPaths.INVITATIONS, fields));
	}

	@Override
	public void deleteChild(ChildType childType, long childId) {
		_liferayClient.delete(childType.path() + "/" + childId);
	}

	@Override
	public void deleteStatusHistory(long statusHistoryId) {
		_liferayClient.delete(
			ObjectPaths.STATUS_HISTORIES + "/" + statusHistoryId);
	}

	@Override
	public ObjectNode findApplicationByInvitation(long invitationId) {
		List<ObjectNode> applications = _list(
			ObjectPaths.APPLICATIONS,
			Fields.APPLICATION_INVITATION + " eq " +
				quote(String.valueOf(invitationId)));

		if (applications.isEmpty()) {
			return null;
		}

		return applications.get(0);
	}

	@Override
	public List<ObjectNode> findApplicationsByStatus(
		Collection<ApplicationStatus> statuses) {

		StringJoiner stringJoiner = new StringJoiner(",", "(", ")");

		for (ApplicationStatus applicationStatus : statuses) {
			stringJoiner.add(quote(applicationStatus.key()));
		}

		return _list(
			ObjectPaths.APPLICATIONS, "applicationStatus in " + stringJoiner);
	}

	@Override
	public List<ObjectNode> findChildren(
		ChildType childType, long applicationId) {

		return _list(
			childType.path(),
			childType.applicationField() + " eq " +
				quote(String.valueOf(applicationId)));
	}

	@Override
	public ObjectNode findInvitationByTokenHash(String tokenHash) {
		List<ObjectNode> invitations = _list(
			ObjectPaths.INVITATIONS, "tokenHash eq " + quote(tokenHash));

		if (invitations.isEmpty()) {
			return null;
		}

		return invitations.get(0);
	}

	@Override
	public List<ObjectNode> findInvitationsByEmailAndState(
		String email, String state) {

		return _list(
			ObjectPaths.INVITATIONS,
			"email eq " + quote(email) + " and state eq " + quote(state));
	}

	@Override
	public List<ObjectNode> findStatusHistory(long applicationId) {
		return _list(
			ObjectPaths.STATUS_HISTORIES,
			Fields.STATUS_HISTORY_APPLICATION + " eq " +
				quote(String.valueOf(applicationId)));
	}

	@Override
	public ObjectNode getApplication(long applicationId) {
		return _object(
			_liferayClient.get(ObjectPaths.APPLICATIONS + "/" + applicationId));
	}

	@Override
	public ObjectNode getInvitation(long invitationId) {
		return _object(
			_liferayClient.get(ObjectPaths.INVITATIONS + "/" + invitationId));
	}

	@Override
	public ObjectNode getSettings() {
		return _object(
			_liferayClient.get(
				ObjectPaths.SETTINGS + "/by-external-reference-code/" +
					PartnerSettings.ERC));
	}

	@Override
	public List<ObjectNode> listApplications() {
		return _list(ObjectPaths.APPLICATIONS, null);
	}

	@Override
	public List<ObjectNode> listInvitations(long invitedByUserId) {
		if (invitedByUserId == 0) {
			return _list(ObjectPaths.INVITATIONS, null);
		}

		return _list(
			ObjectPaths.INVITATIONS,
			Fields.INVITATION_INVITED_BY + " eq " +
				quote(String.valueOf(invitedByUserId)));
	}

	@Override
	public ObjectNode patchApplication(
		long applicationId, Map<String, Object> fields) {

		return _object(
			_liferayClient.patch(
				ObjectPaths.APPLICATIONS + "/" + applicationId, fields));
	}

	@Override
	public ObjectNode patchChild(
		ChildType childType, long childId, Map<String, Object> fields) {

		return _object(
			_liferayClient.patch(childType.path() + "/" + childId, fields));
	}

	@Override
	public ObjectNode patchInvitation(
		long invitationId, Map<String, Object> fields) {

		return _object(
			_liferayClient.patch(
				ObjectPaths.INVITATIONS + "/" + invitationId, fields));
	}

	private List<ObjectNode> _list(String path, String filter) {
		List<ObjectNode> entries = new ArrayList<>();

		for (int page = 1;; page++) {
			StringBuilder sb = new StringBuilder(path);

			sb.append("?pageSize=");
			sb.append(_PAGE_SIZE);
			sb.append("&page=");
			sb.append(page);
			sb.append("&sort=id:asc");

			if (filter != null) {
				sb.append("&filter=");
				sb.append(Urls.param(filter));
			}

			JsonNode jsonNode = _liferayClient.get(sb.toString());

			for (JsonNode item : jsonNode.path("items")) {
				entries.add((ObjectNode)item);
			}

			if (page >= jsonNode.path(
					"lastPage"
				).asInt(
					1
				)) {

				return entries;
			}
		}
	}

	private ObjectNode _object(JsonNode jsonNode) {
		if (!(jsonNode instanceof ObjectNode)) {
			throw new IllegalStateException(
				"Expected a JSON object from Liferay but got " + jsonNode);
		}

		return (ObjectNode)jsonNode;
	}

	private static final int _PAGE_SIZE = 200;

	private final LiferayClient _liferayClient;

}
