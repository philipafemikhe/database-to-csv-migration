package com.example.demo.migration.mapping;

import java.util.Locale;

/**
 * TransferBeneficiaries.transactionType -> beneficiary_details.beneficiary_type
 *
 *   LOCAL   -> WIB
 *   RTGS    -> OBR
 *   EFT     -> OBE
 *   INSTANT -> OBI, except in Zambia where a wallet-institution bank maps to IWL
 *              (Zambia clubs wallets and instant transfers under the same old transactionType).
 */
public final class BeneficiaryTypeMapper {

	private BeneficiaryTypeMapper() {
	}

	public static String map(String oldTransactionType, boolean countryIsZambia, boolean bankIsWallet) {
		if (oldTransactionType == null) {
			return null;
		}
		return switch (oldTransactionType.trim().toUpperCase(Locale.ROOT)) {
			case "LOCAL" -> "WIB";
			case "RTGS" -> "OBR";
			case "EFT" -> "OBE";
			case "INSTANT" -> (countryIsZambia && bankIsWallet) ? "IWL" : "OBI";
			default -> null;
		};
	}
}
