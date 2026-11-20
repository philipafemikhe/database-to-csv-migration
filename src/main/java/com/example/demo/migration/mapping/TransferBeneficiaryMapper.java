package com.example.demo.migration.mapping;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.model.BankBranchRecord;
import com.example.demo.migration.model.BankRecord;
import com.example.demo.migration.model.UserRecord;
import com.example.demo.migration.repo.BankBranchesRepository;
import com.example.demo.migration.repo.BanksRepository;
import com.example.demo.migration.repo.UsersRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * dbo.TransferBeneficiaries -> beneficiary_details, per TransferBeneficaries_Migration_Logic.xlsx.
 *
 *   customer_id            <- Users.userName (lookup by TransferBeneficiaries.customer_id)
 *   customer_country_code  <- Users.country_id, mapped via IswCountry's fixed 1-4 table
 *   beneficiary_type       <- transactionType, mapped via BeneficiaryTypeMapper
 *   beneficiary_name       <- accountName
 *   account_number         <- accountNumber
 *   bank_name / swift_code <- Banks lookup by bank_id
 *   branch_name            <- BankBranches lookup by bankBranch_id, only for Botswana + EFT,
 *                              validated against the row's bank_id
 *   has_branches           <- true iff branch_name was resolved
 *   branch_code            <- BankBranches.gatewayCode, looked up by bankBranch_id (any
 *                              country/type - independent of the branch_name validation above)
 *   wallet_type / wallet_code <- per MigrationCorrection_Wallets.docx: Banks.name / Banks.code,
 *                              only for Zambia when the bank is a wallet institution with
 *                              allowInstantTransfer=1 (previously wallet_type incorrectly held
 *                              Banks.code with no separate wallet_code at all)
 *   bank_gateway_code      <- Banks.swiftCode (the gatewayCode column) for RTGS rows, Banks.code
 *                              for INSTANT rows; blank for LOCAL/EFT/SchoolTransaction
 *   is_active / is_deleted <- true / false (always)
 *   created_at             <- migration run timestamp (source table has no createDate)
 *   beneficiary_currency   <- accountCurrency
 */
public final class TransferBeneficiaryMapper {

	private TransferBeneficiaryMapper() {
	}

	public static OutputRecord mapRow(ResultSet rs, UsersRepository users, BanksRepository banks,
			BankBranchesRepository branches, String runTimestamp, WarningCollector warnings) throws SQLException {

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
		IswCountry country = null;
		if (user == null) {
			if (customerId != null) {
				warnings.warn(oldId, "customer_id " + customerId + " not found in Users; customer_id/customer_country_code left blank");
			}
		} else {
			customerIdOut = user.userName() == null ? "" : user.userName();
			country = IswCountry.fromId(user.countryId());
			if (user.countryId() != null && country == null) {
				warnings.warn(oldId, "country_id " + user.countryId() + " not recognized (expected 1-4); customer_country_code left blank");
			} else if (country != null) {
				customerCountryCodeOut = country.code();
			}
		}

		boolean countryIsZambia = country == IswCountry.ZAMBIA;
		boolean countryIsBotswana = country == IswCountry.BOTSWANA;

		BankRecord bank = banks.findById(bankId);
		if (bankId != null && bank == null && banks.isAvailable()) {
			warnings.warn(oldId, "bank_id " + bankId + " not found in Banks; bank_name/swift_code left blank");
		}

		String beneficiaryType = BeneficiaryTypeMapper.map(transactionType, countryIsZambia, bank);
		if (beneficiaryType == null && transactionType != null) {
			warnings.warn(oldId, "unrecognized transactionType '" + transactionType + "'; beneficiary_type left blank");
		}

		boolean zambiaInstantWallet = countryIsZambia && bank != null && bank.wallet() && Boolean.TRUE.equals(bank.allowInstantTransfer());
		String walletType = zambiaInstantWallet ? bank.name() : null;
		String walletCode = zambiaInstantWallet ? bank.code() : null;

		String bankGatewayCode = null;
		if (bank != null && transactionType != null) {
			String normalizedType = transactionType.trim().toUpperCase(Locale.ROOT);
			if ("RTGS".equals(normalizedType)) {
				bankGatewayCode = bank.swiftCode();
			} else if ("INSTANT".equals(normalizedType)) {
				bankGatewayCode = bank.code();
			}
		}

		BankBranchRecord branch = bankBranchId == null ? null : branches.findById(bankBranchId);
		boolean botswanaEft = countryIsBotswana && "EFT".equalsIgnoreCase(transactionType);
		// A NULL bankBranch_id is not logged: the row still migrates, just without branch fields.
		if (bankBranchId != null && branch == null) {
			warnings.warn(oldId, "bankBranch_id " + bankBranchId + " not found in BankBranches; branch_code left blank"
					+ (botswanaEft ? " and branch_name left blank" : ""));
		}

		String branchName = null;
		boolean hasBranches = false;
		if (botswanaEft && branch != null) {
			if (bankId != null && branch.bankId() != null && !branch.bankId().equals(bankId)) {
				warnings.warn(oldId, "bankBranch_id " + bankBranchId + " belongs to bank " + branch.bankId()
						+ ", not row's bank_id " + bankId + "; branch_name left blank");
			} else {
				branchName = branch.name();
				hasBranches = true;
			}
		}

		String branchCode = branch == null ? null : branch.gatewayCode();

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
				.set("wallet_code", walletCode)
				.set("beneficiary_currency", accountCurrency)
				.set("has_branches", hasBranches)
				.set("branch_code", branchCode)
				.set("bank_gateway_code", bankGatewayCode);
	}
}
