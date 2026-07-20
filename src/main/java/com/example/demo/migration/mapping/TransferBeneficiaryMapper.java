package com.example.demo.migration.mapping;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.model.BankBranchRecord;
import com.example.demo.migration.model.BankRecord;
import com.example.demo.migration.model.CountryPartnerRecord;
import com.example.demo.migration.model.UserRecord;
import com.example.demo.migration.repo.BankBranchesRepository;
import com.example.demo.migration.repo.BanksRepository;
import com.example.demo.migration.repo.CountryPartnerRepository;
import com.example.demo.migration.repo.UsersRepository;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * dbo.TransferBeneficiaries -> beneficiary_details
 *
 *   customer_id            <- Users.userName (lookup by TransferBeneficiaries.customer_id)
 *   customer_country_code  <- CountryPartner lookup by Users.country_id (a short code like "BW",
 *                              not the raw numeric id)
 *   beneficiary_type       <- transactionType, mapped via BeneficiaryTypeMapper
 *   beneficiary_name       <- accountName
 *   account_number         <- accountNumber
 *   bank_name / swift_code <- Banks lookup by bank_id
 *   branch_name            <- BankBranches lookup by bankBranch_id, only for Botswana + EFT,
 *                              validated against the row's bank_id
 *   has_branches           <- true iff branch_name was resolved
 *   branch_code            <- bankBranch_id, copied verbatim regardless of country/type
 *   wallet_type            <- Banks.name, only for Zambia when the bank is a wallet institution
 *   is_active / is_deleted <- true / false (always)
 *   created_at             <- migration run timestamp (source table has no createDate)
 *   beneficiary_currency   <- accountCurrency
 */
public final class TransferBeneficiaryMapper {

	private TransferBeneficiaryMapper() {
	}

	public static OutputRecord mapRow(ResultSet rs, UsersRepository users, BanksRepository banks,
			BankBranchesRepository branches, CountryPartnerRepository countries, String runTimestamp,
			WarningCollector warnings) throws SQLException {

		long oldId = rs.getLong("id");
		String accountName = JdbcUtils.getString(rs, "accountName");
		String accountNumber = JdbcUtils.getString(rs, "accountNumber");
		Long bankId = JdbcUtils.getLong(rs, "bank_id");
		Long customerId = JdbcUtils.getLong(rs, "customer_id");
		String accountCurrency = JdbcUtils.getString(rs, "accountCurrency");
		String transactionType = JdbcUtils.getString(rs, "transactionType");
		Long bankBranchId = JdbcUtils.getLong(rs, "bankBranch_id");

		UserRecord user = users.findById(customerId);
		String customerIdOut = "";
		String customerCountryCodeOut = "";
		String countryLabel = null;
		if (user == null) {
			if (customerId != null) {
				warnings.warn(oldId, "customer_id " + customerId + " not found in Users; customer_id/customer_country_code left blank");
			}
		} else {
			customerIdOut = user.userName() == null ? "" : user.userName();
			if (user.countryId() != null) {
				CountryPartnerRecord country = countries.findById(user.countryId());
				if (country == null) {
					warnings.warn(oldId, "country_id " + user.countryId() + " not found in CountryPartner; customer_country_code left blank");
				} else {
					countryLabel = country.label();
					customerCountryCodeOut = countryLabel == null ? "" : countryLabel;
				}
			}
		}

		boolean countryIsZambia = CountryMatcher.isZambia(countryLabel);
		boolean countryIsBotswana = CountryMatcher.isBotswana(countryLabel);

		BankRecord bank = banks.findById(bankId);
		if (bankId != null && bank == null && banks.isAvailable()) {
			warnings.warn(oldId, "bank_id " + bankId + " not found in Banks; bank_name/swift_code left blank");
		}
		boolean bankIsWallet = bank != null && bank.wallet();

		String beneficiaryType = BeneficiaryTypeMapper.map(transactionType, countryIsZambia, bankIsWallet);
		if (beneficiaryType == null && transactionType != null) {
			warnings.warn(oldId, "unrecognized transactionType '" + transactionType + "'; beneficiary_type left blank");
		}

		String walletType = (countryIsZambia && bankIsWallet && bank != null) ? bank.name() : null;

		String branchName = null;
		boolean hasBranches = false;
		if (countryIsBotswana && "EFT".equalsIgnoreCase(transactionType)) {
			if (bankBranchId == null) {
				warnings.warn(oldId, "Botswana EFT row has no bankBranch_id; branch_name left blank");
			} else {
				BankBranchRecord branch = branches.findById(bankBranchId);
				if (branch == null) {
					warnings.warn(oldId, "bankBranch_id " + bankBranchId + " not found in BankBranches; branch_name left blank");
				} else if (bankId != null && branch.bankId() != null && !branch.bankId().equals(bankId)) {
					warnings.warn(oldId, "bankBranch_id " + bankBranchId + " belongs to bank " + branch.bankId()
							+ ", not row's bank_id " + bankId + "; branch_name left blank");
				} else {
					branchName = branch.name();
					hasBranches = true;
				}
			}
		}

		String branchCode = bankBranchId == null ? null : String.valueOf(bankBranchId);

		return new OutputRecord(BeneficiaryDetailsSchema.HEADER)
				.set("beneficiary_id", oldId)
				.set("customer_id", customerIdOut)
				.set("customer_country_code", customerCountryCodeOut)
				.set("beneficiary_type", beneficiaryType)
				.set("beneficiary_name", accountName)
				.set("account_number", accountNumber)
				.set("bank_name", bank == null ? null : bank.name())
				.set("branch_name", branchName)
				.set("swift_code", bank == null ? null : bank.swiftCode())
				.set("is_active", true)
				.set("is_deleted", false)
				.set("created_at", runTimestamp)
				.set("wallet_type", walletType)
				.set("beneficiary_currency", accountCurrency)
				.set("has_branches", hasBranches)
				.set("branch_code", branchCode);
	}
}
