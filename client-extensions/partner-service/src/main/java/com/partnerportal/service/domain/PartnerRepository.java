package com.partnerportal.service.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Data access for the partner objects. The Liferay implementation calls
 * {@code /o/c/...}; tests use an in-memory one. Entries are the JSON Liferay
 * returns (picklists as {@code {"key": ...}}).
 */
public interface PartnerRepository {

	long addStatusHistory(
		long applicationId, ApplicationStatus from, ApplicationStatus to,
		long actorUserId, String reason, Instant changedAt);

	ObjectNode createApplication(Map<String, Object> fields);

	ObjectNode createChild(ChildType childType, Map<String, Object> fields);

	ObjectNode createInvitation(Map<String, Object> fields);

	void deleteChild(ChildType childType, long childId);

	void deleteStatusHistory(long statusHistoryId);

	/**
	 * @return the application created for the invitation, or null
	 */
	ObjectNode findApplicationByInvitation(long invitationId);

	List<ObjectNode> findApplicationsByStatus(
		Collection<ApplicationStatus> statuses);

	List<ObjectNode> findChildren(ChildType childType, long applicationId);

	/**
	 * @return the invitation with this token hash, or null
	 */
	ObjectNode findInvitationByTokenHash(String tokenHash);

	List<ObjectNode> findInvitationsByEmailAndState(String email, String state);

	List<ObjectNode> findStatusHistory(long applicationId);

	ObjectNode getApplication(long applicationId);

	ObjectNode getInvitation(long invitationId);

	ObjectNode getSettings();

	List<ObjectNode> listApplications();

	/**
	 * @param invitedByUserId 0 for all invitations
	 */
	List<ObjectNode> listInvitations(long invitedByUserId);

	ObjectNode patchApplication(long applicationId, Map<String, Object> fields);

	ObjectNode patchChild(
		ChildType childType, long childId, Map<String, Object> fields);

	ObjectNode patchInvitation(long invitationId, Map<String, Object> fields);

}
