package com.example.demo.migration;

import com.example.demo.migration.csv.CsvWriter;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.mapping.BeneficiaryDetailsSchema;
import com.example.demo.migration.mapping.CustomerInfoSchema;
import com.example.demo.migration.repo.BankBranchesRepository;
import com.example.demo.migration.repo.BanksRepository;
import com.example.demo.migration.repo.CountryPartnerRepository;
import com.example.demo.migration.repo.DevicesRepository;
import com.example.demo.migration.repo.UsersRepository;
import com.example.demo.migration.source.CustomerInfoSource;
import com.example.demo.migration.source.PaymentBeneficiarySource;
import com.example.demo.migration.source.TransferBeneficiarySource;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the migration against an in-memory H2 database standing in for the real SQL Server
 * source (Banks/CountryPartner use deliberately non-default column names, to prove the
 * dynamic column resolution + config overrides actually work against a real schema).
 */
class MigrationIntegrationTest {

	private Connection connection;
	private MigrationConfig config;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() throws Exception {
		connection = DriverManager.getConnection("jdbc:h2:mem:migrationtest_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
		seedSchema();

		Path propsFile = tempDir.resolve("migration.properties");
		Files.writeString(propsFile, """
				db.url=%s
				banks.column.id=bankId
				banks.column.name=bankName
				banks.column.swiftCode=swift
				banks.column.institution=instType
				countryPartner.column.id=countryId
				countryPartner.column.name=label
				""".formatted(connection.getMetaData().getURL()), StandardCharsets.UTF_8);
		config = MigrationConfig.load(propsFile);
	}

	@AfterEach
	void tearDown() throws Exception {
		connection.close();
	}

	private void seedSchema() throws Exception {
		try (Statement st = connection.createStatement()) {
			st.execute("CREATE SCHEMA dbo");

			st.execute("CREATE TABLE dbo.CountryPartner (countryId BIGINT, label VARCHAR(50))");
			st.execute("INSERT INTO dbo.CountryPartner VALUES (1, 'Botswana'), (2, 'Zambia'), (3, 'Malawi')");

			st.execute("""
					CREATE TABLE dbo.Users (
					  id BIGINT, userName VARCHAR(50), country_id BIGINT, mobileNumber VARCHAR(30),
					  primaryAccountNumber VARCHAR(30), dateRegistered TIMESTAMP, lastLogin TIMESTAMP,
					  ussdLastLogin TIMESTAMP, defaultLanguage VARCHAR(20), timeToBeginTransact TIMESTAMP,
					  registrationMode VARCHAR(20), hasMobile BOOLEAN, hasUssd BOOLEAN, loginDisabled BOOLEAN,
					  isDeleted BOOLEAN, migratedPin BOOLEAN, ussdDisabled BOOLEAN, imsinumber VARCHAR(30),
					  reasonForProfileDeletion VARCHAR(100), profileDeletionDate TIMESTAMP
					)""");
			st.execute("""
					INSERT INTO dbo.Users VALUES
					  (100, 'R000100', 1, '26772754869', '0044501047348', '2026-06-16 13:05:21.865', NULL, NULL,
					   'ENGLISH', NULL, 'OTP', TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, NULL, NULL, NULL),
					  (200, 'R000200', 2, '260973641672', '9998887776', '2026-05-01 08:00:00.000',
					   '2026-07-01 09:00:00.000', NULL, NULL, NULL, 'OTP', TRUE, FALSE, FALSE, FALSE, FALSE, TRUE,
					   '650109169194084', NULL, NULL),
					  (300, 'R000300', 3, '265994885882', '1125501060384', '2026-06-03 16:01:24.863', NULL, NULL,
					   'FR', NULL, 'OTP', TRUE, TRUE, TRUE, FALSE, TRUE, FALSE, NULL, NULL, NULL),
					  (400, 'R000400', 888, '26770000000', NULL, '2026-01-01 00:00:00.000', NULL, NULL, NULL, NULL,
					   'OTP', TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, NULL, NULL, NULL)
					""");

			st.execute("CREATE TABLE dbo.Banks (bankId BIGINT, bankName VARCHAR(100), swift VARCHAR(20), instType VARCHAR(20))");
			st.execute("""
					INSERT INTO dbo.Banks VALUES
					  (88, 'First National Bank Botswana', 'FIRNBWGX', 'BANK'),
					  (114, 'Airtel Money', NULL, 'WALLET'),
					  (21, 'Standard Chartered Zambia', 'SCBLZMLX', 'BANK')
					""");

			st.execute("CREATE TABLE dbo.BankBranches (id BIGINT, name VARCHAR(100), bank_id BIGINT, code VARCHAR(20))");
			st.execute("""
					INSERT INTO dbo.BankBranches VALUES
					  (27, 'FRANCISTOWN(2917)', 88, 'B027'),
					  (99, 'SOME OTHER BRANCH', 999, 'B099'),
					  (500, 'PURE DIGIT CODE BRANCH', 88, '004'),
					  (501, 'LETTER PREFIX CODE BRANCH', 88, 'B112')
					""");

			st.execute("""
					CREATE TABLE dbo.Devices (
					  id BIGINT, uuid VARCHAR(64), customer_id BIGINT, registrationCompleted TIMESTAMP,
					  registrationStarted TIMESTAMP, active BOOLEAN
					)""");
			st.execute("""
					INSERT INTO dbo.Devices VALUES
					  (1, 'DEVICE-UUID-100', 100, '2026-06-16 13:06:00.000', '2026-06-16 13:05:00.000', TRUE),
					  (2, 'OLD-INACTIVE', 300, '2026-07-01 00:00:00.000', '2026-07-01 00:00:00.000', FALSE),
					  (3, 'ACTIVE-DEVICE', 300, '2026-06-01 00:00:00.000', '2026-06-01 00:00:00.000', TRUE)
					""");

			st.execute("""
					CREATE TABLE dbo.TransferBeneficiaries (
					  id BIGINT, accountName VARCHAR(100), accountNumber VARCHAR(50), bank_id BIGINT,
					  customer_id BIGINT, accountCurrency VARCHAR(10), transactionType VARCHAR(20), bankBranch_id BIGINT
					)""");
			st.execute("""
					INSERT INTO dbo.TransferBeneficiaries VALUES
					  (1, 'Cable Masters', '1810264', 88, 100, 'BWP', 'EFT', 27),
					  (2, 'Mismatch Case', '555', 88, 100, 'BWP', 'EFT', 99),
					  (3, 'No Branch Case', '777', 88, 100, 'BWP', 'EFT', NULL),
					  (4, 'Zambia Wallet Guy', '111', 114, 200, 'ZMW', 'INSTANT', NULL),
					  (5, 'Zambia NonWallet Guy', '222', 21, 200, 'ZMW', 'INSTANT', NULL),
					  (6, 'Malawi Local Guy', '333', NULL, 300, 'MWK', 'LOCAL', NULL),
					  (7, 'RTGS Guy', '444', 88, 300, 'MWK', 'RTGS', NULL),
					  (8, 'Unknown Type', '555', 88, 300, 'MWK', 'WEIRD', NULL),
					  (9, 'Unresolved Customer', '666', 88, 999, 'MWK', 'LOCAL', NULL)
					""");

			st.execute("""
					CREATE TABLE dbo.PaymentBeneficiaries (
					  id BIGINT, additionalParams VARCHAR(2000), customer_id BIGINT, biller_id BIGINT,
					  quickPay BOOLEAN, createDate TIMESTAMP, destinationAccountNumber VARCHAR(50), destinationAccountName VARCHAR(100)
					)""");
			st.execute("""
					INSERT INTO dbo.PaymentBeneficiaries VALUES
					  (500, '{"userName":"R0498292","accountNumber":"0001503165339","currencyCode":"MWK","customerName":"EMMANUEL KALULU"}',
					   300, 5, FALSE, '2026-03-20 14:05:38.456', NULL, NULL),
					  (501, '{"userName":"R0512210","toAccountName":"Nathan Direct","accountNumber":"999","currencyCode":"MWK"}',
					   300, 6, TRUE, '2026-03-23 08:57:08.711', '8888', 'Override Name'),
					  (502, 'not-json', 999, 7, FALSE, '2026-01-01 00:00:00.000', NULL, NULL)
					""");
		}
	}

	@Test
	void migratesTransferAndPaymentBeneficiariesIntoOneCsv() throws Exception {
		UsersRepository users = UsersRepository.load(connection, config.usersTable());
		BanksRepository banks = BanksRepository.load(connection, config);
		BankBranchesRepository branches = BankBranchesRepository.load(connection, config.bankBranchesTable());
		CountryPartnerRepository countries = CountryPartnerRepository.load(connection, config);

		assertTrue(banks.isAvailable());
		assertTrue(countries.isAvailable());
		assertEquals(3, banks.size());
		assertEquals(3, countries.size());

		WarningCollector transferWarnings = new WarningCollector("TransferBeneficiaries");
		WarningCollector paymentWarnings = new WarningCollector("PaymentBeneficiaries");
		Path outputPath = tempDir.resolve("beneficiary_details.csv");

		int transferCount;
		int paymentCount;
		try (CsvWriter writer = new CsvWriter(outputPath, BeneficiaryDetailsSchema.HEADER)) {
			transferCount = TransferBeneficiarySource.migrate(connection, config, users, banks, branches, countries, writer, "2026-07-20 10:00:00.000", transferWarnings);
			paymentCount = PaymentBeneficiarySource.migrate(connection, config, users, countries, writer, paymentWarnings);
		}

		assertEquals(9, transferCount);
		assertEquals(3, paymentCount);

		Map<String, CSVRecord> byId = readCsvById(outputPath, BeneficiaryDetailsSchema.HEADER, "beneficiary_id");
		assertEquals(12, byId.size());

		// Botswana EFT, valid branch match.
		CSVRecord r1 = byId.get("1");
		assertEquals("R000100", r1.get("customer_id"));
		assertEquals("Botswana", r1.get("customer_country_code"));
		assertEquals("OBE", r1.get("beneficiary_type"));
		assertEquals("First National Bank Botswana", r1.get("bank_name"));
		assertEquals("FIRNBWGX", r1.get("swift_code"));
		assertEquals("FRANCISTOWN(2917)", r1.get("branch_name"));
		assertEquals("true", r1.get("has_branches"));
		assertEquals("27", r1.get("branch_code"));
		assertEquals("true", r1.get("is_active"));
		assertEquals("false", r1.get("is_deleted"));
		assertEquals("2026-07-20 10:00:00.000", r1.get("created_at"));

		// Botswana EFT, branch belongs to a different bank -> validation failure, blank branch_name.
		CSVRecord r2 = byId.get("2");
		assertEquals("", r2.get("branch_name"));
		assertEquals("false", r2.get("has_branches"));
		assertEquals("99", r2.get("branch_code"));

		// Botswana EFT, no bankBranch_id at all.
		CSVRecord r3 = byId.get("3");
		assertEquals("", r3.get("branch_name"));
		assertEquals("", r3.get("branch_code"));

		// Zambia INSTANT + wallet bank -> IWL, wallet_type populated.
		CSVRecord r4 = byId.get("4");
		assertEquals("IWL", r4.get("beneficiary_type"));
		assertEquals("Airtel Money", r4.get("wallet_type"));
		assertEquals("Airtel Money", r4.get("bank_name"));

		// Zambia INSTANT + non-wallet bank -> OBI, no wallet_type.
		CSVRecord r5 = byId.get("5");
		assertEquals("OBI", r5.get("beneficiary_type"));
		assertEquals("", r5.get("wallet_type"));

		// LOCAL / RTGS plain mapping.
		assertEquals("WIB", byId.get("6").get("beneficiary_type"));
		assertEquals("OBR", byId.get("7").get("beneficiary_type"));

		// Unrecognized transactionType -> blank.
		assertEquals("", byId.get("8").get("beneficiary_type"));

		// Unresolved customer_id -> blank customer fields.
		CSVRecord r9 = byId.get("9");
		assertEquals("", r9.get("customer_id"));
		assertEquals("", r9.get("customer_country_code"));

		// PaymentBeneficiaries: JSON-derived fields, offset id.
		long offset = config.paymentBeneficiaryIdOffset();
		CSVRecord p1 = byId.get(String.valueOf(500 + offset));
		assertEquals("R0498292", p1.get("customer_id"));
		assertEquals("Malawi", p1.get("customer_country_code"));
		assertEquals("EMMANUEL KALULU", p1.get("beneficiary_name"));
		assertEquals("0001503165339", p1.get("account_number"));
		assertEquals("MWK", p1.get("beneficiary_currency"));
		assertEquals("false", p1.get("is_favourite"));
		assertEquals("5", p1.get("biller_id"));
		assertEquals("2026-03-20 14:05:38.456", p1.get("created_at"));

		// destinationAccountNumber/Name override JSON when present.
		CSVRecord p2 = byId.get(String.valueOf(501 + offset));
		assertEquals("Override Name", p2.get("beneficiary_name"));
		assertEquals("8888", p2.get("account_number"));
		assertEquals("true", p2.get("is_favourite"));

		// Invalid JSON + unresolved customer -> blank customer_id.
		CSVRecord p3 = byId.get(String.valueOf(502 + offset));
		assertEquals("", p3.get("customer_id"));

		// TransferBeneficiaries warnings: mismatch(row2), missing branch(row3), unknown type(row8), unresolved customer(row9).
		assertEquals(4, transferWarnings.size(), "transfer warnings: " + transferWarnings.all());
		// PaymentBeneficiaries warnings: invalid json(row502) + unresolved customer fallback(row502).
		assertEquals(2, paymentWarnings.size(), "payment warnings: " + paymentWarnings.all());
	}

	@Test
	void migratesUsersIntoCustomerInfoCsv() throws Exception {
		UsersRepository users = UsersRepository.load(connection, config.usersTable());
		BankBranchesRepository branches = BankBranchesRepository.load(connection, config.bankBranchesTable());
		DevicesRepository devices = DevicesRepository.load(connection, config.devicesTable());
		CountryPartnerRepository countries = CountryPartnerRepository.load(connection, config);

		assertEquals(4, users.size());
		assertEquals(2, devices.size());

		WarningCollector warnings = new WarningCollector("Users");
		Path outputPath = tempDir.resolve("customer_info.csv");

		int count;
		try (CsvWriter writer = new CsvWriter(outputPath, CustomerInfoSchema.HEADER)) {
			count = CustomerInfoSource.migrate(users, branches, devices, countries, writer, "2026-07-20 10:00:00.000", warnings);
		}
		assertEquals(4, count);

		Map<String, CSVRecord> byId = readCsvById(outputPath, CustomerInfoSchema.HEADER, "id");

		// User 100: exact-digits branch code match, active device, ENGLISH -> EN, hasUssd -> account_id_ussd set.
		CSVRecord u100 = byId.get("100");
		assertEquals("R000100", u100.get("customer_id"));
		assertEquals("26772754869", u100.get("mobile_number"));
		assertEquals("0044501047348", u100.get("account_id"));
		assertEquals("0044501047348", u100.get("primary_account_id"));
		assertEquals("0044501047348", u100.get("default_account_id"));
		assertEquals("500", u100.get("branch_id"));
		assertEquals("Botswana", u100.get("country_code"));
		assertEquals("Botswana", u100.get("default_country_code"));
		assertEquals("DEVICE-UUID-100", u100.get("device_id"));
		assertEquals("EN", u100.get("default_language"));
		assertEquals("0044501047348", u100.get("account_id_ussd"));
		assertEquals("2026-06-16 13:05:21.865", u100.get("registered_on"));
		assertEquals("2026-06-16 13:05:21.865", u100.get("ussd_registered_on"));
		assertEquals("2026-06-16 13:05:21.865", u100.get("created_date"));
		assertEquals("true", u100.get("has_mobile"));
		assertEquals("true", u100.get("has_ussd"));
		assertEquals("false", u100.get("is_disabled"));
		assertEquals("true", u100.get("is_migrated_user"));
		assertEquals("2026-07-20 10:00:00.000", u100.get("migrated_on"));
		assertEquals("2026-07-20 10:00:00.000", u100.get("updated_date"));
		assertEquals("MIGRATION", u100.get("created_by"));
		assertEquals("MIGRATION", u100.get("updated_by"));
		assertEquals("false", u100.get("is_approval_required"));
		assertEquals("false", u100.get("reset_imsi"));
		assertEquals("false", u100.get("is_aop_user"));
		assertTrue(u100.get("unique_id").endsWith("0044501047348"));

		// User 200: no branch match, no device (falls back to mobile_number), hasUssd=false -> account_id_ussd blank.
		CSVRecord u200 = byId.get("200");
		assertEquals("", u200.get("branch_id"));
		assertEquals("260973641672", u200.get("device_id"));
		assertEquals("", u200.get("account_id_ussd"));
		assertEquals("650109169194084", u200.get("imsi_number"));
		assertEquals("true", u200.get("is_ussd_disabled"));

		// User 300: letter-prefixed code matched via digits-only fallback; active device wins over a newer inactive one.
		CSVRecord u300 = byId.get("300");
		assertEquals("501", u300.get("branch_id"));
		assertEquals("ACTIVE-DEVICE", u300.get("device_id"));
		assertEquals("FR", u300.get("default_language"));
		assertEquals("true", u300.get("is_disabled"));
		assertEquals("true", u300.get("is_migrated_user_via_pwd"));

		// User 400: unresolved country_id (888 not in CountryPartner), no account number at all.
		CSVRecord u400 = byId.get("400");
		assertEquals("", u400.get("country_code"));
		assertEquals("", u400.get("branch_id"));
		assertEquals("26770000000", u400.get("device_id"));

		// Warnings: unresolved branch(200), unresolved country(400). User 400 has no account
		// number so no branch warning is raised for it (nothing to derive from).
		assertEquals(2, warnings.size(), "warnings: " + warnings.all());
	}

	/**
	 * Confirmed real Banks.xlsx schema: id, code, deleted, enabled, gatewayCode, name, processor,
	 * country_id, routingNumber, bankIBRefNumber, branchCode, hasBranches, allowsInstantTransfer,
	 * institution, shortName. No banks.column.* overrides here - proves the shipped defaults
	 * (swift_code <- gatewayCode) work against the real table without any config tweaking.
	 */
	@Test
	void banksRepositoryResolvesRealSchemaWithDefaultConfig() throws Exception {
		try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:banksdefaults_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")) {
			try (Statement st = conn.createStatement()) {
				st.execute("CREATE SCHEMA dbo");
				st.execute("""
						CREATE TABLE dbo.Banks (
						  id BIGINT, code VARCHAR(20), deleted BOOLEAN, enabled BOOLEAN, gatewayCode VARCHAR(20),
						  name VARCHAR(100), processor VARCHAR(20), country_id BIGINT, routingNumber VARCHAR(20),
						  bankIBRefNumber VARCHAR(20), branchCode VARCHAR(20), hasBranches BOOLEAN,
						  allowsInstantTransfer BOOLEAN, institution VARCHAR(20), shortName VARCHAR(100)
						)""");
				st.execute("""
						INSERT INTO dbo.Banks VALUES
						  (1, 'B001', FALSE, TRUE, 'FMERMWMW', 'FIRST MERCHANT BANK', 'NATIVE', 1, 'FMERMWMW', NULL, NULL, FALSE, FALSE, 'BANK', 'FIRST MERCHANT BANK'),
						  (114, 'ZM-NFS-AIRTEL', FALSE, TRUE, NULL, 'AIRTEL', 'NATIVE', 4, NULL, NULL, NULL, TRUE, TRUE, 'WALLET', 'AIRTEL')
						""");
			}

			Path propsFile = tempDir.resolve("banks-defaults.properties");
			Files.writeString(propsFile, "db.url=" + conn.getMetaData().getURL() + "\n", StandardCharsets.UTF_8);
			MigrationConfig defaultConfig = MigrationConfig.load(propsFile);

			BanksRepository banks = BanksRepository.load(conn, defaultConfig);
			assertTrue(banks.isAvailable());
			assertEquals(2, banks.size());
			assertEquals("FIRST MERCHANT BANK", banks.findById(1L).name());
			assertEquals("FMERMWMW", banks.findById(1L).swiftCode());
			assertTrue(!banks.findById(1L).wallet());
			assertEquals("AIRTEL", banks.findById(114L).name());
			assertTrue(banks.findById(114L).wallet());
		}
	}

	private static Map<String, CSVRecord> readCsvById(Path path, String[] header, String idColumn) throws Exception {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(header).setSkipHeaderRecord(true).build();
			CSVParser parser = format.parse(reader);
			return parser.getRecords().stream()
					.collect(java.util.stream.Collectors.toMap(r -> r.get(idColumn), r -> r));
		}
	}
}
