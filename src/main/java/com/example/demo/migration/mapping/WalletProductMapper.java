package com.example.demo.migration.mapping;

import com.example.demo.migration.model.ProductRecord;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Malawi/Botswana wallet product identification, per MigrationCorrection_Wallets.docx. Originally
 * matched on the product *name* a product_id resolves to via the Products table (the same
 * dev/production-stability reasoning applied to Billers.name matching in
 * PaymentBeneficiaryMapper.adminRemarks and the gateway-code sets in TransactionDTypeMapper) -
 * switched to matching on Products.gatewayCode instead, per the follow-up correction that added
 * "Airtel Agents"/"TNM Agents" (gatewayCode is the more precise identifier for these specific
 * eight wallet products).
 *
 * Both `transaction` and `beneficiary_details` derive wallet_type/wallet_code (Products.name /
 * Products.gatewayCode) from this same check whenever a Malawi/Botswana row's product_id resolves
 * to one of these gateway codes - independent of whatever biller_id/product_id combination a
 * given mapper uses to decide IWL vs BILL (beneficiary_type/dtype).
 */
public final class WalletProductMapper {

	private static final Set<String> WALLET_PRODUCT_GATEWAY_CODES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"tnmb2w",         // Bank to Mpamba Wallet, product_id 69
			"airb2w",         // Bank to Airtel Money, product_id 10083
			"bw-orange-b2c",  // Orange Money Bank to Wallet, product_id 10144
			"comvivab2w",     // Simpo Transfer/Cashout, product_id 10266
			"comvivaagent",   // Simpo Cash Agent Deposit, product_id 10296
			"bw-myzaka-b2w",  // MYZAKA, product_id 10328
			"airtel",         // Airtel Agents, product_id 10378
			"tnmagents"       // TNM Agents, product_id 10260
	)));

	private WalletProductMapper() {
	}

	public static boolean isWalletProduct(ProductRecord product) {
		return product != null && product.gatewayCode() != null
				&& WALLET_PRODUCT_GATEWAY_CODES.contains(product.gatewayCode().trim().toLowerCase(Locale.ROOT));
	}
}
