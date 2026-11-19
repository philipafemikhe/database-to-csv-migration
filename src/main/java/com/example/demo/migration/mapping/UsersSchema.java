package com.example.demo.migration.mapping;

import static com.example.demo.migration.mapping.ColumnRule.bool;
import static com.example.demo.migration.mapping.ColumnRule.copy;
import static com.example.demo.migration.mapping.ColumnRule.defaultZero;

/**
 * dbo.Users -> users, per the "Users" sheet of MobileBanking_Users_Devices_OldUSSDAccount_Mapping.xlsx.
 * A straight column-for-column copy (unlike customer_info, which reshapes Users into a new model).
 */
public final class UsersSchema {

	public static final ColumnRule[] RULES = {
			copy("DTYPE", "dtype"),
			copy("id", "id"),
			copy("dateRegistered", "date_registered"),
			copy("fullName", "full_name"),
			copy("lastLogin", "last_login"),
			copy("userName", "user_name"),
			bool("active", "active"),
			copy("customerType", "customer_type"),
			copy("emailAddress", "email_address"),
			copy("mobileNumber", "mobile_number"),
			copy("paymentCellNumber", "payment_cell_number"),
			copy("primaryAccountNumber", "primary_account_number"),
			copy("registrationStatus", "registration_status"),
			copy("salt", "salt"),
			copy("secret", "secret"),
			copy("country_id", "country_id"),
			copy("customerOpType", "customer_op_type"),
			copy("companyId", "company_id"),
			copy("corporateUserRoleId", "corporate_user_role_id"),
			defaultZero("corporateUserHierarchy", "corporate_user_hierarchy"),
			copy("corporateLimitRange", "corporate_limit_range"),
			copy("corporateInitiator", "corporate_initiator"),
			copy("lastDateCorporateUpdated", "last_date_corporate_updated"),
			copy("corporateAuthorizer", "corporate_authorizer"),
			copy("followWorkflow", "follow_workflow"),
			copy("corporateUserRoleDesc", "corporate_user_role_desc"),
			copy("lastTransactionString", "last_transaction_string"),
			copy("lastTransactionTime", "last_transaction_time"),
			copy("registrationMode", "registration_mode"),
			copy("canNotTransact", "can_not_transact"),
			copy("timeToBeginTransact", "time_to_begin_transact"),
			bool("hasUssd", "has_ussd"),
			bool("oldUssdAccount", "old_ussd_account"),
			bool("hasMobile", "has_mobile"),
			bool("ussdDisabled", "ussd_disabled"),
			copy("quickPayAccount", "quick_pay_account"),
			copy("imsinumber", "imsinumber"),
			copy("userId", "user_id"),
			copy("cifNumber", "cif_number"),
			copy("idNumber", "id_number"),
			bool("migratedPin", "migrated_pin"),
			copy("ussdLastLogin", "ussd_last_login"),
			copy("migratedDate", "migrated_date"),
			copy("loginDisabled", "login_disabled"),
			defaultZero("loginRetries", "login_retries"),
			copy("cachedAccount", "cached_account"),
			copy("dateAccountCached", "date_account_cached"),
			copy("imsi", "imsi"),
			copy("defaultLanguage", "default_language"),
			copy("reasonForProfileDeletion", "reason_for_profile_deletion"),
			copy("profileDeletionDate", "profile_deletion_date"),
			bool("isDeleted", "is_deleted"),
			bool("disable_user", "disable_user"),
			bool("display_banner", "display_banner"),
			bool("force_password_reset", "force_password_reset")
	};

	public static final String[] HEADER = ColumnRule.header(RULES);

	private UsersSchema() {
	}
}
