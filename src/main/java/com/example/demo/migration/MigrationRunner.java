package com.example.demo.migration;

import com.example.demo.migration.csv.CsvWriter;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.mapping.BeneficiaryDetailsSchema;
import com.example.demo.migration.mapping.CustomerInfoSchema;
import com.example.demo.migration.mapping.TimestampFormat;
import com.example.demo.migration.repo.BankBranchesRepository;
import com.example.demo.migration.repo.BanksRepository;
import com.example.demo.migration.repo.CountryPartnerRepository;
import com.example.demo.migration.repo.DevicesRepository;
import com.example.demo.migration.repo.UsersRepository;
import com.example.demo.migration.source.CustomerInfoSource;
import com.example.demo.migration.source.PaymentBeneficiarySource;
import com.example.demo.migration.source.TransferBeneficiarySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates one migration run: loads shared reference data once (Users, BankBranches,
 * CountryPartner), then drives the beneficiary_details.csv and/or customer_info.csv migrations
 * against it. Each of the two writes its own output file plus a sibling warnings.log.
 */
public final class MigrationRunner {

	private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

	private final Connection connection;
	private final MigrationConfig config;

	public MigrationRunner(Connection connection, MigrationConfig config) {
		this.connection = connection;
		this.config = config;
	}

	public void run(boolean doBeneficiaries, boolean doCustomers) throws Exception {
		log.info("Loading reference data...");
		UsersRepository users = UsersRepository.load(connection, config.usersTable());
		BankBranchesRepository branches = BankBranchesRepository.load(connection, config.bankBranchesTable());
		CountryPartnerRepository countries = CountryPartnerRepository.load(connection, config);
		log.info("Loaded Users={} BankBranches={} CountryPartner={}({})",
				users.size(), branches.size(), countries.size(), countries.isAvailable() ? "available" : "UNAVAILABLE");

		String runTimestamp = TimestampFormat.now();

		if (doBeneficiaries) {
			runBeneficiaryMigration(users, branches, countries, runTimestamp);
		}
		if (doCustomers) {
			runCustomerInfoMigration(users, branches, countries, runTimestamp);
		}
	}

	private void runBeneficiaryMigration(UsersRepository users, BankBranchesRepository branches,
			CountryPartnerRepository countries, String runTimestamp) throws Exception {
		BanksRepository banks = BanksRepository.load(connection, config);
		log.info("Loaded Banks={}({})", banks.size(), banks.isAvailable() ? "available" : "UNAVAILABLE");

		WarningCollector transferWarnings = new WarningCollector("TransferBeneficiaries");
		WarningCollector paymentWarnings = new WarningCollector("PaymentBeneficiaries");

		Path outputPath = Path.of(config.outputPath());
		int transferCount;
		int paymentCount;
		try (CsvWriter writer = new CsvWriter(outputPath, BeneficiaryDetailsSchema.HEADER)) {
			log.info("Migrating TransferBeneficiaries -> {}", outputPath.toAbsolutePath());
			transferCount = TransferBeneficiarySource.migrate(connection, config, users, banks, branches, countries, writer, runTimestamp, transferWarnings);
			log.info("Migrating PaymentBeneficiaries -> {}", outputPath.toAbsolutePath());
			paymentCount = PaymentBeneficiarySource.migrate(connection, config, users, countries, writer, paymentWarnings);
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
		Path outputPath = Path.of(config.customerInfoOutputPath());
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
}
