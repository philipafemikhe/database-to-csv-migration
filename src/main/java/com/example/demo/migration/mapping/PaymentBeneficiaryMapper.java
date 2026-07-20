package com.example.demo.migration.mapping;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.json.AdditionalParamsParser;
import com.example.demo.migration.model.CountryPartnerRecord;
import com.example.demo.migration.model.UserRecord;
import com.example.demo.migration.repo.CountryPartnerRepository;
import com.example.demo.migration.repo.UsersRepository;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * dbo.PaymentBeneficiaries -> beneficiary_details
 *
 * These are biller/utility payment beneficiaries, not bank transfers: there's no bank_id or
 * bankBranch_id on the source table, so bank_name/branch_name/swift_code/wallet_type/branch_code
 * are always left blank here (unlike the TransferBeneficiaries mapping). beneficiary_type is also
 * left blank: no field on this source indicates LOCAL/INSTANT/RTGS/EFT.
 *
 *   beneficiary_id          <- id + idOffset (avoids colliding with TransferBeneficiaries ids
 *                               in the same output file)
 *   customer_id             <- additionalParams.userName, falling back to Users.userName by
 *                               customer_id if the JSON doesn't carry it
 *   customer_country_code   <- CountryPartner lookup by Users.country_id (by customer_id); a
 *                               short code like "BW", not the raw numeric id
 *   beneficiary_name        <- destinationAccountName, else additionalParams (toAccountName /
 *                               customerName / beneficiaryName)
 *   account_number          <- destinationAccountNumber, else additionalParams.accountNumber
 *   beneficiary_currency    <- additionalParams.currencyCode
 *   is_favourite            <- quickPay
 *   created_at               <- createDate (this table, unlike TransferBeneficiaries, has one)
 *   biller_id               <- biller_id
 *   is_active / is_deleted  <- true / false (always)
 */
public final class PaymentBeneficiaryMapper {

	private PaymentBeneficiaryMapper() {
	}

	public static OutputRecord mapRow(ResultSet rs, UsersRepository users, CountryPartnerRepository countries,
			long idOffset, WarningCollector warnings) throws SQLException {

		long oldId = rs.getLong("id");
		String additionalParams = JdbcUtils.getString(rs, "additionalParams");
		Long customerId = JdbcUtils.getLong(rs, "customer_id");
		Long billerId = JdbcUtils.getLong(rs, "biller_id");
		Boolean quickPay = JdbcUtils.getBoolean(rs, "quickPay");
		Timestamp createDate = rs.getTimestamp("createDate");
		String destinationAccountNumber = JdbcUtils.getString(rs, "destinationAccountNumber");
		String destinationAccountName = JdbcUtils.getString(rs, "destinationAccountName");

		JsonNode json = AdditionalParamsParser.parse(additionalParams);
		if (additionalParams != null && json == null) {
			warnings.warn(oldId, "additionalParams is not valid JSON; JSON-derived fields left blank");
		}

		UserRecord user = users.findById(customerId);
		String customerCountryCodeOut = "";
		if (user != null && user.countryId() != null) {
			CountryPartnerRecord country = countries.findById(user.countryId());
			if (country == null) {
				warnings.warn(oldId, "country_id " + user.countryId() + " not found in CountryPartner; customer_country_code left blank");
			} else if (country.label() != null) {
				customerCountryCodeOut = country.label();
			}
		}

		String customerIdOut = AdditionalParamsParser.firstText(json, "userName");
		if (customerIdOut == null) {
			customerIdOut = user != null && user.userName() != null ? user.userName() : "";
			if (customerIdOut.isEmpty()) {
				warnings.warn(oldId, "no userName in additionalParams and customer_id " + customerId + " not found in Users; customer_id left blank");
			}
		}

		String beneficiaryName = destinationAccountName != null ? destinationAccountName
				: AdditionalParamsParser.firstText(json, "toAccountName", "customerName", "beneficiaryName");

		String accountNumber = destinationAccountNumber != null ? destinationAccountNumber
				: AdditionalParamsParser.firstText(json, "accountNumber");

		String currency = AdditionalParamsParser.firstText(json, "currencyCode");

		return new OutputRecord(BeneficiaryDetailsSchema.HEADER)
				.set("beneficiary_id", oldId + idOffset)
				.set("customer_id", customerIdOut)
				.set("customer_country_code", customerCountryCodeOut)
				.set("beneficiary_name", beneficiaryName)
				.set("account_number", accountNumber)
				.set("beneficiary_currency", currency)
				.set("is_active", true)
				.set("is_deleted", false)
				.set("is_favourite", Boolean.TRUE.equals(quickPay))
				.set("created_at", createDate == null ? null : TimestampFormat.format(createDate.toLocalDateTime()))
				.set("biller_id", billerId);
	}
}
