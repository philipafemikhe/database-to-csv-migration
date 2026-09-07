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
import java.util.Locale;

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
 *   admin_remarks                     <- All rules key off Billers.name (case-insensitive contains),
 *                                         not biller_id - biller_id isn't stable across dev/production
 *                                         even though a biller's name is, same approach
 *                                         TransactionDTypeMapper already uses for "Simpo Cash"/"Orange-B2C"
 *                                         gateway-code matching. Each rule is still gated to a specific
 *                                         country: Malawi + name contains "TNM" and "Bundle" ->
 *                                         additionalParams.productGatewayCode; Zimbabwe + name contains
 *                                         "City of Bulawayo" -> additionalParams.utilityType; Zimbabwe +
 *                                         name contains "Nyaradzo" -> static "1"; Zimbabwe + name contains
 *                                         "Zimra" -> the resolved Products row's name decides: contains
 *                                         "Custom" -> "Custom", else -> static "Domestic" (also matched by
 *                                         name, not product_id - a real Products extract showed the
 *                                         "Domestic"/"Custom" products actually belong to Zimra, not City
 *                                         of Bulawayo, which is where an earlier version of this rule had
 *                                         nested them by product_id); Malawi + name contains "Airtel" and
 *                                         "Bundle" -> additionalParams.productGatewayCode. Blank for
 *                                         everything else.
 *   biller_id                         <- biller_id
 *   biller_name                       <- Billers lookup by biller_id
 *   biller_input_value                <- customerReferece (sic - the source column is misspelled)
 *   wallet_type / wallet_code         <- per MigrationCorrection_Wallets.docx: Products lookup by
 *                                         product_id, for Malawi/Botswana rows whose resolved
 *                                         Products.name is one of a fixed set of wallet product
 *                                         names (see WalletProductMapper - matched by name, not
 *                                         product_id, for the same dev/production-stability reason
 *                                         as the admin_remarks rules above) - independent of the
 *                                         biller_id/product_id pair that decides beneficiary_type
 *                                         below, so a row can be BILL and still carry a resolved
 *                                         wallet_type/wallet_code, or vice versa
 *   bank_gateway_code                 <- Billers.gatewayCode by biller_id, only when beneficiary_type
 *                                         resolves to BILL; blank otherwise (IWL rows draw their gateway
 *                                         code from Products via wallet_code instead)
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
		if (country == IswCountry.MALAWI || country == IswCountry.BOTSWANA) {
			ProductRecord product = products.findById(productId);
			if (product != null && WalletProductMapper.isWalletProduct(product)) {
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

		String adminRemarks = adminRemarks(country, productId, json, biller, products);

		String bankGatewayCode = "BILL".equals(beneficiaryType) && biller != null ? biller.gatewayCode() : null;

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
				.set("admin_remarks", adminRemarks)
				.set("biller_id", billerId)
				.set("biller_name", biller == null ? null : biller.name())
				.set("biller_input_value", customerReference)
				.set("wallet_type", walletType)
				.set("wallet_code", walletCode)
				.set("bank_gateway_code", bankGatewayCode);
	}

	private static String adminRemarks(IswCountry country, Long productId, JsonNode json, BillerRecord biller, ProductsRepository products) {
		String billerName = biller == null ? null : biller.name();

		if (country == IswCountry.MALAWI && containsIgnoreCase(billerName, "TNM") && containsIgnoreCase(billerName, "Bundle")) {
			return AdditionalParamsParser.firstText(json, "productGatewayCode");
		}
		if (country == IswCountry.ZIMBABWE && containsIgnoreCase(billerName, "City of Bulawayo")) {
			return AdditionalParamsParser.firstText(json, "utilityType");
		}
		if (country == IswCountry.ZIMBABWE) {
			if (containsIgnoreCase(billerName, "Nyaradzo")) {
				return "1";
			}
			if (containsIgnoreCase(billerName, "Zimra")) {
				ProductRecord product = products.findById(productId);
				String productName = product == null ? null : product.name();
				if (containsIgnoreCase(productName, "Custom")) {
					return "Custom";
				}
				return "Domestic";
			}
		}
		if (country == IswCountry.MALAWI && containsIgnoreCase(billerName, "Airtel") && containsIgnoreCase(billerName, "Bundle")) {
			return AdditionalParamsParser.firstText(json, "productGatewayCode");
		}
		return null;
	}

	private static boolean containsIgnoreCase(String haystack, String needle) {
		return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
	}
}
