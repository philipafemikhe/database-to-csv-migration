package com.example.demo.migration.db;

import com.example.demo.migration.model.UserRecord;
import com.example.demo.migration.repo.UsersRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses a comma-separated {@code --customer-ids}-style list into the numeric {@code Users.id}
 * values every source table's {@code customer_id} column actually stores, and builds the SQL
 * "IN (?, ?, ...)" fragment for it. Each token may be either a raw numeric id or a
 * {@code Users.userName} (e.g. "R0540050") - the alphanumeric value customers are actually
 * identified by day to day - resolved against the already-loaded {@link UsersRepository}. Shared
 * by every source (TransferBeneficiaries, PaymentBeneficiaries, Transactions, Users) that
 * supports restricting a migration run to a specific set of customers.
 */
public final class CustomerIdFilter {

	private CustomerIdFilter() {
	}

	public static List<Long> resolve(String raw, UsersRepository users) {
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
				continue;
			} catch (NumberFormatException e) {
				// Not a raw numeric id - fall through and try it as a userName below.
			}
			UserRecord user = users.findByUserName(trimmed);
			if (user == null) {
				throw new IllegalArgumentException("Invalid --customer-ids/customer.ids value '" + trimmed
						+ "'; expected a numeric customer id or a Users.userName, and no user matches that userName");
			}
			ids.add(user.id());
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
