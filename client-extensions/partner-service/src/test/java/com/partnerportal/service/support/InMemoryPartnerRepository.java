package com.partnerportal.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.partnerportal.service.domain.ApplicationStatus;
import com.partnerportal.service.domain.ChildType;
import com.partnerportal.service.domain.PartnerRepository;
import com.partnerportal.service.liferay.Entries;
import com.partnerportal.service.liferay.Fields;
import com.partnerportal.service.liferay.LiferayException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * In-memory stand-in for the Liferay objects. Entries are stored as the JSON
 * Liferay would return, with picklists as {@code {"key": ...}}.
 */
public class InMemoryPartnerRepository implements PartnerRepository {

	public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public InMemoryPartnerRepository() {
		for (ChildType childType : ChildType.values()) {
			children.put(childType, new LinkedHashMap<>());
		}
	}

	@Override
	public synchronized long addStatusHistory(
		long applicationId, ApplicationStatus from, ApplicationStatus to,
		long actorUserId, String reason, Instant changedAt) {

		if (failNextHistoryWrite) {
			failNextHistoryWrite = false;

			throw new LiferayException(
				"POST", "/o/c/statushistories", 500, "history write failed");
		}

		ObjectNode entry = OBJECT_MAPPER.createObjectNode();

		long id = _ids.incrementAndGet();

		entry.put("id", id);
		entry.put("applicationId", applicationId);
		entry.put("fromStatus", (from == null) ? null : from.key());
		entry.put("toStatus", to.key());
		entry.put("actorUserId", actorUserId);
		entry.put("reason", reason);
		entry.put("changedAt", changedAt.toString());

		statusHistories.put(id, entry);

		return id;
	}

	public synchronized long createApplication(ApplicationStatus applicationStatus) {
		Map<String, Object> fields = new LinkedHashMap<>();

		fields.put("applicationStatus", applicationStatus.key());

		return Entries.id(createApplication(fields));
	}

	@Override
	public synchronized ObjectNode createApplication(Map<String, Object> fields) {
		Map<String, Object> withDefaults = new LinkedHashMap<>();

		withDefaults.put(
			"applicationStatus", ApplicationStatus.INVITATION_SENT.key());
		withDefaults.putAll(fields);

		return _create(applications, withDefaults);
	}

	@Override
	public synchronized ObjectNode createChild(
		ChildType childType, Map<String, Object> fields) {

		for (String field : REQUIRED_CHILD_FIELDS.get(childType)) {
			_checkRequired(childType, field, fields.get(field), "POST");
		}

		return _create(children.get(childType), fields);
	}

	@Override
	public synchronized ObjectNode createInvitation(Map<String, Object> fields) {
		return _create(invitations, fields);
	}

	@Override
	public synchronized void deleteChild(ChildType childType, long childId) {
		if (children.get(
				childType
			).remove(
				childId
			) == null) {

			throw new LiferayException(
				"DELETE", childType.path() + "/" + childId, 404, "not found");
		}
	}

	@Override
	public synchronized void deleteStatusHistory(long statusHistoryId) {
		statusHistories.remove(statusHistoryId);
	}

	@Override
	public synchronized ObjectNode findApplicationByInvitation(long invitationId) {
		List<ObjectNode> found = _find(
			applications,
			entry -> Entries.longValue(entry, Fields.APPLICATION_INVITATION) ==
				invitationId);

		return found.isEmpty() ? null : found.get(0);
	}

	@Override
	public synchronized List<ObjectNode> findApplicationsByStatus(
		Collection<ApplicationStatus> statuses) {

		Set<String> keys = statuses.stream(
		).map(
			ApplicationStatus::key
		).collect(
			Collectors.toSet()
		);

		return _find(
			applications,
			entry -> keys.contains(
				Entries.picklistKey(entry, "applicationStatus")));
	}

	@Override
	public synchronized List<ObjectNode> findChildren(
		ChildType childType, long applicationId) {

		return _find(
			children.get(childType),
			entry -> Entries.longValue(entry, childType.applicationField()) ==
				applicationId);
	}

	@Override
	public synchronized ObjectNode findInvitationByTokenHash(String tokenHash) {
		List<ObjectNode> found = _find(
			invitations,
			entry -> Objects.equals(Entries.text(entry, "tokenHash"), tokenHash));

		return found.isEmpty() ? null : found.get(0);
	}

	@Override
	public synchronized List<ObjectNode> findInvitationsByEmailAndState(
		String email, String state) {

		return _find(
			invitations,
			entry -> Objects.equals(Entries.text(entry, "email"), email) &&
				Objects.equals(Entries.picklistKey(entry, "state"), state));
	}

	@Override
	public synchronized List<ObjectNode> findStatusHistory(long applicationId) {
		return historyOf(applicationId);
	}

	@Override
	public synchronized ObjectNode getApplication(long applicationId) {
		return _get(applications, applicationId, "partnerapplications");
	}

	@Override
	public synchronized ObjectNode getInvitation(long invitationId) {
		return _get(invitations, invitationId, "partnerinvitations");
	}

	@Override
	public synchronized ObjectNode getSettings() {
		return settings.deepCopy();
	}

	public synchronized List<ObjectNode> historyOf(long applicationId) {
		List<ObjectNode> rows = new ArrayList<>();

		for (ObjectNode row : statusHistories.values()) {
			if (row.path(
					"applicationId"
				).asLong() == applicationId) {

				rows.add(row);
			}
		}

		return rows;
	}

	@Override
	public synchronized List<ObjectNode> listApplications() {
		return _find(applications, entry -> true);
	}

	@Override
	public synchronized List<ObjectNode> listInvitations(long invitedByUserId) {
		return _find(
			invitations,
			entry -> (invitedByUserId == 0) ||
				(Entries.longValue(entry, Fields.INVITATION_INVITED_BY) ==
					invitedByUserId));
	}

	@Override
	public synchronized ObjectNode patchApplication(
		long applicationId, Map<String, Object> fields) {

		if (failNextApplicationPatch) {
			failNextApplicationPatch = false;

			throw new LiferayException(
				"PATCH", "/o/c/partnerapplications/" + applicationId, 500,
				"patch failed");
		}

		return _patch(applications, applicationId, fields);
	}

	@Override
	public synchronized ObjectNode patchChild(
		ChildType childType, long childId, Map<String, Object> fields) {

		for (String field : REQUIRED_CHILD_FIELDS.get(childType)) {
			if (fields.containsKey(field)) {
				_checkRequired(childType, field, fields.get(field), "PATCH");
			}
		}

		return _patch(children.get(childType), childId, fields);
	}

	@Override
	public synchronized ObjectNode patchInvitation(
		long invitationId, Map<String, Object> fields) {

		return _patch(invitations, invitationId, fields);
	}

	public synchronized String statusOf(long applicationId) {
		return Entries.picklistKey(
			applications.get(applicationId), "applicationStatus");
	}

	/**
	 * Picklist fields are stored as {@code {"key": ...}} like Liferay returns
	 * them.
	 */
	public static final Set<String> PICKLIST_FIELDS = new HashSet<>(
		Arrays.asList(
			"applicationStatus", "documentType", "fromStatus", "posType",
			"revisionRequestedBy", "role", "stage", "state", "toStatus"));

	/**
	 * Required fields of the child objects ({@code docs/data-model.md}).
	 * Liferay answers 400 when one is missing, and so does this fake.
	 */
	public static final Map<ChildType, List<String>> REQUIRED_CHILD_FIELDS =
		new EnumMap<>(ChildType.class);

	static {
		REQUIRED_CHILD_FIELDS.put(
			ChildType.COMMENT, Arrays.asList("stage", "body"));
		REQUIRED_CHILD_FIELDS.put(
			ChildType.CONTACT, Arrays.asList("fullName", "email"));
		REQUIRED_CHILD_FIELDS.put(
			ChildType.DOCUMENT, Arrays.asList("documentType", "file"));
		REQUIRED_CHILD_FIELDS.put(ChildType.POS, Arrays.asList("name"));
	}

	public final Map<Long, ObjectNode> applications = new LinkedHashMap<>();
	public final Map<ChildType, Map<Long, ObjectNode>> children =
		new EnumMap<>(ChildType.class);
	public boolean failNextApplicationPatch;
	public boolean failNextHistoryWrite;
	public final Map<Long, ObjectNode> invitations = new LinkedHashMap<>();
	public ObjectNode settings = OBJECT_MAPPER.createObjectNode();
	public final Map<Long, ObjectNode> statusHistories = new LinkedHashMap<>();

	private void _checkRequired(
		ChildType childType, String field, Object value, String method) {

		if ((value == null) || String.valueOf(value).trim().isEmpty()) {
			throw new LiferayException(
				method, childType.path(), 400,
				"No value was provided for required object field \"" + field +
					"\"");
		}
	}

	private ObjectNode _create(
		Map<Long, ObjectNode> table, Map<String, Object> fields) {

		ObjectNode entry = OBJECT_MAPPER.createObjectNode();

		long id = _ids.incrementAndGet();

		entry.put("id", id);
		entry.put("externalReferenceCode", "ERC-" + id);

		_set(entry, fields);

		table.put(id, entry);

		return entry.deepCopy();
	}

	private List<ObjectNode> _find(
		Map<Long, ObjectNode> table, Predicate<ObjectNode> predicate) {

		return table.values(
		).stream(
		).filter(
			predicate
		).map(
			ObjectNode::deepCopy
		).collect(
			Collectors.toList()
		);
	}

	private ObjectNode _get(Map<Long, ObjectNode> table, long id, String name) {
		ObjectNode entry = table.get(id);

		if (entry == null) {
			throw new LiferayException(
				"GET", "/o/c/" + name + "/" + id, 404, "not found");
		}

		return entry.deepCopy();
	}

	private ObjectNode _patch(
		Map<Long, ObjectNode> table, long id, Map<String, Object> fields) {

		ObjectNode entry = table.get(id);

		if (entry == null) {
			throw new LiferayException("PATCH", "/o/c/" + id, 404, "not found");
		}

		_set(entry, fields);

		return entry.deepCopy();
	}

	private void _set(ObjectNode entry, Map<String, Object> fields) {
		for (Map.Entry<String, Object> field : fields.entrySet()) {
			if (PICKLIST_FIELDS.contains(field.getKey()) &&
				(field.getValue() instanceof String)) {

				entry.putObject(
					field.getKey()
				).put(
					"key", (String)field.getValue()
				);
			}
			else {
				entry.set(
					field.getKey(),
					OBJECT_MAPPER.valueToTree(field.getValue()));
			}
		}
	}

	private final AtomicLong _ids = new AtomicLong(1000);

}
