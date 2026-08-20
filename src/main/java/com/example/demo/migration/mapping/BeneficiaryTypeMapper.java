package com.example.demo.migration.mapping;

import com.example.demo.migration.model.BankRecord;

import java.util.Locale;

/**
 * TransferBeneficiaries.transactionType -> beneficiary_details.beneficiary_type, per
 * TransferBeneficaries_Migration_Logic.xlsx.
 *
 *   LOCAL   -> WIB
 *   RTGS    -> OBR (Zambia included - the doc's "Exception - Zambia" note under RTGS just
 *              re-affirms OBR for a BANK-institution/allowInstantTransfer=1 bank, it doesn't
 *              override the result)
 *   EFT     -> OBE
 *   INSTANT -> OBI, except in Zambia where a wallet-institution bank with allowInstantTransfer=1
 *              maps to IWL instead (both conditions required - wallet alone isn't enough).
 */
public final class BeneficiaryTypeMapper {

	private BeneficiaryTypeMapper() {
	}

	public static String map(String oldTransactionType, boolean countryIsZambia, BankRecord bank) {
		if (oldTransactionType == null) {
			return null;
		}
		boolean zambiaInstantWallet = countryIsZambia && bank != null && bank.wallet() && Boolean.TRUE.equals(bank.allowInstantTransfer());
		switch (oldTransactionType.trim().toUpperCase(Locale.ROOT)) {
			case "LOCAL":
				return "WIB";
			case "RTGS":
				return "OBR";
			case "EFT":
				return "OBE";
			case "INSTANT":
				return zambiaInstantWallet ? "IWL" : "OBI";
			default:
				return null;
		}
	}
}
