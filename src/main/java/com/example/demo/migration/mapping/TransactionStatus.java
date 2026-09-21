package com.example.demo.migration.mapping;

import java.util.Locale;

/**
 * Transactions.status handling for "Payment pending acknowledgement": Botswana and Zambia treat
 * it as a successful transaction (the payment went through, only the acknowledgement is still
 * outstanding), so it is both migrated under --status-filter=SUCCESSFUL and written out as
 * SUCCESSFUL. Every other country/status is left exactly as it is in the source.
 */
public final class TransactionStatus {

	public static final String SUCCESSFUL = "SUCCESSFUL";
	private static final String PENDING_ACKNOWLEDGEMENT = "PAYMENT PENDING ACKNOWLEDGEMENT";

	private TransactionStatus() {
	}

	/** SQL predicate (no bind parameters) matching pending-acknowledgement rows of the countries that count them as successful. */
	public static String successfulPendingAcknowledgementSql(String statusColumn, String countryIdColumn) {
		return "(" + countryIdColumn + " IN (" + IswCountry.BOTSWANA.id() + ", " + IswCountry.ZAMBIA.id() + ")"
				+ " AND UPPER(REPLACE(" + statusColumn + ", '_', ' ')) = '" + PENDING_ACKNOWLEDGEMENT + "')";
	}

	public static String normalize(IswCountry country, String status) {
		if (status != null && (country == IswCountry.BOTSWANA || country == IswCountry.ZAMBIA)
				&& PENDING_ACKNOWLEDGEMENT.equals(status.trim().replace('_', ' ').toUpperCase(Locale.ROOT))) {
			return SUCCESSFUL;
		}
		return status;
	}
}
