package com.example.demo.migration.mapping;

/** Matches the 57-column layout of new_models/customer_info.csv. */
public final class CustomerInfoSchema {

	public static final String[] HEADER = {
			"id", "unique_id", "customer_id", "mobile_number", "account_id", "branch_id", "country_code",
			"device_id", "imsi_number", "primary_account_id", "default_account_id", "default_country_code",
			"account_id_ussd", "last_login", "ussd_last_login", "default_language", "time_to_begin_transact",
			"registered_on", "ussd_registered_on", "registration_mode", "free_text_1", "free_text_2", "free_text_3",
			"free_text_4", "free_text_5", "free_text_6", "free_text_7", "has_mobile", "has_ussd", "is_disabled",
			"is_profile_deleted", "is_migrated_user", "is_migrated_user_via_pwd", "migrated_on", "is_admin_approved",
			"is_approval_required", "is_biometric_enabled", "is_face_id_enabled", "biometric_enabled_on",
			"face_id_enabled_on", "created_by", "created_date", "updated_by", "updated_date", "is_mobile_disabled",
			"is_ussd_disabled", "is_ussd_active", "ussd_active_on", "reason_for_profile_deletion",
			"profile_deletion_date", "profile_photo", "ussd_mobile_number", "reset_imsi", "is_ussd_txn_enabled",
			"is_mob_txn_enabled", "default_theme", "onboard_status", "is_aop_user", "onboard_started_on",
			"onboarded_on"
	};

	private CustomerInfoSchema() {
	}
}
