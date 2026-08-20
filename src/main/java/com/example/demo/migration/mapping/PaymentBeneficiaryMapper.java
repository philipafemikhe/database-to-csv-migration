package com.example.demo.migration.mapping;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.json.AdditionalParamsParser;
import com.example.demo.migration.model.BillerRecord;
import com.example.demo.migration.model.ProductRecord;
import com.example.demo.migration.model.UserRecord;
import com.example.demo.migration.repo.BillersRepository;
import com.example.demo.migration.repo.ProductsRepository;
import com.example.demo.migration.repo.UsersRepository;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * dbo.PaymentBeneficiaries -> beneficiary_details, per
 * PaymentBeneficiaries_Inerswitch_MigrationLogic.xlsx.docx.
 *
 * These are biller/utility payment beneficiaries, not bank transfers: there's no bank_id or
 * bankBranch_id on the source table, so bank_name/branch_name/swift_code/branch_code are always
 * left blank here (unlike the TransferBeneficiaries mapping).
 *
 *   beneficiary_id                    <- id + idOffset (avoids colliding with TransferBeneficiaries
 *                                         ids in the same output file)
 *   customer_id                       <- additionalParams.userName, falling back to Users.userName
 *                                         by customer_id if the JSON doesn't carry it
 *   customer_country_code /
 *     beneficiary_country             <- Users.country_id (by customer_id), mapped via IswCountry's
 *                                         fixed 1-4 table - same source used for TransferBeneficiaries'
 *                                         customer_country_code, kept consistent since both mappers
 *                                         feed the same output file
 *   beneficiary_type                  <- country + biller_id/product_id, see PaymentBeneficiaryTypeMapper
 *   beneficiary_name                  <- destinationAccountName, else additionalParams (toAccountName /
 *                                         customerName / beneficiaryName)
 *   nickname                          <- additionalParams.customerName
 *   account_number                    <- destinationAccountNumber, else additionalParams.accountNumber
 *   beneficiary_currency              <- additionalParams.currencyCode
 *   is_favourite                      <- quickPay
 *   created_at                        <- createDate (this table, unlike TransferBeneficiaries, has one)
 *   last_modified_at                  <- migration run timestamp
 *   created_by / last_modified_by     <- SYSTEM
 *   biller_id                         <- biller_id
 *   biller_name                       <- Billers lookup by biller_id
 *   biller_input_value                <- customerReferece (sic - the source column is misspelled)
 *   wallet_type / wallet_code         <- Products lookup by product_id, only for the Malawi/Botswana
 *                                         biller_id/product_id combinations that resolve to IWL
 *   is_active / is_deleted            <- true / false (always)
 */
public final class PaymentBeneficiaryMapper {

	private PaymentBeneficiaryMapper() {
	}

	public static OutputRecord mapRow(ResultSet rs, UsersRepository users, BillersRepository billers,
			ProductsRepository products, long idOffset, String runTimestamp, WarningCollector warnings) throws SQLException {

		long oldId = rs.getLong("id");
		String additionalParams = JdbcUtils.getString(rs, "additionalParams");
		Long customerId = JdbcUtils.getLong(rs, "customer_id");
		Long billerId = JdbcUtils.getLong(rs, "biller_id");
		Long productId = JdbcUtils.getLong(rs, "product_id");
		Boolean quickPay = JdbcUtils.getBoolean(rs, "quickPay");
		Timestamp createDate = rs.getTimestamp("createDate");
		String destinationAccountNumber = JdbcUtils.getString(rs, "destinationAccountNumber");
		String destinationAccountName = JdbcUtils.getString(rs, "destinationAccountName");
		String customerReference = JdbcUtils.getString(rs, "customerReferece");

		JsonNode json = AdditionalParamsParser.parse(additionalParams);
		if (additionalParams != null && json == null) {
			warnings.warn(oldId, "additionalParams is not valid JSON; JSON-derived fields left blank");
		}

		UserRecord user = users.findById(customerId);
		String customerCountryCodeOut = "";
		IswCountry country = null;
		if (user != null && user.countryId() != null) {
			country = IswCountry.fromId(user.countryId());
			if (country == null) {
				warnings.warn(oldId, "country_id " + user.countryId() + " not recognized (expected 1-4); "
						+ "customer_country_code/beneficiary_country/beneficiary_type left blank");
			} else {
				customerCountryCodeOut = country.code();
			}
		}

		String customerIdOut = AdditionalParamsParser.firstText(json, "userName");
		if (customerIdOut == null) {
			customerIdOut = user != null && user.userName() != null ? user.userName() : "";
			if (customerIdOut.isEmpty()) {
				warnings.warn(oldId, "no userName in additionalParams and customer_id " + customerId + " not found in Users; customer_id left blank");
			}
		}

		String beneficiaryType = PaymentBeneficiaryTypeMapper.map(country, billerId, productId);

		BillerRecord biller = billers.findById(billerId);
		if (billerId != null && biller == null && billers.isAvailable()) {
			warnings.warn(oldId, "biller_id " + billerId + " not found in Billers; biller_name left blank");
		}

		String walletType = null;
		String walletCode = null;
		if (PaymentBeneficiaryTypeMapper.isWallet(country, billerId, productId)) {
			ProductRecord product = products.findById(productId);
			if (product == null && products.isAvailable()) {
				warnings.warn(oldId, "product_id " + productId + " not found in Products; wallet_type/wallet_code left blank");
			} else if (product != null) {
				walletType = product.name();
				walletCode = product.gatewayCode();
			}
		}

		String beneficiaryName = destinationAccountName != null ? destinationAccountName
				: AdditionalParamsParser.firstText(json, "toAccountName", "customerName", "beneficiaryName");

		String nickname = AdditionalParamsParser.firstText(json, "customerName");

		String accountNumber = destinationAccountNumber != null ? destinationAccountNumber
				: AdditionalParamsParser.firstText(json, "accountNumber");

		String currency = AdditionalParamsParser.firstText(json, "currencyCode");

		return new OutputRecord(BeneficiaryDetailsSchema.HEADER)
				.set("beneficiary_id", oldId + idOffset)
				.set("customer_id", customerIdOut)
				.set("customer_country_code", customerCountryCodeOut)
				.set("beneficiary_type", beneficiaryType)
				.set("beneficiary_country", customerCountryCodeOut)
				.set("beneficiary_name", beneficiaryName)
				.set("nickname", nickname)
				.set("account_number", accountNumber)
				.set("beneficiary_currency", currency)
				.set("is_active", true)
				.set("is_deleted", false)
				.set("is_favourite", Boolean.TRUE.equals(quickPay))
				.set("created_at", createDate == null ? null : TimestampFormat.format(createDate.toLocalDateTime()))
				.set("last_modified_at", runTimestamp)
				.set("created_by", "SYSTEM")
				.set("last_modified_by", "SYSTEM")
				.set("biller_id", billerId)
				.set("biller_name", biller == null ? null : biller.name())
				.set("biller_input_value", customerReference)
				.set("wallet_type", walletType)
				.set("wallet_code", walletCode);
	}
}
