package com.example.demo.migration.mapping;

/**
 * PaymentBeneficiaries.biller_id/product_id + country -> beneficiary_type, per
 * PaymentBeneficiaries_Inerswitch_MigrationLogic.xlsx.docx:
 *
 *   Malawi:    (biller_id=5 AND product_id=69) OR (biller_id=7 AND product_id=10083) -> IWL,
 *              everything else -> BILL.
 *   Botswana:  biller_id IN (90, 115) -> IWL, everything else -> BILL.
 *   Zimbabwe:  BILL, unconditionally.
 *   Zambia:    BILL, unconditionally.
 *
 * The same biller_id/product_id combination that resolves to IWL here is also what makes a row
 * wallet-eligible for wallet_type/wallet_code (see PaymentBeneficiaryMapper).
 */
public final class PaymentBeneficiaryTypeMapper {

	private PaymentBeneficiaryTypeMapper() {
	}

	public static boolean isWallet(IswCountry country, Long billerId, Long productId) {
		if (country == IswCountry.MALAWI) {
			return (isId(billerId, 5) && isId(productId, 69)) || (isId(billerId, 7) && isId(productId, 10083));
		}
		if (country == IswCountry.BOTSWANA) {
			return isId(billerId, 90) || isId(billerId, 115);
		}
		return false;
	}

	public static String map(IswCountry country, Long billerId, Long productId) {
		if (country == null) {
			return null;
		}
		return isWallet(country, billerId, productId) ? "IWL" : "BILL";
	}

	private static boolean isId(Long actual, long expected) {
		return actual != null && actual == expected;
	}
}
