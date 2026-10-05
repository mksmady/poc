// Validation PM_VAL_INVITATION_ACTIVE_UNIQUE on PM_OBJ_INVITATION (US-001 AC2):
// at most one active invitation per email, compared without case.
// Liferay refuses the save when validationCriteriaMet is false.

import com.liferay.portal.kernel.module.util.SystemBundleUtil

def service = { String name ->
	def bundleContext = SystemBundleUtil.getBundleContext()

	bundleContext.getService(bundleContext.getServiceReference(name))
}

validationCriteriaMet = true

if ((state != "active") || !email) {
	return
}

def objectDefinitionLocalService = service("com.liferay.object.service.ObjectDefinitionLocalService")
def objectEntryLocalService = service("com.liferay.object.service.ObjectEntryLocalService")

long companyId = com.liferay.portal.kernel.util.PortalUtil.getDefaultCompanyId()
long definitionId = objectDefinitionLocalService.getObjectDefinitionByExternalReferenceCode("PM_OBJ_INVITATION", companyId).getObjectDefinitionId()
String normalized = email.trim().toLowerCase(Locale.ROOT)
long self = (binding.variables.id ?: 0L) as long

validationCriteriaMet = !objectEntryLocalService.getObjectEntries(0L, definitionId, -1, -1).any {
	if (it.getObjectEntryId() == self) {
		return false
	}

	def other = objectEntryLocalService.getValues(it)

	(other.state == "active") && (String.valueOf(other.email).trim().toLowerCase(Locale.ROOT) == normalized)
}
