package com.partnerportal.service.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.partnerportal.service.liferay.Entries;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.stereotype.Service;

/**
 * The only writer of {@code applicationStatus} (CLAUDE.md rule 4). Checks the
 * transition against {@code docs/status-lifecycle.md}, writes
 * {@code StatusHistory}, then updates the application. If the application
 * update fails, the history row is removed so history and status stay in step.
 */
@Service
public class StatusService {

	public static final String FIELD_APPLICATION_STATUS = "applicationStatus";

	public StatusService(PartnerRepository partnerRepository, Clock clock) {
		_partnerRepository = partnerRepository;
		_clock = clock;
	}

	public ApplicationStatus getStatus(ObjectNode application) {
		return ApplicationStatus.fromKey(
			Entries.picklistKey(application, FIELD_APPLICATION_STATUS));
	}

	/**
	 * Records the "(new) → invitationSent" row for an application that was
	 * just created with the picklist default.
	 */
	public void recordCreated(long applicationId, long actorUserId) {
		_partnerRepository.addStatusHistory(
			applicationId, null, ApplicationStatus.INVITATION_SENT, actorUserId,
			null, Instant.now(_clock));
	}

	public ApplicationStatus transition(
		long applicationId, ApplicationStatus to, long actorUserId,
		String reason) {

		return transition(
			applicationId, to, actorUserId, reason, Collections.emptyMap());
	}

	/**
	 * Moves the application only when its status is still {@code expected},
	 * so concurrent callers (two tabs opening the same link) make one change.
	 * Serialized per application within this service instance.
	 *
	 * @return whether this call made the change
	 */
	public boolean transitionIfCurrent(
		long applicationId, ApplicationStatus expected, ApplicationStatus to,
		long actorUserId, String reason) {

		synchronized (_lock(applicationId)) {
			ObjectNode application = _partnerRepository.getApplication(
				applicationId);

			if (getStatus(application) != expected) {
				return false;
			}

			transition(applicationId, to, actorUserId, reason);

			return true;
		}
	}

	/**
	 * @param  extraFields other application fields to write in the same update
	 *         (for example {@code statusReason} or {@code submittedAt})
	 * @return the status before the change
	 */
	public ApplicationStatus transition(
		long applicationId, ApplicationStatus to, long actorUserId,
		String reason, Map<String, Object> extraFields) {

		if (extraFields.containsKey(FIELD_APPLICATION_STATUS)) {
			throw new IllegalArgumentException(
				"Pass the target status as the to argument");
		}

		synchronized (_lock(applicationId)) {
			return _transition(
				applicationId, to, actorUserId, reason, extraFields);
		}
	}

	private Object _lock(long applicationId) {
		return _locks[(int)Math.floorMod(applicationId, (long)_locks.length)];
	}

	private ApplicationStatus _transition(
		long applicationId, ApplicationStatus to, long actorUserId,
		String reason, Map<String, Object> extraFields) {

		ObjectNode application = _partnerRepository.getApplication(
			applicationId);

		ApplicationStatus from = getStatus(application);

		if (!from.canTransitionTo(to)) {
			throw new IllegalTransitionException(from, to);
		}

		long statusHistoryId = _partnerRepository.addStatusHistory(
			applicationId, from, to, actorUserId, reason, Instant.now(_clock));

		Map<String, Object> fields = new LinkedHashMap<>(extraFields);

		fields.put(FIELD_APPLICATION_STATUS, to.key());

		try {
			_partnerRepository.patchApplication(applicationId, fields);
		}
		catch (RuntimeException runtimeException) {
			try {
				_partnerRepository.deleteStatusHistory(statusHistoryId);
			}
			catch (RuntimeException deleteRuntimeException) {
				_log.error(
					"Unable to remove status history " + statusHistoryId +
						" after a failed status change",
					deleteRuntimeException);

				runtimeException.addSuppressed(deleteRuntimeException);
			}

			throw runtimeException;
		}

		if (_log.isInfoEnabled()) {
			_log.info(
				"Application " + applicationId + ": " + from.key() + " -> " +
					to.key() + " by user " + actorUserId);
		}

		return from;
	}

	private static final Log _log = LogFactory.getLog(StatusService.class);

	private final Clock _clock;

	/**
	 * Lock stripes: transitions of one application run one at a time, so
	 * the "from" status checked is the one being changed. Locks are re-entrant
	 * (synchronized), which transitionIfCurrent relies on.
	 */
	private final Object[] _locks = _createLocks(64);

	private final PartnerRepository _partnerRepository;

	private static Object[] _createLocks(int count) {
		Object[] locks = new Object[count];

		for (int i = 0; i < count; i++) {
			locks[i] = new Object();
		}

		return locks;
	}

}
