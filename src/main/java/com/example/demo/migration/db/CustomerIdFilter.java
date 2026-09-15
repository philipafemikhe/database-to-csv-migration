package com.example.demo.migration.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses the {@code --customer-ids} / {@code customer.ids} comma-separated list of numeric
 * customer ids, and builds the SQL "IN (?, ?, ...)" fragment for it. Shared by every source
 * (TransferBeneficiaries, PaymentBeneficiaries, Transactions, Users) that supports restricting a
 * migration run to a specific set of customers.
 */
public final class CustomerIdFilter {

	private CustomerIdFilter() {
	}

	public static List<Long> parse(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return Collections.emptyList();
		}
		List<Long> ids = new ArrayList<>();
		for (String token : raw.split(",")) {
			String trimmed = token.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			try {
				ids.add(Long.parseLong(trimmed));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid --customer-ids/customer.ids value '" + trimmed
						+ "'; expected a comma-separated list of numeric customer ids");
			}
		}
		return ids;
	}

	public static String inClausePlaceholders(int count) {
		StringBuilder sb = new StringBuilder("(");
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append('?');
		}
		return sb.append(')').toString();
	}
}
