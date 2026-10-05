// Issues the registration link of a PartnerInvitation (US-001 AC3 to AC6).
// Runs as two object actions on PM_OBJ_INVITATION (see scripts/README.md):
//   PM_ACTION_INVITATION_CREATED  onAfterAdd
//   PM_ACTION_INVITATION_RESEND   onAfterUpdate, when resendCount changed
// First run (no tokenHash): sets the expiry, creates the PartnerApplication in
// invitationSent with its StatusHistory row. Resend: revokes the old link by
// replacing the hash, sets a new expiry, moves an expired application back to
// invitationSent. Both send PM_INVITE with the raw token, which is never
// stored (CLAUDE.md rule 6).

import com.liferay.portal.kernel.module.util.SystemBundleUtil
import com.liferay.portal.kernel.service.ServiceContext
import com.liferay.portal.kernel.util.HtmlUtil

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat

def log = com.liferay.portal.kernel.log.LogFactoryUtil.getLog("partner.invitation.issue")

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

def invitationEntry = objectEntryLocalService.getObjectEntry(id)
long companyId = invitationEntry.getCompanyId()

def definitionId = { String erc ->
	objectDefinitionLocalService.getObjectDefinitionByExternalReferenceCode(erc, companyId).getObjectDefinitionId()
}

def entries = { String erc ->
	objectEntryLocalService.getObjectEntries(0L, definitionId(erc), -1, -1)
}

def values = { entry -> new HashMap(objectEntryLocalService.getValues(entry)) }

def update = { long userId, entry, Map changes ->
	def merged = values(entry)

	merged.putAll(changes)

	def serviceContext = new ServiceContext()

	serviceContext.setCompanyId(companyId)
	serviceContext.setUserId(userId)

	objectEntryLocalService.updateObjectEntry(userId, entry.getObjectEntryId(), merged, serviceContext)
}

def add = { long userId, String erc, Map fields ->
	def serviceContext = new ServiceContext()

	serviceContext.setCompanyId(companyId)
	serviceContext.setUserId(userId)

	objectEntryLocalService.addObjectEntry(userId, 0L, definitionId(erc), new HashMap(fields), serviceContext)
}

def invitation = values(invitationEntry)
boolean firstIssue = (invitation.tokenHash == null) || invitation.tokenHash.toString().isEmpty()

long amUserId = (invitation["r_invitationInvitedBy_userId"] ?: 0L) as long

if (amUserId == 0L) {
	amUserId = Long.valueOf(String.valueOf(currentUserId))
}

// Settings (CLAUDE.md rule 8)

def settings = entries("PM_OBJ_SETTINGS").collect { values(it) }.find { true } ?: [:]
int expiryDays = (settings.invitationExpiryDays ?: 30) as int

// Token: 32 random bytes, URL-safe base64; only the SHA-256 hex is stored

byte[] bytes = new byte[32]

new SecureRandom().nextBytes(bytes)

String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
String tokenHash = MessageDigest.getInstance("SHA-256").digest(token.getBytes("UTF-8")).collect { String.format("%02x", it & 0xff) }.join()

Date now = new Date((long)(System.currentTimeMillis() / 1000L) * 1000L)
Date expiresAt = new Date(now.getTime() + expiryDays * 24L * 60L * 60L * 1000L)

Map changes = [expiresAt: expiresAt, sentAt: now, state: "active", tokenHash: tokenHash]

if (firstIssue) {

	// resendCount is left alone: changing it would trigger the resend action

	changes.reminderCount = 0
	changes["r_invitationInvitedBy_userId"] = amUserId
}

update(amUserId, invitationEntry, changes)

// The application: exactly one per invitation

def applicationEntry = entries("PM_OBJ_APPLICATION").find {
	(values(it)["r_invitationApplication_c_partnerInvitationId"] ?: 0L) as long == (id as long)
}

if (applicationEntry == null) {
	applicationEntry = add(
		amUserId, "PM_OBJ_APPLICATION",
		[
			applicationStatus: "invitationSent",
			r_applicationAssignedAM_userId: amUserId,
			r_invitationApplication_c_partnerInvitationId: id as long
		])

	add(
		amUserId, "PM_OBJ_STATUS_HISTORY",
		[
			changedAt: now,
			r_applicationStatusHistory_c_partnerApplicationId: applicationEntry.getObjectEntryId(),
			r_statusHistoryActor_userId: amUserId,
			toStatus: "invitationSent"
		])
}
else if (values(applicationEntry).applicationStatus == "expired") {

	// The application action writes the history row for this change

	update(amUserId, applicationEntry, [applicationStatus: "invitationSent"])
}

// PM_INVITE with the link; the raw token exists only in this email

String registrationUrl = settings.registrationUrl

if (!registrationUrl) {
	def company = service("com.liferay.portal.kernel.service.CompanyLocalService").getCompany(companyId)
	def portal = com.liferay.portal.kernel.util.PortalUtil

	registrationUrl = portal.getPortalURL(company.getVirtualHostname(), portal.getPortalServerPort(false), false) + "/web/guest/partner-registration"
}

String link = registrationUrl + "?t=" + token

SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)

dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

try {
	def notificationType = service("com.liferay.notification.type.NotificationTypeServiceTracker").getNotificationType("email")
	def template = service("com.liferay.notification.service.NotificationTemplateLocalService").getNotificationTemplateByExternalReferenceCode("PM_INVITE", companyId)
	def am = userLocalService.getUser(amUserId)

	def builder = notificationType.getClass().getClassLoader().loadClass("com.liferay.notification.context.NotificationContextBuilder").newInstance()

	def context = builder.className(

		// A neutral class name: with an object class name Liferay evaluates the
		// terms itself and ignores the values passed here

		"PartnerPortal"
	).classPK(
		id as long
	).notificationTemplate(
		template
	).termValues(
		[
			"[%AM_NAME%]": HtmlUtil.escape(am.getFullName()),
			"[%EXPIRES_AT%]": dateFormat.format(expiresAt),
			"[%LINK%]": HtmlUtil.escapeHREF(link),
			"[%PARTNER_NAME%]": HtmlUtil.escape(invitation.firstName + " " + invitation.lastName),
			"[%RECIPIENT_EMAIL%]": invitation.email
		]
	).userId(
		amUserId
	).build()

	notificationType.sendNotification(context)
}
catch (Exception exception) {

	// The invitation stays valid; the AM can resend

	log.error("PM_INVITE could not be queued for invitation " + id, exception)
}
