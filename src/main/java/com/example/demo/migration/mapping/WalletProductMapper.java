package com.example.demo.migration.mapping;

import com.example.demo.migration.model.ProductRecord;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Malawi/Botswana wallet product identification, per MigrationCorrection_Wallets.docx. The doc
 * pairs six product_ids with six product names, but the match here is on the *name* a product_id
 * resolves to via the Products table, not the id itself - the same dev/production-stability
 * reasoning already applied to Billers.name matching in PaymentBeneficiaryMapper.adminRemarks and
 * the gateway-code sets in TransactionDTypeMapper, since product_ids aren't guaranteed stable
 * across environments even though a product's name is.
 *
 * Both `transaction` and `beneficiary_details` derive wallet_type/wallet_code (Products.name /
 * Products.gatewayCode) from this same check whenever a Malawi/Botswana row's product_id resolves
 * to one of these names - independent of whatever biller_id/product_id combination a given mapper
 * uses to decide IWL vs BILL (beneficiary_type/dtype).
 */
public final class WalletProductMapper {

	private static final Set<String> WALLET_PRODUCT_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"bank to mpamba wallet",      // product_id 69
			"bank to airtel money",       // product_id 10083
			"orange money bank to wallet", // product_id 10144
			"simpo transfer/cashout",     // product_id 10266
			"simpo cash agent deposit",   // product_id 10296
			"myzaka"                      // product_id 10328
	)));

	private WalletProductMapper() {
	}

	public static boolean isWalletProduct(ProductRecord product) {
		return product != null && product.name() != null
				&& WALLET_PRODUCT_NAMES.contains(product.name().trim().toLowerCase(Locale.ROOT));
	}
}
