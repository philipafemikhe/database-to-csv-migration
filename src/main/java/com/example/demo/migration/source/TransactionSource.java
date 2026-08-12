package com.example.demo.migration.source;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.csv.CsvWriter;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.mapping.TransactionMapper;
import com.example.demo.migration.repo.BanksRepository;
import com.example.demo.migration.repo.BillersRepository;
import com.example.demo.migration.repo.ProductsRepository;
import com.example.demo.migration.repo.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class TransactionSource {

	private static final Logger log = LoggerFactory.getLogger(TransactionSource.class);
	private static final int PROGRESS_INTERVAL = 5000;

	private TransactionSource() {
	}

	public static int migrate(Connection connection, MigrationConfig config, UsersRepository users, BanksRepository banks,
			BillersRepository billers, ProductsRepository products, CsvWriter writer, WarningCollector warnings) throws SQLException, IOException {

		String columns = "DTYPE, id, amount, charge, bankReference, deviceUUID, narration, sourceAccount, status, "
				+ "statusCode, statusDescription, transactionDate, destinationAccount, transferProcessor, customerReference, "
				+ "finalResponseCode, gatewayReference, initialResponseCode, customer_id, destinationBank_id, biller_id, "
				+ "product_id, phoneNumber, country_id, receiptNo, tokens, destinationAccountName, channel, user_id, "
				+ "sourceAccountCurrency";
		Integer limit = config.rowLimit();
		String top = limit == null ? "" : "TOP (" + limit + ") ";
		String sql = "SELECT " + top + columns + " FROM " + config.transactionsTable();

		long start = System.currentTimeMillis();
		int count = 0;
		try (Statement st = connection.createStatement()) {
			st.setFetchSize(1000);
			try (ResultSet rs = st.executeQuery(sql)) {
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
}
