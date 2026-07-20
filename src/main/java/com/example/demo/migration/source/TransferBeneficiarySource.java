package com.example.demo.migration.source;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.csv.CsvWriter;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.mapping.TransferBeneficiaryMapper;
import com.example.demo.migration.repo.BankBranchesRepository;
import com.example.demo.migration.repo.BanksRepository;
import com.example.demo.migration.repo.CountryPartnerRepository;
import com.example.demo.migration.repo.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class TransferBeneficiarySource {

	private static final Logger log = LoggerFactory.getLogger(TransferBeneficiarySource.class);
	private static final int PROGRESS_INTERVAL = 5000;

	private TransferBeneficiarySource() {
	}

	public static int migrate(Connection connection, MigrationConfig config, UsersRepository users, BanksRepository banks,
			BankBranchesRepository branches, CountryPartnerRepository countries, CsvWriter writer,
			String runTimestamp, WarningCollector warnings) throws SQLException, IOException {

		String columns = "id, accountName, accountNumber, bank_id, customer_id, accountCurrency, transactionType, bankBranch_id";
		Integer limit = config.rowLimit();
		String top = limit == null ? "" : "TOP (" + limit + ") ";
		String sql = "SELECT " + top + columns + " FROM " + config.transferBeneficiariesTable();

		long start = System.currentTimeMillis();
		int count = 0;
		try (Statement st = connection.createStatement()) {
			st.setFetchSize(1000);
			try (ResultSet rs = st.executeQuery(sql)) {
				while (rs.next()) {
					writer.write(TransferBeneficiaryMapper.mapRow(rs, users, banks, branches, countries, runTimestamp, warnings));
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
