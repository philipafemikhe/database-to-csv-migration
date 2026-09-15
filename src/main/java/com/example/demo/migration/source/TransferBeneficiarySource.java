package com.example.demo.migration.source;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.csv.RecordWriter;
import com.example.demo.migration.db.CustomerIdFilter;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.mapping.TransferBeneficiaryMapper;
import com.example.demo.migration.repo.BankBranchesRepository;
import com.example.demo.migration.repo.BanksRepository;
import com.example.demo.migration.repo.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public final class TransferBeneficiarySource {

	private static final Logger log = LoggerFactory.getLogger(TransferBeneficiarySource.class);
	private static final int PROGRESS_INTERVAL = 5000;

	private TransferBeneficiarySource() {
	}

	public static int migrate(Connection connection, MigrationConfig config, UsersRepository users, BanksRepository banks,
			BankBranchesRepository branches, RecordWriter writer, String runTimestamp, WarningCollector warnings) throws SQLException, IOException {

		String columns = "id, accountName, accountNumber, bank_id, customer_id, accountCurrency, transactionType, bankBranch_id";
		Integer limit = config.rowLimit();
		String top = limit == null ? "" : "TOP (" + limit + ") ";
		List<Long> customerIds = CustomerIdFilter.parse(config.customerIds());
		String sql = "SELECT " + top + columns + " FROM " + config.transferBeneficiariesTable()
				+ (customerIds.isEmpty() ? "" : " WHERE customer_id IN " + CustomerIdFilter.inClausePlaceholders(customerIds.size()));

		long start = System.currentTimeMillis();
		int count = 0;
		try (PreparedStatement st = connection.prepareStatement(sql)) {
			st.setFetchSize(1000);
			for (int i = 0; i < customerIds.size(); i++) {
				st.setLong(i + 1, customerIds.get(i));
			}
			try (ResultSet rs = st.executeQuery()) {
				while (rs.next()) {
					writer.write(TransferBeneficiaryMapper.mapRow(rs, users, banks, branches, runTimestamp, warnings));
					count++;
					if (count % PROGRESS_INTERVAL == 0) {
						log.info("TransferBeneficiaries: {} rows migrated so far...", count);
					}
				}
			}
		}
		log.info("TransferBeneficiaries: completed, {} rows in {} ms ({} warning(s))", count, System.currentTimeMillis() - start, warnings.size());
		return count;
	}
}
