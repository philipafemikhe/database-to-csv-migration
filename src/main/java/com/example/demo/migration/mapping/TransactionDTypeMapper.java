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
 * lookups -> the new dtype, per DTypeMappingRulesforTransactions.docx. Five ISW transaction types
 * (LocalTransfer, SchoolTransaction, BillPayment, AirtimePurchase, InterBankTransfer), each with
 * its own country-specific rule (Malawi/Botswana/Zimbabwe/Zambia):
 *
 *   LocalTransfer      -> WIB, unconditionally.
 *   SchoolTransaction  -> WIB, unconditionally (not in the original doc; added per a later
 *                          correction - previously fell through to "unrecognized", dtype blank).
 *   BillPayment        -> Malawi/Botswana: IWL when Billers.gatewayCode (by biller_id) is one of the
 *                          wallet biller gateway codes (see isWalletBiller) OR the resolved product
 *                          is one of WalletProductMapper's wallet products, else BILL;
 *                          Zimbabwe/Zambia: always BILL.
 *   AirtimePurchase    -> Malawi: product_id resolves to a Products row; if that row is one of
 *                          WalletProductMapper's wallet products (by gatewayCode) -> IWL, else ->
 *                          BILL (not Billers - Airtime keys off the product, unlike BillPayment
 *                          which keys off the biller); Botswana: same wallet biller/product check
 *                          as BillPayment (Orange Money Bank to Wallet is booked as an
 *                          AirtimePurchase); Zimbabwe/Zambia: always BILL.
 *   InterBankTransfer  -> Malawi: Banks.allowInstantTransfer by destinationBank_id (1->OBI,
 *                          0->OBR); Zimbabwe: destinationBank_id NULL->OBZ(ZIPIT), else
 *                          OBR(RTGS); Botswana: always OBE; Zambia: wallet bank->IWL, else
 *                          allowInstantTransfer flag (0->OBR, 1->OBI).
 */
public final class TransactionDTypeMapper {

	private static final Set<String> MALAWI_BILLPAYMENT_WALLET_GATEWAY_CODES = Collections.unmodifiableSet(
			new HashSet<>(Arrays.asList("tnmagents", "comvivaagent", "comvivab2w")));
	private static final Set<String> BOTSWANA_BILLPAYMENT_WALLET_GATEWAY_CODES = Collections.unmodifiableSet(
			new HashSet<>(Arrays.asList("bw-orange-b2c", "bw-myzaka-b2w")));

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
			case "SCHOOLTRANSACTION":
				return "WIB";
			case "BILLPAYMENT":
				return billPayment(oldId, country, billerId, productId, billers, products, warnings);
			case "AIRTIMEPURCHASE":
				return airtimePurchase(oldId, country, billerId, productId, billers, products, warnings);
			case "INTERBANKTRANSFER":
				return interBankTransfer(oldId, country, destinationBankId, banks, warnings);
			default:
				warnings.warn(oldId, "unrecognized transaction DTYPE '" + isoTransactionType + "' (country=" + country + "); dtype left blank");
				return null;
		}
	}

	private static String billPayment(long oldId, IswCountry country, Long billerId, Long productId, BillersRepository billers,
			ProductsRepository products, WarningCollector warnings) {
		if (country == null) {
			return unresolvedCountry(oldId, "BillPayment", warnings);
		}
		if (country == IswCountry.ZIMBABWE || country == IswCountry.ZAMBIA) {
			return "BILL";
		}
		return walletBillerOrProduct(country, billerId, productId, billers, products);
	}

	private static String walletBillerOrProduct(IswCountry country, Long billerId, Long productId, BillersRepository billers,
			ProductsRepository products) {
		if (isWalletBiller(country, billerGatewayCodeOf(billerId, billers))) {
			return "IWL";
		}
		ProductRecord product = productId == null ? null : products.findById(productId);
		return WalletProductMapper.isWalletProduct(product) ? "IWL" : "BILL";
	}

	/** Malawi/Botswana Billers.gatewayCode values that mark a BillPayment as a wallet (IWL) transaction. */
	public static boolean isWalletBiller(IswCountry country, String billerGatewayCode) {
		if (billerGatewayCode == null) {
			return false;
		}
		String normalized = billerGatewayCode.trim().toLowerCase(Locale.ROOT);
		if (country == IswCountry.MALAWI) {
			return MALAWI_BILLPAYMENT_WALLET_GATEWAY_CODES.contains(normalized);
		}
		return country == IswCountry.BOTSWANA && BOTSWANA_BILLPAYMENT_WALLET_GATEWAY_CODES.contains(normalized);
	}

	private static String airtimePurchase(long oldId, IswCountry country, Long billerId, Long productId, BillersRepository billers,
			ProductsRepository products, WarningCollector warnings) {
		if (country == null) {
			return unresolvedCountry(oldId, "AirtimePurchase", warnings);
		}
		if (country == IswCountry.BOTSWANA) {
			// Orange Money Bank to Wallet is recorded as an AirtimePurchase (biller BW-ORANGE-B2C,
			// product_id 10144), so Botswana gets the same wallet check as BillPayment.
			return walletBillerOrProduct(country, billerId, productId, billers, products);
		}
		if (country != IswCountry.MALAWI) {
			return "BILL";
		}
		ProductRecord product = productId == null ? null : products.findById(productId);
		if (product == null) {
			if (productId != null && products.isAvailable()) {
				warnings.warn(oldId, "Malawi AirtimePurchase product_id " + productId + " not found in Products (country=" + country + "); dtype left blank");
			}
			return null;
		}
		return WalletProductMapper.isWalletProduct(product) ? "IWL" : "BILL";
	}

	private static String interBankTransfer(long oldId, IswCountry country, Long destinationBankId, BanksRepository banks, WarningCollector warnings) {
		if (country == null) {
			return unresolvedCountry(oldId, "InterBankTransfer", warnings);
		}
		if (country == IswCountry.BOTSWANA) {
			return "OBE";
		}
		if (country == IswCountry.ZIMBABWE) {
			return destinationBankId == null ? "OBZ" : "OBR";
		}

		BankRecord bank = destinationBankId == null ? null : banks.findById(destinationBankId);
		if (bank == null) {
			warnings.warn(oldId, "InterBankTransfer destinationBank_id " + destinationBankId + " not found in Banks (country="
					+ country + "); dtype left blank");
			return null;
		}

		if (country == IswCountry.MALAWI) {
			if (bank.allowInstantTransfer() == null) {
				warnings.warn(oldId, "InterBankTransfer destinationBank_id " + destinationBankId + " has no resolvable allowInstantTransfer flag (country=" + country + "); dtype left blank");
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
			warnings.warn(oldId, "InterBankTransfer destinationBank_id " + destinationBankId + " has no resolvable allowInstantTransfer flag (country=" + country + "); dtype left blank");
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

	private static String unresolvedCountry(long oldId, String isoTransactionType, WarningCollector warnings) {
		warnings.warn(oldId, isoTransactionType + " has an unrecognized/unavailable country_id; dtype left blank");
		return null;
	}
}
