package com.example.demo.migration.mapping;

import com.example.demo.migration.model.BankRecord;

import java.util.Locale;

/**
 * TransferBeneficiaries.transactionType -> beneficiary_details.beneficiary_type, per
 * TransferBeneficaries_Migration_Logic.xlsx.
 *
 *   LOCAL           -> WIB, unconditionally.
 *   EFT             -> OBE, unconditionally.
 *   INSTANT / RTGS  -> OBI / OBR (respectively), except in Zambia, where both instead resolve
 *                      the same way, purely from the row's bank (allowInstantTransfer +
 *                      institution), ignoring which of the two old transactionType values it
 *                      actually was:
 *                        allowInstantTransfer=1 + institution=WALLET -> IWL
 *                        allowInstantTransfer=1 + institution=BANK   -> OBI
 *                        allowInstantTransfer=0                      -> OBR
 *                      (An earlier version of this rule left RTGS unconditionally as OBR for
 *                      Zambia, reading the doc's dangling "Exception - Zambia" note under RTGS as
 *                      a no-op reaffirmation; the note was actually incomplete in the doc, and the
 *                      real target for a Zambia RTGS row with a BANK-institution/
 *                      allowInstantTransfer=1 bank is OBI, not OBR.) When the bank or its
 *                      allowInstantTransfer flag can't be resolved, Zambia falls back to the same
 *                      default as every other country (INSTANT->OBI, RTGS->OBR).
 */
public final class BeneficiaryTypeMapper {

	private BeneficiaryTypeMapper() {
	}

	public static String map(String oldTransactionType, boolean countryIsZambia, BankRecord bank) {
		if (oldTransactionType == null) {
			return null;
		}
		String normalized = oldTransactionType.trim().toUpperCase(Locale.ROOT);
		switch (normalized) {
			case "LOCAL":
				return "WIB";
			case "EFT":
				return "OBE";
			case "INSTANT":
			case "RTGS":
				return countryIsZambia ? zambiaInstantOrRtgs(bank, normalized) : defaultInstantOrRtgs(normalized);
			default:
				return null;
		}
	}

	private static String zambiaInstantOrRtgs(BankRecord bank, String normalized) {
		if (bank == null || bank.allowInstantTransfer() == null) {
			return defaultInstantOrRtgs(normalized);
		}
		if (!bank.allowInstantTransfer()) {
			return "OBR";
		}
		return bank.wallet() ? "IWL" : "OBI";
	}

	private static String defaultInstantOrRtgs(String normalized) {
		return "INSTANT".equals(normalized) ? "OBI" : "OBR";
	}
}
