package com.partnerportal.service.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The wizard data exchanged with {@code partner-registration} (US-002). Field
 * names match the PartnerApplication and child object fields in
 * {@code docs/data-model.md}. {@link #documents} and {@link #existingCompany}
 * are filled by the service and ignored on save.
 */
public class RegistrationDraft {

	public Company company = new Company();
	public Compliance compliance = new Compliance();
	public List<Contact> contacts = new ArrayList<>();
	public List<Document> documents = new ArrayList<>();
	public boolean existingCompany;
	public List<PosLocation> posLocations = new ArrayList<>();
	public Map<String, String> questionnaire = new LinkedHashMap<>();

	public static class Company {

		public String city;
		public String companyName;
		public String country;
		public String employeeBand;
		public String postalCode;
		public String registrationNumber;
		public String street;
		public String vatId;
		public String website;

	}

	public static class Compliance {

		public String certifications;
		public String complianceNotes;
		public boolean gdprConsent;
		public boolean sanctionsDeclaration;

	}

	public static class Contact {

		public String email;
		public String fullName;
		public Long id;
		public String jobTitle;
		public String phone;
		public String role;

	}

	public static class Document {

		public String documentType;
		public String fileName;
		public Long id;
		public String note;

	}

	public static class PosLocation {

		public List<String> categories = new ArrayList<>();
		public String city;
		public String country;
		public Long id;
		public String name;
		public String posType;
		public String postalCode;
		public String street;

	}

}
