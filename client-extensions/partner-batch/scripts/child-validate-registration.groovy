// Validation PM_VAL_<CHILD>_REGISTRATION on PM_OBJ_CONTACT, PM_OBJ_POS and
// PM_OBJ_DOCUMENT (US-002 AC11): the "Partner Registration Client" user may
// add or change a child only while its application can still be edited.
// Deletes are not validated by Liferay; see docs/open-questions.md.

import com.liferay.portal.kernel.module.util.SystemBundleUtil

def service = { String name ->
	def bundleContext = SystemBundleUtil.getBundleContext()

	bundleContext.getService(bundleContext.getServiceReference(name))
}

validationCriteriaMet = true

def roleLocalService = service("com.liferay.portal.kernel.service.RoleLocalService")

long companyId = com.liferay.portal.kernel.util.PortalUtil.getDefaultCompanyId()
long userId = com.liferay.portal.kernel.security.auth.PrincipalThreadLocal.getUserId()

if (!roleLocalService.hasUserRole(userId, companyId, "Partner Registration Client", true)) {
	return
}

def parentField = [
	"r_applicationContacts_c_partnerApplicationId",
	"r_applicationDocuments_c_partnerApplicationId",
	"r_applicationPosLocations_c_partnerApplicationId"
].find { binding.variables.containsKey(it) }

long applicationId = parentField ? ((binding.variables[parentField] ?: 0L) as long) : 0L

if (applicationId == 0L) {
	validationCriteriaMet = false

	return
}

def objectEntryLocalService = service("com.liferay.object.service.ObjectEntryLocalService")
def application = objectEntryLocalService.fetchObjectEntry(applicationId)

validationCriteriaMet = (application != null) && (objectEntryLocalService.getValues(application).applicationStatus in ["invitationSent", "registrationInProgress", "revisionRequested"])
