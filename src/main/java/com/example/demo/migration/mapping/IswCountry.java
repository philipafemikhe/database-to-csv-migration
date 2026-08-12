package com.example.demo.migration.mapping;

/**
 * country_id -> country, per the fixed table given in both DTypeMappingRulesforTransactions.docx
 * and TransferBeneficaries_Migration_Logic.xlsx (1 = Malawi, 2 = Botswana, 3 = Zimbabwe,
 * 4 = Zambia) - confirmed identical in both mapping docs, so this is a system-wide constant, not
 * specific to transactions. Unlike CountryPartnerRepository elsewhere in this project, this
 * mapping is given explicitly rather than resolved from a lookup table, so it's a fixed enum
 * instead of a dynamic-schema repository.
 */
public enum IswCountry {

	MALAWI(1, "MW"),
	BOTSWANA(2, "BW"),
	ZIMBABWE(3, "ZW"),
	ZAMBIA(4, "ZM");

	private final long countryId;
	private final String code;

	IswCountry(long countryId, String code) {
		this.countryId = countryId;
		this.code = code;
	}

	public String code() {
		return code;
	}

	public static IswCountry fromId(Long countryId) {
		if (countryId == null) {
			return null;
		}
		for (IswCountry country : values()) {
			if (country.countryId == countryId) {
				return country;
			}
		}
		return null;
	}
}
