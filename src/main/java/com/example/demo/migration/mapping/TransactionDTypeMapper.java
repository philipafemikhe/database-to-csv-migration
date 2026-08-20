package com.example.demo.migration.mapping;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.model.BankRecord;
import com.example.demo.migration.model.BillerRecord;
import com.example.demo.migration.model.ProductRecord;
import com.example.demo.migration.repo.BanksRepository;
import com.example.demo.migration.repo.BillersRepository;
import com.example.demo.migration.repo.ProductsRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Transactions.DTYPE (the old system's ISW transaction type) + country + biller/product/bank
 * lookups -> the new dtype, per DTypeMappingRulesforTransactions.docx. Four ISW transaction types
 * (LocalTransfer, BillPayment, AirtimePurchase, InterBankTransfer), each with its own
 * country-specific rule (Malawi/Botswana/Zimbabwe/Zambia):
 *
 *   LocalTransfer      -> WIB, unconditionally.
 *   BillPayment        -> Malawi/Botswana: Billers.gatewayCode lookup by biller_id decides
 *                          IWL vs BILL; Zimbabwe/Zambia: always BILL.
 *   AirtimePurchase    -> Malawi: Products.gatewayCode lookup by product_id decides BILL vs IWL
 *                          (not Billers - Airtime keys off the product, unlike BillPayment which
 *                          keys off the biller); everywhere else: always BILL.
 *   InterBankTransfer  -> Malawi: Banks.allowInstantTransfer by destinationBank_id (1->OBI,
 *                          0->OBR); Zimbabwe: destinationBank_id NULL->OBI(ZIPIT), else
 *                          OBR(RTGS); Botswana: always OBE; Zambia: wallet bank->IWL, else
 *                          allowInstantTransfer flag (0->OBR, 1->OBI).
 */
public final class TransactionDTypeMapper {

	private static final Set<String> MALAWI_BILLPAYMENT_WALLET_GATEWAY_CODES = Collections.unmodifiableSet(
			new HashSet<>(Arrays.asList("simpo cash", "simpo agent deposits")));
	private static final String BOTSWANA_BILLPAYMENT_WALLET_GATEWAY_CODE = "orange-b2c";

	private TransactionDTypeMapper() {
	}

	public static String map(long oldId, String isoTransactionType, IswCountry country, Long billerId, Long productId,
			Long destinationBankId, BillersRepository billers, ProductsRepository products, BanksRepository banks,
			WarningCollector warnings) {

		if (isoTransactionType == null) {
			return null;
		}
		switch (isoTransactionType.trim().toUpperCase(Locale.ROOT)) {
			case "LOCALTRANSFER":
				return "WIB";
			case "BILLPAYMENT":
				return billPayment(oldId, country, billerId, billers, warnings);
			case "AIRTIMEPURCHASE":
				return airtimePurchase(oldId, country, productId, products, warnings);
			case "INTERBANKTRANSFER":
				return interBankTransfer(oldId, country, destinationBankId, banks, warnings);
			default:
				warnings.warn(oldId, "unrecognized transaction DTYPE '" + isoTransactionType + "'; dtype left blank");
				return null;
		}
	}

	private static String billPayment(long oldId, IswCountry country, Long billerId, BillersRepository billers, WarningCollector warnings) {
		if (country == null) {
			return unresolvedCountry(oldId, "BillPayment", warnings);
		}
		if (country == IswCountry.ZIMBABWE || country == IswCountry.ZAMBIA) {
			return "BILL";
		}
		String gatewayCode = billerGatewayCodeOf(billerId, billers);
		if (country == IswCountry.MALAWI) {
			return gatewayCode != null && MALAWI_BILLPAYMENT_WALLET_GATEWAY_CODES.contains(gatewayCode.toLowerCase(Locale.ROOT)) ? "IWL" : "BILL";
		}
		// Botswana
		return BOTSWANA_BILLPAYMENT_WALLET_GATEWAY_CODE.equalsIgnoreCase(gatewayCode) ? "IWL" : "BILL";
	}

	private static String airtimePurchase(long oldId, IswCountry country, Long productId, ProductsRepository products, WarningCollector warnings) {
		if (country == null) {
			return unresolvedCountry(oldId, "AirtimePurchase", warnings);
		}
		if (country != IswCountry.MALAWI) {
			return "BILL";
		}
		String gatewayCode = productGatewayCodeOf(productId, products);
		if (gatewayCode == null) {
			warnings.warn(oldId, "Malawi AirtimePurchase has no resolvable Products.gatewayCode for product_id " + productId + "; dtype left blank");
			return null;
		}
		switch (gatewayCode.toUpperCase(Locale.ROOT)) {
			case "TNMAIR":
			case "ATLAIR":
				return "BILL";
			case "AIRB2W":
			case "TNMB2W":
				return "IWL";
			default:
				warnings.warn(oldId, "Malawi AirtimePurchase product gateway code '" + gatewayCode + "' not recognized; dtype left blank");
				return null;
		}
	}

	private static String interBankTransfer(long oldId, IswCountry country, Long destinationBankId, BanksRepository banks, WarningCollector warnings) {
		if (country == null) {
			return unresolvedCountry(oldId, "InterBankTransfer", warnings);
		}
		if (country == IswCountry.BOTSWANA) {
			return "OBE";
		}
		if (country == IswCountry.ZIMBABWE) {
			return destinationBankId == null ? "OBI" : "OBR";
		}

		BankRecord bank = destinationBankId == null ? null : banks.findById(destinationBankId);
		if (bank == null) {
			warnings.warn(oldId, "InterBankTransfer destinationBank_id " + destinationBankId + " not found in Banks; dtype left blank");
			return null;
		}

		if (country == IswCountry.MALAWI) {
			if (bank.allowInstantTransfer() == null) {
				warnings.warn(oldId, "InterBankTransfer destinationBank_id " + destinationBankId + " has no resolvable allowInstantTransfer flag; dtype left blank");
				return null;
			}
			return bank.allowInstantTransfer() ? "OBI" : "OBR";
		}

		// Zambia: a wallet-institution bank always wins over the allowInstantTransfer flag (same
		// clubbing of wallets under InterBankTransfer as beneficiary_type; see BeneficiaryTypeMapper).
		if (bank.wallet()) {
			return "IWL";
		}
		if (bank.allowInstantTransfer() == null) {
			warnings.warn(oldId, "InterBankTransfer destinationBank_id " + destinationBankId + " has no resolvable allowInstantTransfer flag; dtype left blank");
			return null;
		}
		return bank.allowInstantTransfer() ? "OBI" : "OBR";
	}

	private static String billerGatewayCodeOf(Long billerId, BillersRepository billers) {
		if (billerId == null) {
			return null;
		}
		BillerRecord biller = billers.findById(billerId);
		return biller == null ? null : biller.gatewayCode();
	}

	private static String productGatewayCodeOf(Long productId, ProductsRepository products) {
		if (productId == null) {
			return null;
		}
		ProductRecord product = products.findById(productId);
		return product == null ? null : product.gatewayCode();
	}

	private static String unresolvedCountry(long oldId, String isoTransactionType, WarningCollector warnings) {
		warnings.warn(oldId, isoTransactionType + " has an unrecognized/unavailable country_id; dtype left blank");
		return null;
	}
}
