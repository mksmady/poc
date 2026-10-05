package com.partnerportal.service.domain;

import static com.partnerportal.service.domain.ApplicationStatus.APPROVAL_PENDING;
import static com.partnerportal.service.domain.ApplicationStatus.AUTHORIZED;
import static com.partnerportal.service.domain.ApplicationStatus.CONTRACT_PENDING;
import static com.partnerportal.service.domain.ApplicationStatus.DEACTIVATED;
import static com.partnerportal.service.domain.ApplicationStatus.EXPIRED;
import static com.partnerportal.service.domain.ApplicationStatus.INVITATION_SENT;
import static com.partnerportal.service.domain.ApplicationStatus.REGISTRATION_IN_PROGRESS;
import static com.partnerportal.service.domain.ApplicationStatus.REJECTED;
import static com.partnerportal.service.domain.ApplicationStatus.REVISION_REQUESTED;
import static com.partnerportal.service.domain.ApplicationStatus.SUBMITTED;
import static com.partnerportal.service.domain.ApplicationStatus.TERMINATED;
import static com.partnerportal.service.domain.ApplicationStatus.UNDER_LEGAL_REVIEW;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.partnerportal.service.liferay.LiferayException;
import com.partnerportal.service.support.InMemoryPartnerRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Covers every row of {@code docs/status-lifecycle.md} as allowed, every other
 * pair as refused, and the refusals the doc names explicitly.
 */
public class StatusServiceTest {

	@BeforeEach
	public void setUp() {
		_repository = new InMemoryPartnerRepository();
		_statusService = new StatusService(
			_repository,
			Clock.fixed(Instant.parse("2026-10-04T08:00:00Z"), ZoneOffset.UTC));
	}

	@MethodSource("allowedTransitions")
	@ParameterizedTest(name = "{0} -> {1} allowed")
	public void testAllowedTransition(
		ApplicationStatus from, ApplicationStatus to) {

		long applicationId = _repository.createApplication(from);

		ApplicationStatus previous = _statusService.transition(
			applicationId, to, _ACTOR, "reason");

		Assertions.assertEquals(from, previous);
		Assertions.assertEquals(to.key(), _repository.statusOf(applicationId));

		List<ObjectNode> history = _repository.historyOf(applicationId);

		Assertions.assertEquals(1, history.size());

		ObjectNode row = history.get(0);

		Assertions.assertEquals(
			from.key(),
			row.get(
				"fromStatus"
			).asText());
		Assertions.assertEquals(
			to.key(),
			row.get(
				"toStatus"
			).asText());
		Assertions.assertEquals(
			_ACTOR,
			row.get(
				"actorUserId"
			).asLong());
		Assertions.assertEquals(
			"2026-10-04T08:00:00Z",
			row.get(
				"changedAt"
			).asText());
	}

	@Test
	public void testDocumentedRefusals() {
		_assertRefused(INVITATION_SENT, AUTHORIZED);
		_assertRefused(APPROVAL_PENDING, CONTRACT_PENDING);
		_assertRefused(SUBMITTED, REJECTED);

		for (ApplicationStatus to : ApplicationStatus.values()) {
			_assertRefused(REJECTED, to);
			_assertRefused(TERMINATED, to);
		}
	}

	@Test
	public void testExtraFieldsAreWrittenWithTheStatus() {
		long applicationId = _repository.createApplication(UNDER_LEGAL_REVIEW);

		_statusService.transition(
			applicationId, REJECTED, _ACTOR, "Sanctions match",
			Collections.singletonMap("statusReason", "Sanctions match"));

		Assertions.assertEquals(
			"Sanctions match",
			_repository.applications.get(
				applicationId
			).get(
				"statusReason"
			).asText());
	}

	@Test
	public void testExtraFieldsCannotCarryTheStatus() {
		long applicationId = _repository.createApplication(INVITATION_SENT);

		Assertions.assertThrows(
			IllegalArgumentException.class,
			() -> _statusService.transition(
				applicationId, EXPIRED, _ACTOR, null,
				Collections.singletonMap("applicationStatus", "authorized")));
	}

	@Test
	public void testFailedApplicationUpdateRemovesTheHistoryRow() {
		long applicationId = _repository.createApplication(APPROVAL_PENDING);

		_repository.failNextApplicationPatch = true;

		Assertions.assertThrows(
			LiferayException.class,
			() -> _statusService.transition(
				applicationId, UNDER_LEGAL_REVIEW, _ACTOR, null));

		Assertions.assertEquals(
			APPROVAL_PENDING.key(), _repository.statusOf(applicationId));
		Assertions.assertTrue(
			_repository.historyOf(
				applicationId
			).isEmpty());
	}

	@Test
	public void testFailedHistoryWriteLeavesTheStatusUnchanged() {
		long applicationId = _repository.createApplication(APPROVAL_PENDING);

		_repository.failNextHistoryWrite = true;

		Assertions.assertThrows(
			LiferayException.class,
			() -> _statusService.transition(
				applicationId, UNDER_LEGAL_REVIEW, _ACTOR, null));

		Assertions.assertEquals(
			APPROVAL_PENDING.key(), _repository.statusOf(applicationId));
	}

	/**
	 * The enum must allow exactly the rows in the lifecycle doc: nothing more,
	 * nothing less.
	 */
	@Test
	public void testMatrixMatchesTheLifecycleDocExactly() {
		Set<String> expected = new HashSet<>();

		allowedTransitions().forEach(
			arguments -> expected.add(
				arguments.get()[0] + ">" + arguments.get()[1]));

		Set<String> actual = new HashSet<>();

		for (ApplicationStatus from : ApplicationStatus.values()) {
			for (ApplicationStatus to : from.allowedTargets()) {
				actual.add(from + ">" + to);
			}
		}

		Assertions.assertEquals(expected, actual);
	}

	@Test
	public void testRecordCreated() {
		long applicationId = _repository.createApplication(INVITATION_SENT);

		_statusService.recordCreated(applicationId, _ACTOR);

		ObjectNode row = _repository.historyOf(
			applicationId
		).get(
			0
		);

		Assertions.assertTrue(
			row.get(
				"fromStatus"
			).isNull());
		Assertions.assertEquals(
			INVITATION_SENT.key(),
			row.get(
				"toStatus"
			).asText());
	}

	@MethodSource("refusedTransitions")
	@ParameterizedTest(name = "{0} -> {1} refused")
	public void testRefusedTransition(
		ApplicationStatus from, ApplicationStatus to) {

		_assertRefused(from, to);
	}

	static Stream<Arguments> allowedTransitions() {
		return Stream.of(
			Arguments.of(INVITATION_SENT, REGISTRATION_IN_PROGRESS),
			Arguments.of(INVITATION_SENT, EXPIRED),
			Arguments.of(REGISTRATION_IN_PROGRESS, EXPIRED),
			Arguments.of(EXPIRED, INVITATION_SENT),
			Arguments.of(REGISTRATION_IN_PROGRESS, SUBMITTED),
			Arguments.of(SUBMITTED, APPROVAL_PENDING),
			Arguments.of(SUBMITTED, UNDER_LEGAL_REVIEW),
			Arguments.of(APPROVAL_PENDING, REVISION_REQUESTED),
			Arguments.of(APPROVAL_PENDING, UNDER_LEGAL_REVIEW),
			Arguments.of(APPROVAL_PENDING, AUTHORIZED),
			Arguments.of(UNDER_LEGAL_REVIEW, REVISION_REQUESTED),
			Arguments.of(REVISION_REQUESTED, SUBMITTED),
			Arguments.of(UNDER_LEGAL_REVIEW, CONTRACT_PENDING),
			Arguments.of(UNDER_LEGAL_REVIEW, REJECTED),
			Arguments.of(CONTRACT_PENDING, AUTHORIZED),
			Arguments.of(CONTRACT_PENDING, TERMINATED),
			Arguments.of(AUTHORIZED, DEACTIVATED),
			Arguments.of(DEACTIVATED, AUTHORIZED),
			Arguments.of(AUTHORIZED, TERMINATED),
			Arguments.of(DEACTIVATED, TERMINATED));
	}

	static Stream<Arguments> refusedTransitions() {
		Set<String> allowed = new HashSet<>();

		allowedTransitions().forEach(
			arguments -> allowed.add(
				arguments.get()[0] + ">" + arguments.get()[1]));

		return Arrays.stream(
			ApplicationStatus.values()
		).flatMap(
			from -> EnumSet.allOf(
				ApplicationStatus.class
			).stream(
			).filter(
				to -> !allowed.contains(from + ">" + to)
			).map(
				to -> Arguments.of(from, to)
			)
		);
	}

	private void _assertRefused(ApplicationStatus from, ApplicationStatus to) {
		long applicationId = _repository.createApplication(from);

		IllegalTransitionException illegalTransitionException =
			Assertions.assertThrows(
				IllegalTransitionException.class,
				() -> _statusService.transition(
					applicationId, to, _ACTOR, null));

		Assertions.assertEquals(from, illegalTransitionException.getFrom());
		Assertions.assertEquals(to, illegalTransitionException.getTo());
		Assertions.assertEquals(from.key(), _repository.statusOf(applicationId));
		Assertions.assertTrue(
			_repository.historyOf(
				applicationId
			).isEmpty());
	}

	private static final long _ACTOR = 42;

	private InMemoryPartnerRepository _repository;
	private StatusService _statusService;

}
