package com.example.demo.migration.source;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.csv.CsvWriter;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.mapping.PaymentBeneficiaryMapper;
import com.example.demo.migration.repo.CountryPartnerRepository;
import com.example.demo.migration.repo.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class PaymentBeneficiarySource {

	private static final Logger log = LoggerFactory.getLogger(PaymentBeneficiarySource.class);
	private static final int PROGRESS_INTERVAL = 5000;

	private PaymentBeneficiarySource() {
	}

	public static int migrate(Connection connection, MigrationConfig config, UsersRepository users,
			CountryPartnerRepository countries, CsvWriter writer, WarningCollector warnings) throws SQLException, IOException {

		String columns = "id, additionalParams, customer_id, biller_id, quickPay, createDate, destinationAccountNumber, destinationAccountName";
		Integer limit = config.rowLimit();
		String top = limit == null ? "" : "TOP (" + limit + ") ";
		String sql = "SELECT " + top + columns + " FROM " + config.paymentBeneficiariesTable();

		long offset = config.paymentBeneficiaryIdOffset();
		long start = System.currentTimeMillis();
		int count = 0;
		try (Statement st = connection.createStatement()) {
			st.setFetchSize(1000);
			try (ResultSet rs = st.executeQuery(sql)) {
				while (rs.next()) {
					writer.write(PaymentBeneficiaryMapper.mapRow(rs, users, countries, offset, warnings));
					count++;
					if (count % PROGRESS_INTERVAL == 0) {
						log.info("PaymentBeneficiaries: {} rows migrated so far...", count);
					}
				}
			}
		}
		log.info("PaymentBeneficiaries: completed, {} rows in {} ms ({} warning(s))", count, System.currentTimeMillis() - start, warnings.size());
		return count;
	}
}
