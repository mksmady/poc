// Validation PM_VAL_INVITATION_RESEND on PM_OBJ_INVITATION (US-001 AC6).
// A resend is an update that raises resendCount. It is allowed for the
// inviting or assigned AM and for admins, while the application is
// invitationSent, registrationInProgress or expired.

import com.liferay.portal.kernel.module.util.SystemBundleUtil

def service = { String name ->
	def bundleContext = SystemBundleUtil.getBundleContext()

	bundleContext.getService(bundleContext.getServiceReference(name))
}

validationCriteriaMet = true

long self = (binding.variables.id ?: 0L) as long

if (self == 0L) {
	return
}

def objectDefinitionLocalService = service("com.liferay.object.service.ObjectDefinitionLocalService")
def objectEntryLocalService = service("com.liferay.object.service.ObjectEntryLocalService")
def roleLocalService = service("com.liferay.portal.kernel.service.RoleLocalService")

// On add, id is already the new key but nothing is stored yet

def storedEntry = objectEntryLocalService.fetchObjectEntry(self)

if (storedEntry == null) {
	return
}

def stored = objectEntryLocalService.getValues(storedEntry)

if (((resendCount ?: 0) as int) == ((stored.resendCount ?: 0) as int)) {
	return
}

long companyId = storedEntry.getCompanyId()
long userId = com.liferay.portal.kernel.security.auth.PrincipalThreadLocal.getUserId()

boolean admin = ["Administrator", "Partner Portal Admin"].any { roleLocalService.hasUserRole(userId, companyId, it, true) }

long applicationDefinitionId = objectDefinitionLocalService.getObjectDefinitionByExternalReferenceCode("PM_OBJ_APPLICATION", companyId).getObjectDefinitionId()

def application = objectEntryLocalService.getObjectEntries(0L, applicationDefinitionId, -1, -1).collect { objectEntryLocalService.getValues(it) }.find {
	(it["r_invitationApplication_c_partnerInvitationId"] ?: 0L) as long == self
}

boolean own = ((stored["r_invitationInvitedBy_userId"] ?: 0L) as long == userId) || (application && ((application["r_applicationAssignedAM_userId"] ?: 0L) as long == userId))

validationCriteriaMet = application && (application.applicationStatus in ["invitationSent", "registrationInProgress", "expired"]) && (admin || (own && roleLocalService.hasUserRole(userId, companyId, "Partner Account Manager", true)))
