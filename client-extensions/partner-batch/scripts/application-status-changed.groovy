// Runs after every applicationStatus change of a PartnerApplication
// (action PM_ACTION_APPLICATION_STATUS_CHANGED, onAfterUpdate, condition
// oldValue("applicationStatus") != applicationStatus).
// It is the one writer of StatusHistory (CLAUDE.md rule 4): the picklist state
// flow has already refused transitions not in docs/status-lifecycle.md.
// On submitted (US-002 AC10): stamps submittedAt, marks the invitation used,
// sends PM_SUBMITTED to the partner and the assigned AM (and legal after a
// legal revision), then routes to approvalPending or underLegalReview.

import com.liferay.portal.kernel.module.util.SystemBundleUtil
import com.liferay.portal.kernel.service.ServiceContext
import com.liferay.portal.kernel.util.HtmlUtil

def log = com.liferay.portal.kernel.log.LogFactoryUtil.getLog("partner.application.status")

def service = { String name ->
	def bundleContext = SystemBundleUtil.getBundleContext()
	def reference = bundleContext.getServiceReference(name)

	if (reference == null) {
		throw new IllegalStateException("Service not available: " + name)
	}

	bundleContext.getService(reference)
}

def objectDefinitionLocalService = service("com.liferay.object.service.ObjectDefinitionLocalService")
def objectEntryLocalService = service("com.liferay.object.service.ObjectEntryLocalService")
def userLocalService = service("com.liferay.portal.kernel.service.UserLocalService")

def applicationEntry = objectEntryLocalService.getObjectEntry(id)
long companyId = applicationEntry.getCompanyId()
long actorUserId = Long.valueOf(String.valueOf(currentUserId))

def definitionId = { String erc ->
	objectDefinitionLocalService.getObjectDefinitionByExternalReferenceCode(erc, companyId).getObjectDefinitionId()
}

def entries = { String erc ->
	objectEntryLocalService.getObjectEntries(0L, definitionId(erc), -1, -1)
}

def values = { entry -> new HashMap(objectEntryLocalService.getValues(entry)) }

def serviceContext = { long userId ->
	def context = new ServiceContext()

	context.setCompanyId(companyId)
	context.setUserId(userId)

	context
}

// The follow-up changes below are the portal's, not the partner's: they run
// as the assigned AM so the registration page limits
// (application-validate-registration.groovy) do not apply to them

def systemUserId = { Map application ->
	long userId = (application["r_applicationAssignedAM_userId"] ?: 0L) as long

	if (userId == 0L) {
		def admin = service("com.liferay.portal.kernel.service.RoleLocalService").getRole(companyId, "Administrator")

		userId = userLocalService.getRoleUsers(admin.getRoleId()).first().getUserId()
	}

	userId
}

def update = { long userId, entry, Map changes ->
	def merged = values(entry)

	merged.putAll(changes)

	def principal = com.liferay.portal.kernel.security.auth.PrincipalThreadLocal

	String previous = principal.getName()

	principal.setName(userId)

	try {
		objectEntryLocalService.updateObjectEntry(userId, entry.getObjectEntryId(), merged, serviceContext(userId))
	}
	finally {
		principal.setName(previous)
	}
}

def history = {
	entries("PM_OBJ_STATUS_HISTORY").collect { values(it) + [_id: it.getObjectEntryId()] }.findAll {
		(it["r_applicationStatusHistory_c_partnerApplicationId"] ?: 0L) as long == (id as long)
	}.sort { it._id }
}

def record = { String to, String reason ->
	def rows = history()
	String from = rows ? rows.last().toStatus : null

	if (from == to) {

		// Already recorded (a repeated PATCH, or this script re-entered)

		return false
	}

	Map fields = [
		changedAt: new Date((long)(System.currentTimeMillis() / 1000L) * 1000L),
		r_applicationStatusHistory_c_partnerApplicationId: id as long,
		r_statusHistoryActor_userId: actorUserId,
		toStatus: to
	]

	if (from) {
		fields.fromStatus = from
	}

	if (reason) {
		fields.reason = reason
	}

	objectEntryLocalService.addObjectEntry(actorUserId, 0L, definitionId("PM_OBJ_STATUS_HISTORY"), new HashMap(fields), serviceContext(actorUserId))

	true
}

def application = values(applicationEntry)
String status = application.applicationStatus

if (!record(status, null) || (status != "submitted")) {
	return
}

// Submitted: the invitation link now opens the application read-only

long invitationId = (application["r_invitationApplication_c_partnerInvitationId"] ?: 0L) as long
def invitationEntry = invitationId ? objectEntryLocalService.fetchObjectEntry(invitationId) : null
def invitation = invitationEntry ? values(invitationEntry) : [:]

if (invitationEntry) {
	update(systemUserId(application), invitationEntry, [state: "used"])
}

String reviewStage = (application.revisionRequestedBy == "legal") ? "underLegalReview" : "approvalPending"

update(systemUserId(application), applicationEntry, [applicationStatus: reviewStage, submittedAt: application.submittedAt ?: new Date()])

// Re-entry may already have written this row; record() skips duplicates

record(reviewStage, "Routed to review")

// PM_SUBMITTED

try {
	def notificationType = service("com.liferay.notification.type.NotificationTypeServiceTracker").getNotificationType("email")
	def template = service("com.liferay.notification.service.NotificationTemplateLocalService").getNotificationTemplateByExternalReferenceCode("PM_SUBMITTED", companyId)
	def builderClass = notificationType.getClass().getClassLoader().loadClass("com.liferay.notification.context.NotificationContextBuilder")

	String companyName = HtmlUtil.escape(String.valueOf(application.companyName ?: ""))

	def send = { String to, String name, String message ->
		def context = builderClass.newInstance().className(

			// Neutral class name, see invitation-issue.groovy

			"PartnerPortal"
		).classPK(
			id as long
		).notificationTemplate(
			template
		).termValues(
			[
				"[%COMPANY_NAME%]": companyName,
				"[%MESSAGE%]": HtmlUtil.escape(message),
				"[%PARTNER_NAME%]": HtmlUtil.escape(name),
				"[%RECIPIENT_EMAIL%]": to
			]
		).userId(
			actorUserId
		).build()

		notificationType.sendNotification(context)
	}

	if (invitation.email) {
		send(invitation.email, invitation.firstName + " " + invitation.lastName, "Thank you. We will review your application and contact you by email.")
	}

	Set<Long> reviewers = new LinkedHashSet<>()

	long amUserId = (application["r_applicationAssignedAM_userId"] ?: invitation["r_invitationInvitedBy_userId"] ?: 0L) as long

	if (amUserId) {
		reviewers.add(amUserId)
	}

	if (reviewStage == "underLegalReview") {
		def role = service("com.liferay.portal.kernel.service.RoleLocalService").fetchRole(companyId, "Partner Legal")

		if (role) {
			userLocalService.getRoleUsers(role.getRoleId()).each { reviewers.add(it.getUserId()) }
		}
	}

	reviewers.each { long userId ->
		def user = userLocalService.getUser(userId)

		send(user.getEmailAddress(), user.getFullName(), "A partner application is waiting for your review.")
	}
}
catch (Exception exception) {
	log.error("PM_SUBMITTED could not be queued for application " + id, exception)
}
