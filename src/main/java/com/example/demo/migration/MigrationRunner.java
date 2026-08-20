package com.example.demo.migration;

import com.example.demo.migration.csv.CsvWriter;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.mapping.BeneficiaryDetailsSchema;
import com.example.demo.migration.mapping.CustomerInfoSchema;
import com.example.demo.migration.mapping.TimestampFormat;
import com.example.demo.migration.mapping.TransactionSchema;
import com.example.demo.migration.repo.BankBranchesRepository;
import com.example.demo.migration.repo.BanksRepository;
import com.example.demo.migration.repo.BillersRepository;
import com.example.demo.migration.repo.CountryPartnerRepository;
import com.example.demo.migration.repo.DevicesRepository;
import com.example.demo.migration.repo.ProductsRepository;
import com.example.demo.migration.repo.UsersRepository;
import com.example.demo.migration.source.CustomerInfoSource;
import com.example.demo.migration.source.PaymentBeneficiarySource;
import com.example.demo.migration.source.TransactionSource;
import com.example.demo.migration.source.TransferBeneficiarySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates one migration run: loads shared reference data once (Users, BankBranches,
 * CountryPartner), then drives the beneficiary_details.csv, customer_info.csv, and/or
 * transaction.csv migrations against it. Each one writes its own output file plus a sibling
 * warnings.log.
 */
public final class MigrationRunner {

	private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

	private final Connection connection;
	private final MigrationConfig config;

	public MigrationRunner(Connection connection, MigrationConfig config) {
		this.connection = connection;
		this.config = config;
	}

	public void run(boolean doBeneficiaries, boolean doCustomers, boolean doTransactions) throws Exception {
		log.info("Loading reference data...");
		UsersRepository users = UsersRepository.load(connection, config.usersTable());
		BankBranchesRepository branches = BankBranchesRepository.load(connection, config.bankBranchesTable());
		CountryPartnerRepository countries = CountryPartnerRepository.load(connection, config);
		log.info("Loaded Users={} BankBranches={} CountryPartner={}({})",
				users.size(), branches.size(), countries.size(), countries.isAvailable() ? "available" : "UNAVAILABLE");

		String runTimestamp = TimestampFormat.now();

		BanksRepository banks = null;
		if (doBeneficiaries || doTransactions) {
			banks = BanksRepository.load(connection, config);
			log.info("Loaded Banks={}({})", banks.size(), banks.isAvailable() ? "available" : "UNAVAILABLE");
		}

		if (doBeneficiaries) {
			runBeneficiaryMigration(users, banks, branches, runTimestamp);
		}
		if (doCustomers) {
			runCustomerInfoMigration(users, branches, countries, runTimestamp);
		}
		if (doTransactions) {
			runTransactionMigration(users, banks);
		}
	}

	private void runBeneficiaryMigration(UsersRepository users, BanksRepository banks, BankBranchesRepository branches,
			String runTimestamp) throws Exception {
		WarningCollector transferWarnings = new WarningCollector("TransferBeneficiaries");
		WarningCollector paymentWarnings = new WarningCollector("PaymentBeneficiaries");

		Path outputPath = Paths.get(config.outputPath());
		int transferCount;
		int paymentCount;
		try (CsvWriter writer = new CsvWriter(outputPath, BeneficiaryDetailsSchema.HEADER)) {
			log.info("Migrating TransferBeneficiaries -> {}", outputPath.toAbsolutePath());
			transferCount = TransferBeneficiarySource.migrate(connection, config, users, banks, branches, writer, runTimestamp, transferWarnings);
			log.info("Migrating PaymentBeneficiaries -> {}", outputPath.toAbsolutePath());
			paymentCount = PaymentBeneficiarySource.migrate(connection, config, users, writer, paymentWarnings);
		}

		int warningCount = transferWarnings.size() + paymentWarnings.size();
		Path warningsPath = null;
		if (warningCount > 0) {
			List<ValidationWarning> combined = new ArrayList<>(transferWarnings.all());
			combined.addAll(paymentWarnings.all());
			warningsPath = ValidationWarning.writeLog(outputPath, combined);
		}

		log.info("beneficiary_details: wrote {} rows to {} ({} transfer, {} payment); {} warning(s){}",
				transferCount + paymentCount, outputPath.toAbsolutePath(), transferCount, paymentCount, warningCount,
				warningsPath == null ? "" : " -> " + warningsPath.toAbsolutePath());
	}

	private void runCustomerInfoMigration(UsersRepository users, BankBranchesRepository branches,
			CountryPartnerRepository countries, String runTimestamp) throws Exception {
		DevicesRepository devices = DevicesRepository.load(connection, config.devicesTable());
		log.info("Loaded Devices={}", devices.size());

		WarningCollector warnings = new WarningCollector("Users");
		Path outputPath = Paths.get(config.customerInfoOutputPath());
		int count;
		try (CsvWriter writer = new CsvWriter(outputPath, CustomerInfoSchema.HEADER)) {
			log.info("Migrating Users -> {}", outputPath.toAbsolutePath());
			count = CustomerInfoSource.migrate(users, branches, devices, countries, writer, runTimestamp, warnings);
		}

		Path warningsPath = warnings.isEmpty() ? null : ValidationWarning.writeLog(outputPath, warnings.all());
		log.info("customer_info: wrote {} rows to {}; {} warning(s){}",
				count, outputPath.toAbsolutePath(), warnings.size(),
				warningsPath == null ? "" : " -> " + warningsPath.toAbsolutePath());
	}

	private void runTransactionMigration(UsersRepository users, BanksRepository banks) throws Exception {
		BillersRepository billers = BillersRepository.load(connection, config);
		log.info("Loaded Billers={}({})", billers.size(), billers.isAvailable() ? "available" : "UNAVAILABLE");
		ProductsRepository products = ProductsRepository.load(connection, config);
		log.info("Loaded Products={}({})", products.size(), products.isAvailable() ? "available" : "UNAVAILABLE");

		WarningCollector warnings = new WarningCollector("Transactions");
		Path outputPath = Paths.get(config.transactionsOutputPath());
		int count;
		try (CsvWriter writer = new CsvWriter(outputPath, TransactionSchema.HEADER)) {
			log.info("Migrating Transactions -> {}", outputPath.toAbsolutePath());
			count = TransactionSource.migrate(connection, config, users, banks, billers, products, writer, warnings);
		}

		Path warningsPath = warnings.isEmpty() ? null : ValidationWarning.writeLog(outputPath, warnings.all());
		log.info("transaction: wrote {} rows to {}; {} warning(s){}",
				count, outputPath.toAbsolutePath(), warnings.size(),
				warningsPath == null ? "" : " -> " + warningsPath.toAbsolutePath());
	}
}
