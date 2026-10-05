package com.partnerportal.service.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;

import com.partnerportal.service.liferay.Urls;
import com.partnerportal.service.support.FakeLiferayClient;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RoleBootstrapTest {

	@BeforeEach
	public void setUp() {
		_fakeLiferayClient = new FakeLiferayClient();

		_fakeLiferayClient.respond(
			"/o/headless-portal-instances/v1.0/portal-instances",
			"{\"items\": [{\"active\": true, \"companyId\": 777}]}");

		String[] ercs = {
			"PM_OBJ_INVITATION", "PM_OBJ_APPLICATION", "PM_OBJ_CONTACT",
			"PM_OBJ_POS", "PM_OBJ_DOCUMENT", "PM_OBJ_COMMENT",
			"PM_OBJ_STATUS_HISTORY", "PM_OBJ_SETTINGS"
		};

		for (int i = 0; i < ercs.length; i++) {
			_fakeLiferayClient.respond(
				"/o/object-admin/v1.0/object-definitions" +
					"/by-external-reference-code/" + ercs[i],
				"{\"id\": " + (100 + i) + "}");
		}
	}

	@Test
	public void testAmCanAddInvitationsButOnlyViewApplications() {
		new RoleBootstrap(
			_fakeLiferayClient
		).apply();

		JsonNode am = _role(RoleBootstrap.ROLE_AM);

		Assertions.assertEquals("Partner Account Manager", am.get(
			"name"
		).asText());
		Assertions.assertEquals("regular", am.get(
			"roleType"
		).asText());

		Assertions.assertEquals(
			"[\"VIEW\"]", _actions(am, "com.liferay.object.model.ObjectDefinition#101"));
		Assertions.assertNull(_actions(am, "com.liferay.object#101"));
		Assertions.assertEquals(
			"[\"ADD_OBJECT_ENTRY\"]", _actions(am, "com.liferay.object#100"));
		Assertions.assertNull(
			_actions(am, "com.liferay.object.model.ObjectDefinition#107"));

		for (JsonNode rolePermission : am.get("rolePermissions")) {
			Assertions.assertEquals(1, rolePermission.get(
				"scope"
			).asInt());
			Assertions.assertEquals("777", rolePermission.get(
				"primaryKey"
			).asText());
		}
	}

	@Test
	public void testNoRoleCanUpdateApplicationsOrTouchStatusHistory() {
		new RoleBootstrap(
			_fakeLiferayClient
		).apply();

		for (String erc : new String[] {
				RoleBootstrap.ROLE_AM, RoleBootstrap.ROLE_LEGAL,
				RoleBootstrap.ROLE_ADMIN
			}) {

			JsonNode role = _role(erc);

			Assertions.assertEquals(
				"[\"VIEW\"]",
				_actions(role, "com.liferay.object.model.ObjectDefinition#101"),
				erc);
			Assertions.assertEquals(
				"[\"VIEW\"]",
				_actions(role, "com.liferay.object.model.ObjectDefinition#106"),
				erc);
			Assertions.assertNull(
				_actions(role, "com.liferay.object#106"), erc);
		}

		Assertions.assertEquals(
			"[\"VIEW\",\"UPDATE\"]",
			_actions(
				_role(RoleBootstrap.ROLE_ADMIN),
				"com.liferay.object.model.ObjectDefinition#107"));
	}

	@Test
	public void testRunDoesNotFailWhenLiferayIsDown() {
		new RoleBootstrap(
			new FakeLiferayClient()
		).run(
			null
		);
	}

	private String _actions(JsonNode role, String resourceName) {
		for (JsonNode rolePermission : role.get("rolePermissions")) {
			if (rolePermission.get(
					"resourceName"
				).asText(
				).equals(
					resourceName
				)) {

				return rolePermission.get(
					"actionIds"
				).toString();
			}
		}

		return null;
	}

	private JsonNode _role(String name) {
		List<JsonNode> bodies = new ArrayList<>();

		for (FakeLiferayClient.Call call : _fakeLiferayClient.calls) {
			if (call.method.equals("PUT") &&
				call.path.equals(
					"/o/headless-admin-user/v1.0/roles" +
						"/by-external-reference-code/" +
						Urls.segment(name))) {

				bodies.add(call.body);
			}
		}

		Assertions.assertEquals(1, bodies.size(), name);

		return bodies.get(0);
	}

	private FakeLiferayClient _fakeLiferayClient;

}
