package com.example.demo.migration.source;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.csv.RecordWriter;
import com.example.demo.migration.db.CustomerIdFilter;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.mapping.PaymentBeneficiaryMapper;
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
import java.util.List;

public final class PaymentBeneficiarySource {

	private static final Logger log = LoggerFactory.getLogger(PaymentBeneficiarySource.class);
	private static final int PROGRESS_INTERVAL = 5000;

	private PaymentBeneficiarySource() {
	}

	public static int migrate(Connection connection, MigrationConfig config, UsersRepository users,
			BillersRepository billers, ProductsRepository products, RecordWriter writer, String runTimestamp,
			WarningCollector warnings) throws SQLException, IOException {

		String columns = "id, additionalParams, customer_id, biller_id, product_id, quickPay, createDate, "
				+ "destinationAccountNumber, destinationAccountName, customerReferece";
		Integer limit = config.rowLimit();
		String top = limit == null ? "" : "TOP (" + limit + ") ";
		List<Long> customerIds = CustomerIdFilter.resolve(config.beneficiaryCustomerIds(), users);
		String sql = "SELECT " + top + columns + " FROM " + config.paymentBeneficiariesTable()
				+ (customerIds.isEmpty() ? "" : " WHERE customer_id IN " + CustomerIdFilter.inClausePlaceholders(customerIds.size()));

		long offset = config.paymentBeneficiaryIdOffset();
		long start = System.currentTimeMillis();
		int count = 0;
		try (PreparedStatement st = connection.prepareStatement(sql)) {
			st.setFetchSize(1000);
			for (int i = 0; i < customerIds.size(); i++) {
				st.setLong(i + 1, customerIds.get(i));
			}
			try (ResultSet rs = st.executeQuery()) {
				while (rs.next()) {
					writer.write(PaymentBeneficiaryMapper.mapRow(rs, users, billers, products, offset, runTimestamp, warnings));
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
