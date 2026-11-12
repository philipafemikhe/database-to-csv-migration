package com.example.demo.migration;

import com.example.demo.migration.csv.CsvWriter;
import com.example.demo.migration.csv.PerCountryCsvWriter;
import com.example.demo.migration.csv.RecordWriter;
import com.example.demo.migration.db.CustomerIdFilter;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.mapping.BeneficiaryDetailsSchema;
import com.example.demo.migration.mapping.ColumnRule;
import com.example.demo.migration.mapping.CustomerInfoSchema;
import com.example.demo.migration.mapping.DevicesSchema;
import com.example.demo.migration.mapping.IswCountry;
import com.example.demo.migration.mapping.OldUssdAccountSchema;
import com.example.demo.migration.mapping.TimestampFormat;
import com.example.demo.migration.mapping.TransactionSchema;
import com.example.demo.migration.mapping.UsersSchema;
import com.example.demo.migration.model.UserRecord;
import com.example.demo.migration.repo.BankBranchesRepository;
import com.example.demo.migration.repo.BanksRepository;
import com.example.demo.migration.repo.BillersRepository;
import com.example.demo.migration.repo.CountryPartnerRepository;
import com.example.demo.migration.repo.DevicesRepository;
import com.example.demo.migration.repo.ProductsRepository;
import com.example.demo.migration.repo.UsersRepository;
import com.example.demo.migration.source.CustomerInfoSource;
import com.example.demo.migration.source.DirectCopySource;
import com.example.demo.migration.source.PaymentBeneficiarySource;
import com.example.demo.migration.source.TransactionSource;
import com.example.demo.migration.source.TransferBeneficiarySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates one migration run: loads shared reference data once (Users, BankBranches,
 * CountryPartner), then drives the beneficiary_details.csv, customer_info.csv, and/or
 * transaction.csv migrations against it. Each one writes its own output file plus a sibling
 * warnings.log.
 *
 * When {@code splitByCountry} is set, beneficiary_details and transaction are written as one
 * file per country instead (e.g. beneficiary_details_migrated_MW.csv), keyed off the same fixed
 * IswCountry code each row already carries (customer_country_code / country_code) - see
 * PerCountryCsvWriter. customer_info stays a single merged file regardless: it's grouped by the
 * dynamic CountryPartner lookup, not the fixed IswCountry table, so it doesn't share a consistent
 * country code with the other two outputs.
 *
 * users and devices (column-for-column copies of dbo.Users / dbo.Devices) are always written one
 * file per country, keyed off the same fixed IswCountry code - the user's own country_id for
 * users, the owning user's country_id for devices. old_ussd_account has no country link, so it's
 * always a single file.
 */
public final class MigrationRunner {

	private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

	private final Connection connection;
	private final MigrationConfig config;

	public MigrationRunner(Connection connection, MigrationConfig config) {
		this.connection = connection;
		this.config = config;
	}

	public void run(boolean doBeneficiaries, boolean doCustomers, boolean doTransactions, boolean splitByCountry) throws Exception {
		run(doBeneficiaries, doCustomers, doTransactions, false, false, false, splitByCountry);
	}

	public void run(boolean doBeneficiaries, boolean doCustomers, boolean doTransactions, boolean doUsers, boolean doDevices,
			boolean doOldUssdAccounts, boolean splitByCountry) throws Exception {
		log.info("Loading reference data...");
		UsersRepository users = UsersRepository.load(connection, config.usersTable());
		BankBranchesRepository branches = BankBranchesRepository.load(connection, config.bankBranchesTable());
		CountryPartnerRepository countries = CountryPartnerRepository.load(connection, config);
		log.info("Loaded Users={} BankBranches={} CountryPartner={}({})",
				users.size(), branches.size(), countries.size(), countries.isAvailable() ? "available" : "UNAVAILABLE");
		if (users.hasDisableUserColumn()) {
			log.info("Excluding {} user(s) with disable_user = 1, and their devices, old USSD accounts, beneficiaries and transactions",
					users.disabledIds().size());
		} else {
			log.warn("{} has no disable_user column; no users are excluded as disabled", config.usersTable());
		}

		String runTimestamp = TimestampFormat.now();

		BanksRepository banks = null;
		BillersRepository billers = null;
		ProductsRepository products = null;
		if (doBeneficiaries || doTransactions) {
			banks = BanksRepository.load(connection, config);
			log.info("Loaded Banks={}({})", banks.size(), banks.isAvailable() ? "available" : "UNAVAILABLE");
			billers = BillersRepository.load(connection, config);
			log.info("Loaded Billers={}({})", billers.size(), billers.isAvailable() ? "available" : "UNAVAILABLE");
			products = ProductsRepository.load(connection, config);
			log.info("Loaded Products={}({})", products.size(), products.isAvailable() ? "available" : "UNAVAILABLE");
		}

		if (doBeneficiaries) {
			runBeneficiaryMigration(users, banks, branches, billers, products, runTimestamp, splitByCountry);
		}
		if (doCustomers) {
			runCustomerInfoMigration(users, branches, countries, runTimestamp);
		}
		if (doTransactions) {
			runTransactionMigration(users, banks, billers, products, splitByCountry);
		}
		List<Long> customerIds = doUsers || doDevices
				? CustomerIdFilter.resolve(config.customerIds(), config.customerUserNames(), users)
				: Collections.<Long>emptyList();
		if (doUsers) {
			RecordWriter writer = new PerCountryCsvWriter(Paths.get(config.usersOutputPath()), UsersSchema.HEADER,
					record -> countryCode(parseId(record.get("country_id"))));
			runDirectCopy("Users", "users", config.usersTable(), UsersSchema.RULES, UsersSchema.HEADER,
					"id", customerIds, users.activeUsersSql(), writer, Paths.get(config.usersOutputPath()));
		}
		if (doDevices) {
			RecordWriter writer = new PerCountryCsvWriter(Paths.get(config.devicesOutputPath()), DevicesSchema.HEADER, record -> {
				UserRecord owner = users.findById(parseId(record.get("customer_id")));
				return owner == null ? null : countryCode(owner.countryId());
			});
			runDirectCopy("Devices", "devices", config.devicesTable(), DevicesSchema.RULES, DevicesSchema.HEADER,
					"customer_id", customerIds, users.notOwnedByDisabledUserSql("customer_id"), writer,
					Paths.get(config.devicesOutputPath()));
		}
		if (doOldUssdAccounts) {
			Path outputPath = Paths.get(config.oldUssdAccountsOutputPath());
			runDirectCopy("Old_Ussd_Account", "old_ussd_account", config.oldUssdAccountTable(), OldUssdAccountSchema.RULES,
					OldUssdAccountSchema.HEADER, null, Collections.<Long>emptyList(), users.notDisabledUserNameSql("userName"),
					new CsvWriter(outputPath, OldUssdAccountSchema.HEADER), outputPath);
		}
	}

	private void runDirectCopy(String label, String outputName, String table, ColumnRule[] rules,
			String[] header, String filterColumn, List<Long> filterIds, String exclusion, RecordWriter writer, Path outputPath)
			throws Exception {
		WarningCollector warnings = new WarningCollector(label);
		int count;
		try (RecordWriter w = writer) {
			log.info("Migrating {} -> {}", table, outputPath.toAbsolutePath());
			count = DirectCopySource.migrate(connection, label, table, rules, header, filterColumn, filterIds, exclusion,
					config.rowLimit(), w, warnings);
		}
		Path warningsPath = warnings.isEmpty() ? null : ValidationWarning.writeLog(outputPath, warnings.all());
		log.info("{}: wrote {} rows to {}; {} warning(s){}",
				outputName, count, describeDestination(writer, outputPath), warnings.size(),
				warningsPath == null ? "" : " -> " + warningsPath.toAbsolutePath());
	}

	private static String countryCode(Long countryId) {
		IswCountry country = IswCountry.fromId(countryId);
		return country == null ? null : country.code();
	}

	private static Long parseId(String value) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void runBeneficiaryMigration(UsersRepository users, BanksRepository banks, BankBranchesRepository branches,
			BillersRepository billers, ProductsRepository products, String runTimestamp, boolean splitByCountry) throws Exception {
		WarningCollector transferWarnings = new WarningCollector("TransferBeneficiaries");
		WarningCollector paymentWarnings = new WarningCollector("PaymentBeneficiaries");

		Path outputPath = Paths.get(config.outputPath());
		int transferCount;
		int paymentCount;
		RecordWriter writer = splitByCountry
				? new PerCountryCsvWriter(outputPath, BeneficiaryDetailsSchema.HEADER, "customer_country_code")
				: new CsvWriter(outputPath, BeneficiaryDetailsSchema.HEADER);
		try (RecordWriter w = writer) {
			log.info("Migrating TransferBeneficiaries -> {}", outputPath.toAbsolutePath());
			transferCount = TransferBeneficiarySource.migrate(connection, config, users, banks, branches, w, runTimestamp, transferWarnings);
			log.info("Migrating PaymentBeneficiaries -> {}", outputPath.toAbsolutePath());
			paymentCount = PaymentBeneficiarySource.migrate(connection, config, users, billers, products, w, runTimestamp, paymentWarnings);
		}

		int warningCount = transferWarnings.size() + paymentWarnings.size();
		Path warningsPath = null;
		if (warningCount > 0) {
			List<ValidationWarning> combined = new ArrayList<>(transferWarnings.all());
			combined.addAll(paymentWarnings.all());
			warningsPath = ValidationWarning.writeLog(outputPath, combined);
		}

		log.info("beneficiary_details: wrote {} rows to {} ({} transfer, {} payment); {} warning(s){}",
				transferCount + paymentCount, describeDestination(writer, outputPath), transferCount, paymentCount, warningCount,
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
			count = CustomerInfoSource.migrate(config, users, branches, devices, countries, writer, runTimestamp, warnings);
		}

		Path warningsPath = warnings.isEmpty() ? null : ValidationWarning.writeLog(outputPath, warnings.all());
		log.info("customer_info: wrote {} rows to {}; {} warning(s){}",
				count, outputPath.toAbsolutePath(), warnings.size(),
				warningsPath == null ? "" : " -> " + warningsPath.toAbsolutePath());
	}

	private void runTransactionMigration(UsersRepository users, BanksRepository banks, BillersRepository billers,
			ProductsRepository products, boolean splitByCountry) throws Exception {
		WarningCollector warnings = new WarningCollector("Transactions");
		Path outputPath = Paths.get(config.transactionsOutputPath());
		int count;
		RecordWriter writer = splitByCountry
				? new PerCountryCsvWriter(outputPath, TransactionSchema.HEADER, "country_code")
				: new CsvWriter(outputPath, TransactionSchema.HEADER);
		try (RecordWriter w = writer) {
			log.info("Migrating Transactions -> {}", outputPath.toAbsolutePath());
			count = TransactionSource.migrate(connection, config, users, banks, billers, products, w, warnings);
		}

		Path warningsPath = warnings.isEmpty() ? null : ValidationWarning.writeLog(outputPath, warnings.all());
		log.info("transaction: wrote {} rows to {}; {} warning(s){}",
				count, describeDestination(writer, outputPath), warnings.size(),
				warningsPath == null ? "" : " -> " + warningsPath.toAbsolutePath());
	}

	private static String describeDestination(RecordWriter writer, Path fallbackPath) {
		if (!(writer instanceof PerCountryCsvWriter)) {
			return fallbackPath.toAbsolutePath().toString();
		}
		Map<String, Path> files = ((PerCountryCsvWriter) writer).writtenFiles();
		if (files.isEmpty()) {
			return "(no rows written)";
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Path> entry : files.entrySet()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(entry.getKey()).append('=').append(entry.getValue().toAbsolutePath());
		}
		return sb.toString();
	}
}
