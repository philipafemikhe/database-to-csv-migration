package com.example.demo.migration.source;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.csv.RecordWriter;
import com.example.demo.migration.db.CustomerIdFilter;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.mapping.TransactionMapper;
import com.example.demo.migration.mapping.TransactionStatus;
import com.example.demo.migration.repo.BanksRepository;
import com.example.demo.migration.repo.BillersRepository;
import com.example.demo.migration.repo.ProductsRepository;
import com.example.demo.migration.repo.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TransactionSource {

	private static final Logger log = LoggerFactory.getLogger(TransactionSource.class);
	private static final int PROGRESS_INTERVAL = 5000;

	private TransactionSource() {
	}

	public static int migrate(Connection connection, MigrationConfig config, UsersRepository users, BanksRepository banks,
			BillersRepository billers, ProductsRepository products, RecordWriter writer, WarningCollector warnings) throws SQLException, IOException {

		String columns = "DTYPE, id, amount, charge, bankReference, deviceUUID, narration, sourceAccount, status, "
				+ "statusCode, statusDescription, transactionDate, destinationAccount, transferProcessor, customerReference, "
				+ "finalResponseCode, gatewayReference, initialResponseCode, customer_id, destinationBank_id, biller_id, "
				+ "product_id, phoneNumber, country_id, receiptNo, tokens, destinationAccountName, channel, user_id, "
				+ "sourceAccountCurrency";
		Integer limit = config.rowLimit();
		String top = limit == null ? "" : "TOP (" + limit + ") ";
		String statusFilter = normalizeStatusFilter(config.statusFilter());
		List<Long> customerIds = CustomerIdFilter.resolve(config.transactionCustomerIds(), config.transactionCustomerUserNames(), users);

		List<String> conditions = new ArrayList<>();
		if (statusFilter != null) {
			String condition = "UPPER(status) = ?";
			if (TransactionStatus.SUCCESSFUL.equals(statusFilter)) {
				condition = "(" + condition + " OR " + TransactionStatus.successfulPendingAcknowledgementSql("status", "country_id") + ")";
			}
			conditions.add(condition);
		}
		if (!customerIds.isEmpty()) {
			conditions.add("customer_id IN " + CustomerIdFilter.inClausePlaceholders(customerIds.size()));
		}
		String notDisabled = users.notOwnedByDisabledUserSql("customer_id");
		if (notDisabled != null) {
			conditions.add(notDisabled);
		}
		int months = config.transactionMonths();
		// Whole days, so every run on the same day picks the same cutoff. Rows with a NULL
		// transactionDate don't satisfy the comparison and are excluded along with the old ones.
		LocalDateTime cutoff = months == 0 ? null : LocalDate.now().minusMonths(months).atStartOfDay();
		if (cutoff != null) {
			conditions.add("transactionDate >= ?");
			log.info("Transactions: restricting to the last {} month(s), transactionDate >= {}", months, cutoff);
		}
		String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
		String sql = "SELECT " + top + columns + " FROM " + config.transactionsTable() + where;

		long start = System.currentTimeMillis();
		int count = 0;
		try (PreparedStatement st = connection.prepareStatement(sql)) {
			st.setFetchSize(1000);
			int paramIndex = 1;
			if (statusFilter != null) {
				st.setString(paramIndex++, statusFilter);
			}
			for (Long customerId : customerIds) {
				st.setLong(paramIndex++, customerId);
			}
			if (cutoff != null) {
				st.setTimestamp(paramIndex++, Timestamp.valueOf(cutoff));
			}
			try (ResultSet rs = st.executeQuery()) {
				while (rs.next()) {
					writer.write(TransactionMapper.mapRow(rs, users, banks, billers, products, warnings));
					count++;
					if (count % PROGRESS_INTERVAL == 0) {
						log.info("Transactions: {} rows migrated so far...", count);
					}
				}
			}
		}
		log.info("Transactions: completed, {} rows in {} ms ({} warning(s))", count, System.currentTimeMillis() - start, warnings.size());
		return count;
	}

	private static String normalizeStatusFilter(String raw) {
		if (raw == null) {
			return null;
		}
		String upper = raw.trim().toUpperCase(Locale.ROOT);
		if (upper.equals("SUCCESS")) {
			return "SUCCESSFUL";
		}
		if (upper.equals("SUCCESSFUL") || upper.equals("FAILED") || upper.equals("PENDING")) {
			return upper;
		}
		throw new IllegalArgumentException("Unrecognized status.filter/--status-filter value '" + raw + "'; expected SUCCESS, SUCCESSFUL, FAILED, or PENDING");
	}
}
