package com.example.demo.migration.mapping;

import com.example.demo.migration.model.BillerRecord;
import com.example.demo.migration.model.ProductRecord;

/**
 * PaymentBeneficiaries biller/product + country -> beneficiary_type. Keys off
 * Billers.gatewayCode/Products.gatewayCode, not the raw biller_id/product_id - ids aren't stable
 * across dev/production, unlike gateway codes (same convention as admin_remarks,
 * TransactionDTypeMapper, and WalletProductMapper):
 *
 *   Malawi:    the resolved product is one of WalletProductMapper's wallet products -> IWL,
 *              everything else -> BILL.
 *   Botswana:  Billers.gatewayCode is BW-ORANGE-B2C or BW-MYZAKA-BW -> IWL, OR the resolved
 *              product is one of WalletProductMapper's wallet products -> IWL; everything else ->
 *              BILL.
 *   Zimbabwe:  BILL, unconditionally.
 *   Zambia:    BILL, unconditionally.
 */
public final class PaymentBeneficiaryTypeMapper {

	private static final String BOTSWANA_ORANGE_BILLER_GATEWAY_CODE = "bw-orange-b2c";
	private static final String BOTSWANA_MYZAKA_BILLER_GATEWAY_CODE = "bw-myzaka-bw";

	private PaymentBeneficiaryTypeMapper() {
	}

	public static boolean isWallet(IswCountry country, BillerRecord biller, ProductRecord product) {
		if (country == IswCountry.MALAWI) {
			return WalletProductMapper.isWalletProduct(product);
		}
		if (country == IswCountry.BOTSWANA) {
			return isBillerGatewayCode(biller, BOTSWANA_ORANGE_BILLER_GATEWAY_CODE)
					|| isBillerGatewayCode(biller, BOTSWANA_MYZAKA_BILLER_GATEWAY_CODE)
					|| WalletProductMapper.isWalletProduct(product);
		}
		return false;
	}

	public static String map(IswCountry country, BillerRecord biller, ProductRecord product) {
		if (country == null) {
			return null;
		}
		return isWallet(country, biller, product) ? "IWL" : "BILL";
	}

	private static boolean isBillerGatewayCode(BillerRecord biller, String expected) {
		return biller != null && biller.gatewayCode() != null
				&& expected.equalsIgnoreCase(biller.gatewayCode().trim());
	}
}
