package com.example.demo.migration.mapping;

/** Matches the 51-column layout of new_models/beneficiary_details.csv. */
public final class BeneficiaryDetailsSchema {

	public static final String[] HEADER = {
			"beneficiary_id", "customer_id", "customer_country_code", "beneficiary_type", "beneficiary_country",
			"beneficiary_name", "beneficiary_add_line_1", "beneficiary_add_line_2", "beneficiary_city",
			"account_number", "bank_name", "branch_name", "bank_address", "contact_mobile", "contact_email",
			"nickname", "swift_code", "iban_number", "is_active", "is_deleted", "is_favourite", "cooling_period",
			"created_at", "last_modified_at", "created_by", "last_modified_by", "is_approved", "is_rejected",
			"admin_remarks", "wallet_type", "agent_id", "beneficiary_currency", "bank_gateway_code", "has_branches",
			"branch_code", "biller_id", "biller_category", "biller_category_id", "subcategory", "subcategory_id",
			"biller_name", "biller_input_value", "biller_input_label", "biller_icon", "biller_icon_url",
			"biller_input_json", "biller_input_value_json", "api_key", "wallet_code", "input_label", "input_label_ch"
	};

	private BeneficiaryDetailsSchema() {
	}
}
