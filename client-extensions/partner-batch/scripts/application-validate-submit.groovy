// Validation PM_VAL_APPLICATION_SUBMIT on PM_OBJ_APPLICATION (US-002 AC10):
// an application moves to submitted only when everything required is there.
// The wizard checks the same rules first and shows them field by field.

import com.liferay.portal.kernel.module.util.SystemBundleUtil

def service = { String name ->
	def bundleContext = SystemBundleUtil.getBundleContext()

	bundleContext.getService(bundleContext.getServiceReference(name))
}

validationCriteriaMet = true

long self = (binding.variables.id ?: 0L) as long

if ((applicationStatus != "submitted") || (self == 0L)) {
	return
}

def objectDefinitionLocalService = service("com.liferay.object.service.ObjectDefinitionLocalService")
def objectEntryLocalService = service("com.liferay.object.service.ObjectEntryLocalService")

def storedEntry = objectEntryLocalService.fetchObjectEntry(self)

if (storedEntry == null) {
	return
}

long companyId = storedEntry.getCompanyId()

// The entry already holds the new values while rules run; the status before
// this save is the latest StatusHistory row. Only a move into submitted is
// checked, not later saves of a submitted application

long historyDefinitionId = objectDefinitionLocalService.getObjectDefinitionByExternalReferenceCode("PM_OBJ_STATUS_HISTORY", companyId).getObjectDefinitionId()

def lastRow = objectEntryLocalService.getObjectEntries(0L, historyDefinitionId, -1, -1).findAll {
	(objectEntryLocalService.getValues(it)["r_applicationStatusHistory_c_partnerApplicationId"] ?: 0L) as long == self
}.max { it.getObjectEntryId() }

if (lastRow && (objectEntryLocalService.getValues(lastRow).toStatus == "submitted")) {
	return
}

def children = { String erc, String field ->
	long definitionId = objectDefinitionLocalService.getObjectDefinitionByExternalReferenceCode(erc, companyId).getObjectDefinitionId()

	objectEntryLocalService.getObjectEntries(0L, definitionId, -1, -1).collect { objectEntryLocalService.getValues(it) }.findAll {
		(it[field] ?: 0L) as long == self
	}
}

def filled = { value -> (value != null) && !String.valueOf(value).trim().isEmpty() }

boolean company = [companyName, registrationNumber, vatId, street, city, postalCode, website, employeeBand].every { filled(it) } && (String.valueOf(country) ==~ /[A-Za-z]{2}/)
boolean compliance = (gdprConsent == true) && (sanctionsDeclaration == true)
boolean questionnaireDone = filled(questionnaire) && questionnaire.contains("businessType")

boolean primaryContact = children("PM_OBJ_CONTACT", "r_applicationContacts_c_partnerApplicationId").any { it.role == "primary" }

def locations = children("PM_OBJ_POS", "r_applicationPosLocations_c_partnerApplicationId")
boolean posDone = !locations.isEmpty() && locations.every { filled(it.name) && filled(it.street) && filled(it.city) && filled(it.country) && filled(it.posType) && filled(it.categories) }

def documentTypes = children("PM_OBJ_DOCUMENT", "r_applicationDocuments_c_partnerApplicationId").collect { it.documentType } as Set
boolean documents = documentTypes.containsAll(["tradeRegister", "vatCertificate"])

validationCriteriaMet = company && compliance && questionnaireDone && primaryContact && posDone && documents
