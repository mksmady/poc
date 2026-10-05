package com.partnerportal.service.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;

import com.partnerportal.service.liferay.Entries;
import com.partnerportal.service.liferay.LiferayClient;
import com.partnerportal.service.liferay.Urls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Upserts the regular roles and their object permissions from the permissions
 * summary in {@code docs/data-model.md}. Batch JSON cannot carry object
 * permissions because their resource names hold numeric object definition
 * ids, so they are applied here, looked up by ERC, on every start.
 *
 * <p>AM and Legal only get VIEW on applications: every change to an application
 * goes through partner-service so {@code StatusService} stays the single writer
 * of the status (CLAUDE.md rule 4).
 */
@Component
@ConditionalOnProperty(
	havingValue = "true", matchIfMissing = true,
	name = "partner.bootstrap.enabled"
)
@Order(10)
public class RoleBootstrap implements ApplicationRunner {

	public static final String ROLE_ADMIN = "Partner Portal Admin";

	public static final String ROLE_AM = "Partner Account Manager";

	public static final String ROLE_LEGAL = "Partner Legal";

	public RoleBootstrap(LiferayClient liferayClient) {
		_liferayClient = liferayClient;
	}

	public void apply() {
		long companyId = _getCompanyId();

		Map<String, Long> objectDefinitionIds = new LinkedHashMap<>();

		for (String erc : _OBJECT_ERCS) {
			JsonNode objectDefinition = _liferayClient.get(
				"/o/object-admin/v1.0/object-definitions" +
					"/by-external-reference-code/" + erc);

			objectDefinitionIds.put(erc, Entries.id(objectDefinition));
		}

		for (RoleSpec roleSpec : roleSpecs()) {
			Map<String, Object> body = new LinkedHashMap<>();

			body.put("description", roleSpec.description);
			body.put("name", roleSpec.name);
			body.put("roleType", "regular");

			List<Map<String, Object>> rolePermissions = new ArrayList<>();

			for (Grant grant : roleSpec.grants) {
				long objectDefinitionId = objectDefinitionIds.get(
					grant.objectERC);

				if (!grant.entryActions.isEmpty()) {
					rolePermissions.add(
						_rolePermission(
							"com.liferay.object.model.ObjectDefinition#" +
								objectDefinitionId,
							companyId, grant.entryActions));
				}

				if (grant.add) {
					rolePermissions.add(
						_rolePermission(
							"com.liferay.object#" + objectDefinitionId,
							companyId,
							Collections.singletonList("ADD_OBJECT_ENTRY")));
				}
			}

			body.put("rolePermissions", rolePermissions);

			// DXP 2024.Q1 looks the role up by name in this endpoint and
			// renames the role to the path value on update, so the name is
			// the only stable key (docs/open-questions.md)

			_liferayClient.put(
				"/o/headless-admin-user/v1.0/roles/by-external-reference-code/" +
					Urls.segment(roleSpec.name),
				body);

			if (_log.isInfoEnabled()) {
				_log.info(
					"Applied role " + roleSpec.name + " with " +
						rolePermissions.size() + " permissions");
			}
		}
	}

	@Override
	public void run(ApplicationArguments applicationArguments) {
		try {
			apply();
		}
		catch (RuntimeException runtimeException) {

			// Liferay may still be starting; the service must come up anyway

			_log.error(
				"Unable to apply partner roles. Restart partner-service " +
					"once Liferay and partner-batch are deployed.",
				runtimeException);
		}
	}

	static List<RoleSpec> roleSpecs() {
		return Arrays.asList(
			new RoleSpec(
				"PM_ROLE_AM", ROLE_AM,
				"Invites partners and does the first review.",
				new Grant(_INVITATION, true, "VIEW"),
				new Grant(_APPLICATION, false, "VIEW"),
				new Grant(_CONTACT, false, "VIEW"),
				new Grant(_POS, false, "VIEW"),
				new Grant(_DOCUMENT, false, "VIEW"),
				new Grant(_COMMENT, true, "VIEW"),
				new Grant(_STATUS_HISTORY, false, "VIEW")),
			new RoleSpec(
				"PM_ROLE_LEGAL", ROLE_LEGAL,
				"Legal review, contract, reject and terminate.",
				new Grant(_INVITATION, false, "VIEW"),
				new Grant(_APPLICATION, false, "VIEW"),
				new Grant(_CONTACT, false, "VIEW"),
				new Grant(_POS, false, "VIEW"),
				new Grant(_DOCUMENT, false, "VIEW"),
				new Grant(_COMMENT, true, "VIEW"),
				new Grant(_STATUS_HISTORY, false, "VIEW")),
			new RoleSpec(
				"PM_ROLE_ADMIN", ROLE_ADMIN,
				"Settings, all partner records and admin alerts.",
				new Grant(_INVITATION, true, "VIEW", "UPDATE"),
				new Grant(_APPLICATION, false, "VIEW"),
				new Grant(_CONTACT, false, "VIEW"),
				new Grant(_POS, false, "VIEW"),
				new Grant(_DOCUMENT, false, "VIEW"),
				new Grant(_COMMENT, true, "VIEW"),
				new Grant(_STATUS_HISTORY, false, "VIEW"),
				new Grant(_SETTINGS, false, "VIEW", "UPDATE")));
	}

	static class Grant {

		Grant(String objectERC, boolean add, String... entryActions) {
			this.objectERC = objectERC;
			this.add = add;
			this.entryActions = Arrays.asList(entryActions);
		}

		final boolean add;
		final List<String> entryActions;
		final String objectERC;

	}

	static class RoleSpec {

		RoleSpec(String erc, String name, String description, Grant... grants) {
			this.erc = erc;
			this.name = name;
			this.description = description;
			this.grants = Arrays.asList(grants);
		}

		final String description;
		final String erc;
		final List<Grant> grants;
		final String name;

	}

	private long _getCompanyId() {
		JsonNode portalInstances = _liferayClient.get(
			"/o/headless-portal-instances/v1.0/portal-instances");

		for (JsonNode portalInstance : portalInstances.path("items")) {
			if (portalInstance.path(
					"active"
				).asBoolean(
					true
				)) {

				return portalInstance.path(
					"companyId"
				).asLong();
			}
		}

		throw new IllegalStateException("No active portal instance found");
	}

	private Map<String, Object> _rolePermission(
		String resourceName, long companyId, List<String> actionIds) {

		Map<String, Object> rolePermission = new LinkedHashMap<>();

		rolePermission.put("actionIds", actionIds);
		rolePermission.put("primaryKey", String.valueOf(companyId));
		rolePermission.put("resourceName", resourceName);
		rolePermission.put("scope", _SCOPE_COMPANY);

		return rolePermission;
	}

	private static final String _APPLICATION = "PM_OBJ_APPLICATION";

	private static final String _COMMENT = "PM_OBJ_COMMENT";

	private static final String _CONTACT = "PM_OBJ_CONTACT";

	private static final String _DOCUMENT = "PM_OBJ_DOCUMENT";

	private static final String _INVITATION = "PM_OBJ_INVITATION";

	private static final List<String> _OBJECT_ERCS = Arrays.asList(
		"PM_OBJ_INVITATION", "PM_OBJ_APPLICATION", "PM_OBJ_CONTACT",
		"PM_OBJ_POS", "PM_OBJ_DOCUMENT", "PM_OBJ_COMMENT",
		"PM_OBJ_STATUS_HISTORY", "PM_OBJ_SETTINGS");

	private static final String _POS = "PM_OBJ_POS";

	private static final int _SCOPE_COMPANY = 1;

	private static final String _SETTINGS = "PM_OBJ_SETTINGS";

	private static final String _STATUS_HISTORY = "PM_OBJ_STATUS_HISTORY";

	private static final Log _log = LogFactory.getLog(RoleBootstrap.class);

	private final LiferayClient _liferayClient;

}
