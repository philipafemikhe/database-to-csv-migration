package com.example.demo.migration.mapping;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.model.BankRecord;
import com.example.demo.migration.model.BillerRecord;
import com.example.demo.migration.model.ProductRecord;
import com.example.demo.migration.model.UserRecord;
import com.example.demo.migration.repo.BanksRepository;
import com.example.demo.migration.repo.BillersRepository;
import com.example.demo.migration.repo.ProductsRepository;
import com.example.demo.migration.repo.UsersRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * dbo.Transactions -> transaction, per Transactions_MappingDocument.xlsx and
 * DTypeMappingRulesforTransactions.docx.
 *
 *   dtype                <- derived from DTYPE + country + biller/product/bank lookups, see TransactionDTypeMapper
 *   customerid            <- Users.userName, looked up by the row's customer_id
 *   destination_bank_id   <- Banks.name, looked up by destinationBank_id (despite the field name,
 *                             the mapping doc calls for the bank's name here, not its id)
 *   country_code          <- country_id, mapped via IswCountry's fixed 1-4 table
 *   userid / biller_id / product_id <- copied verbatim as raw ids (unlike customerid/destination_bank_id,
 *                             which are resolved to a name)
 *   biller_gateway_code   <- Billers.gatewayCode by biller_id, only when dtype resolves to BILL; blank
 *                             otherwise
 *   transferprocessor     <- for all countries, when dtype resolves to BILL, overwritten with
 *                             Billers.gatewayCode by biller_id (same lookup as biller_gateway_code
 *                             above); the raw Transactions.transferProcessor value otherwise
 *   wallet_type / wallet_code <- per MigrationCorrection_Wallets.docx: Malawi/Botswana rows join
 *                             Products by product_id, then check the resolved Products.gatewayCode
 *                             against a fixed set of wallet product gateway codes (see
 *                             WalletProductMapper) -> Products.name / Products.gatewayCode; Zambia
 *                             rows join Banks by destinationBank_id, only when that bank has
 *                             allowInstantTransfer=1 and a WALLET institution -> Banks.name /
 *                             Banks.code. Blank everywhere else.
 *   everything else       <- copied verbatim from the matching Transactions column
 */
public final class TransactionMapper {

	private TransactionMapper() {
	}

	public static OutputRecord mapRow(ResultSet rs, UsersRepository users, BanksRepository banks,
			BillersRepository billers, ProductsRepository products, WarningCollector warnings) throws SQLException {

		long oldId = rs.getLong("id");
		String isoType = JdbcUtils.getString(rs, "DTYPE");
		BigDecimal amount = rs.getBigDecimal("amount");
		BigDecimal charge = rs.getBigDecimal("charge");
		String bankReference = JdbcUtils.getString(rs, "bankReference");
		String deviceUuid = JdbcUtils.getString(rs, "deviceUUID");
		String narration = JdbcUtils.getString(rs, "narration");
		String sourceAccount = JdbcUtils.getString(rs, "sourceAccount");
		String status = JdbcUtils.getString(rs, "status");
		String statusCode = JdbcUtils.getString(rs, "statusCode");
		String statusDescription = JdbcUtils.getString(rs, "statusDescription");
		String transactionDate = TimestampFormat.format(JdbcUtils.getTimestamp(rs, "transactionDate"));
		String destinationAccount = JdbcUtils.getString(rs, "destinationAccount");
		String transferProcessor = JdbcUtils.getString(rs, "transferProcessor");
		String customerReference = JdbcUtils.getString(rs, "customerReference");
		String finalResponseCode = JdbcUtils.getString(rs, "finalResponseCode");
		String gatewayReference = JdbcUtils.getString(rs, "gatewayReference");
		String initialResponseCode = JdbcUtils.getString(rs, "initialResponseCode");
		Long customerId = JdbcUtils.getLong(rs, "customer_id");
		Long destinationBankId = JdbcUtils.getLong(rs, "destinationBank_id");
		Long billerId = JdbcUtils.getLong(rs, "biller_id");
		Long productId = JdbcUtils.getLong(rs, "product_id");
		String phoneNumber = JdbcUtils.getString(rs, "phoneNumber");
		Long countryId = JdbcUtils.getLong(rs, "country_id");
		String receiptNo = JdbcUtils.getString(rs, "receiptNo");
		String tokens = JdbcUtils.getString(rs, "tokens");
		String destinationAccountName = JdbcUtils.getString(rs, "destinationAccountName");
		String channel = JdbcUtils.getString(rs, "channel");
		Long userId = JdbcUtils.getLong(rs, "user_id");
		String sourceAccountCurrency = JdbcUtils.getString(rs, "sourceAccountCurrency");

		IswCountry country = IswCountry.fromId(countryId);
		if (countryId != null && country == null) {
			warnings.warn(oldId, "country_id " + countryId + " not recognized (expected 1-4); country_code left blank and country-specific dtype rules will not apply");
		}

		String customerIdOut = null;
		UserRecord user = users.findById(customerId);
		if (customerId != null && user == null) {
			warnings.warn(oldId, "customer_id " + customerId + " not found in Users (country=" + country + "); customerid left blank");
		} else if (user != null) {
			customerIdOut = user.userName();
		}

		String destinationBankName = null;
		if (destinationBankId != null) {
			BankRecord bank = banks.findById(destinationBankId);
			if (bank == null && banks.isAvailable()) {
				warnings.warn(oldId, "destinationBank_id " + destinationBankId + " not found in Banks (country=" + country + "); destination_bank_id left blank");
			} else if (bank != null) {
				destinationBankName = bank.name();
			}
		}

		String dtype = TransactionDTypeMapper.map(oldId, isoType, country, billerId, productId, destinationBankId, billers, products, banks, warnings);

		String billerGatewayCode = null;
		if ("BILL".equals(dtype)) {
			BillerRecord biller = billers.findById(billerId);
			if (biller == null) {
				if (billerId != null && billers.isAvailable()) {
					warnings.warn(oldId, "biller_id " + billerId + " not found in Billers (country=" + country + "); biller_gateway_code left blank");
				}
			} else {
				billerGatewayCode = biller.gatewayCode();
				transferProcessor = billerGatewayCode;
			}
		}

		String walletType = null;
		String walletCode = null;
		if (country == IswCountry.MALAWI || country == IswCountry.BOTSWANA) {
			ProductRecord product = products.findById(productId);
			if (product != null && WalletProductMapper.isWalletProduct(product)) {
				walletType = product.name();
				walletCode = product.gatewayCode();
			}
		} else if (country == IswCountry.ZAMBIA && destinationBankId != null) {
			BankRecord destinationBank = banks.findById(destinationBankId);
			if (destinationBank != null && destinationBank.wallet() && Boolean.TRUE.equals(destinationBank.allowInstantTransfer())) {
				walletType = destinationBank.name();
				walletCode = destinationBank.code();
			}
		}

		return new OutputRecord(TransactionSchema.HEADER)
				.set("id", oldId)
				.set("dtype", dtype)
				.set("amount", amount)
				.set("charge", charge)
				.set("bankreference", bankReference)
				.set("deviceuuid", deviceUuid)
				.set("narration", narration)
				.set("sourceaccount", sourceAccount)
				.set("status", status)
				.set("statuscode", statusCode)
				.set("statusdescription", statusDescription)
				.set("transactiondate", transactionDate)
				.set("destinationaccount", destinationAccount)
				.set("transferprocessor", transferProcessor)
				.set("customerreference", customerReference)
				.set("finalresponsecode", finalResponseCode)
				.set("gatewayreference", gatewayReference)
				.set("initialresponsecode", initialResponseCode)
				.set("customerid", customerIdOut)
				.set("destination_bank_id", destinationBankName)
				.set("biller_id", billerId)
				.set("product_id", productId)
				.set("phonenumber", phoneNumber)
				.set("country_code", country == null ? null : country.code())
				.set("receiptno", receiptNo)
				.set("tokens", tokens)
				.set("destinationaccountname", destinationAccountName)
				.set("channel", channel)
				.set("userid", userId)
				.set("sourceaccountcurrency", sourceAccountCurrency)
				.set("biller_gateway_code", billerGatewayCode)
				.set("wallet_type", walletType)
				.set("wallet_code", walletCode);
	}
}
