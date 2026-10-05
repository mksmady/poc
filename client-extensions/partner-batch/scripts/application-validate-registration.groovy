// Validation PM_VAL_APPLICATION_REGISTRATION on PM_OBJ_APPLICATION (US-002
// AC11). Limits the "Partner Registration Client" user, whose password the
// registration page carries: it may edit an application only while it is
// invitationSent, registrationInProgress or revisionRequested, and may only
// move it to registrationInProgress or submitted. The routing that follows a
// submit (submitted to a review stage) is allowed because
// application-status-changed.groovy runs as the same user.

import com.liferay.portal.kernel.module.util.SystemBundleUtil

def service = { String name ->
	def bundleContext = SystemBundleUtil.getBundleContext()

	bundleContext.getService(bundleContext.getServiceReference(name))
}

validationCriteriaMet = true

def objectEntryLocalService = service("com.liferay.object.service.ObjectEntryLocalService")
def roleLocalService = service("com.liferay.portal.kernel.service.RoleLocalService")

long companyId = com.liferay.portal.kernel.util.PortalUtil.getDefaultCompanyId()
long userId = com.liferay.portal.kernel.security.auth.PrincipalThreadLocal.getUserId()

if (!roleLocalService.hasUserRole(userId, companyId, "Partner Registration Client", true)) {
	return
}

long self = (binding.variables.id ?: 0L) as long

// On add, id is already the new key but nothing is stored yet; the
// registration user may not create applications

def storedEntry = (self == 0L) ? null : objectEntryLocalService.fetchObjectEntry(self)

if (storedEntry == null) {
	validationCriteriaMet = false

	return
}

// The entry already holds the submitted values while rules run, so the
// status before this save is the latest StatusHistory row (every change
// writes one, see application-status-changed.groovy)

def objectDefinitionLocalService = service("com.liferay.object.service.ObjectDefinitionLocalService")
long historyDefinitionId = objectDefinitionLocalService.getObjectDefinitionByExternalReferenceCode("PM_OBJ_STATUS_HISTORY", companyId).getObjectDefinitionId()

def lastRow = objectEntryLocalService.getObjectEntries(0L, historyDefinitionId, -1, -1).findAll {
	(objectEntryLocalService.getValues(it)["r_applicationStatusHistory_c_partnerApplicationId"] ?: 0L) as long == self
}.max { it.getObjectEntryId() }

String stored = lastRow ? objectEntryLocalService.getValues(lastRow).toStatus : objectEntryLocalService.getValues(storedEntry).applicationStatus
String next = applicationStatus

if ((stored == "submitted") && (next in ["approvalPending", "underLegalReview"])) {
	return
}

validationCriteriaMet = (stored in ["invitationSent", "registrationInProgress", "revisionRequested"]) && (next in [stored, "registrationInProgress", "submitted"])
