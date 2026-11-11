package com.example.demo.migration.mapping;

import static com.example.demo.migration.mapping.ColumnRule.copy;

/**
 * dbo.Old_Ussd_Account -> old_ussd_account, per the "Old_USSD_account" sheet of
 * MobileBanking_Users_Devices_OldUSSDAccount_Mapping.xlsx.
 */
public final class OldUssdAccountSchema {

	public static final ColumnRule[] RULES = {
			copy("id", "id"),
			copy("emailAddress", "email_address"),
			copy("fullName", "full_name"),
			copy("password", "password"),
			copy("phoneNumber", "phone_number"),
			copy("primaryAccountNumber", "primary_account_number"),
			copy("userName", "user_name")
	};

	public static final String[] HEADER = ColumnRule.header(RULES);

	private OldUssdAccountSchema() {
	}
}
