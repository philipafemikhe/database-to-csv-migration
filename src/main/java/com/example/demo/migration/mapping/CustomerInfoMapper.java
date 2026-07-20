package com.example.demo.migration.mapping;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.model.BankBranchRecord;
import com.example.demo.migration.model.CountryPartnerRecord;
import com.example.demo.migration.model.DeviceRecord;
import com.example.demo.migration.model.UserRecord;
import com.example.demo.migration.repo.BankBranchesRepository;
import com.example.demo.migration.repo.CountryPartnerRepository;
import com.example.demo.migration.repo.DevicesRepository;

import java.util.UUID;

/**
 * dbo.Users -> customer_info
 *
 *   id                        <- Users.id
 *   unique_id                 <- a freshly generated UUID + account_id (no old-system source;
 *                                 this is migration-time provenance, not preserved data)
 *   customer_id                <- userName
 *   mobile_number / ussd_mobile_number <- mobileNumber
 *   account_id / primary_account_id / default_account_id <- primaryAccountNumber
 *   branch_id                 <- BankBranches lookup by the account number's leading digits
 *                                 against BankBranches.code (best-effort; left blank + warned
 *                                 when nothing matches)
 *   country_code / default_country_code <- CountryPartner lookup by country_id
 *   device_id                 <- Devices.uuid (most recent/active, by customer_id), falling
 *                                 back to mobileNumber when the customer has no device record
 *   account_id_ussd            <- primaryAccountNumber, only when hasUssd is true
 *   default_language           <- defaultLanguage, normalized to a short code (see LanguageCode)
 *   is_migrated_user            <- true (always: every row here is a migrated user)
 *   migrated_on / updated_date <- migration run timestamp
 *   created_by / updated_by    <- "MIGRATION" (constant; old Users has no per-record author)
 *   created_date / registered_on / ussd_registered_on <- dateRegistered
 *   is_approval_required / reset_imsi / is_aop_user <- false (constant; matches observed defaults)
 *
 * Everything else in the 60-column schema (free_text_*, biometric/face-id fields, onboarding
 * fields, is_admin_approved, is_ussd_active/ussd_active_on, profile_photo, default_theme) has
 * no old-system source and is left blank.
 */
public final class CustomerInfoMapper {

	private CustomerInfoMapper() {
	}

	public static OutputRecord map(UserRecord user, BankBranchesRepository branches, DevicesRepository devices,
			CountryPartnerRepository countries, String runTimestamp, WarningCollector warnings) {

		String accountId = user.primaryAccountNumber();

		String branchId = null;
		if (accountId != null && accountId.length() >= 3) {
			BankBranchRecord branch = branches.findByCodePrefix(accountId.substring(0, 3));
			if (branch != null) {
				branchId = String.valueOf(branch.id());
			} else {
				warnings.warn(user.id(), "no BankBranches match for code prefix derived from account number '" + accountId + "'; branch_id left blank");
			}
		} else if (accountId != null) {
			warnings.warn(user.id(), "account number '" + accountId + "' too short to derive a branch code; branch_id left blank");
		}

		String countryCode = null;
		if (user.countryId() != null) {
			CountryPartnerRecord country = countries.findById(user.countryId());
			if (country == null) {
				warnings.warn(user.id(), "country_id " + user.countryId() + " not found in CountryPartner; country_code left blank");
			} else {
				countryCode = country.label();
			}
		}

		DeviceRecord device = devices.findByCustomerId(user.id());
		String deviceId = (device != null && device.uuid() != null) ? device.uuid() : user.mobileNumber();

		String accountIdUssd = Boolean.TRUE.equals(user.hasUssd()) ? accountId : null;

		String registeredOn = TimestampFormat.format(user.dateRegistered());
		String uniqueId = UUID.randomUUID() + (accountId == null ? "" : accountId);

		return new OutputRecord(CustomerInfoSchema.HEADER)
				.set("id", user.id())
				.set("unique_id", uniqueId)
				.set("customer_id", user.userName())
				.set("mobile_number", user.mobileNumber())
				.set("account_id", accountId)
				.set("branch_id", branchId)
				.set("country_code", countryCode)
				.set("device_id", deviceId)
				.set("imsi_number", user.imsiNumber())
				.set("primary_account_id", accountId)
				.set("default_account_id", accountId)
				.set("default_country_code", countryCode)
				.set("account_id_ussd", accountIdUssd)
				.set("last_login", TimestampFormat.format(user.lastLogin()))
				.set("ussd_last_login", TimestampFormat.format(user.ussdLastLogin()))
				.set("default_language", LanguageCode.normalize(user.defaultLanguage()))
				.set("time_to_begin_transact", TimestampFormat.format(user.timeToBeginTransact()))
				.set("registered_on", registeredOn)
				.set("ussd_registered_on", registeredOn)
				.set("registration_mode", user.registrationMode())
				.set("has_mobile", user.hasMobile())
				.set("has_ussd", user.hasUssd())
				.set("is_disabled", user.loginDisabled())
				.set("is_profile_deleted", user.isDeleted())
				.set("is_migrated_user", true)
				.set("is_migrated_user_via_pwd", user.migratedPin())
				.set("migrated_on", runTimestamp)
				.set("is_approval_required", false)
				.set("created_by", "MIGRATION")
				.set("created_date", registeredOn)
				.set("updated_by", "MIGRATION")
				.set("updated_date", runTimestamp)
				.set("is_ussd_disabled", user.ussdDisabled())
				.set("reason_for_profile_deletion", user.reasonForProfileDeletion())
				.set("profile_deletion_date", TimestampFormat.format(user.profileDeletionDate()))
				.set("ussd_mobile_number", user.mobileNumber())
				.set("reset_imsi", false)
				.set("is_aop_user", false);
	}
}
