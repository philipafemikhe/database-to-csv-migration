package com.example.demo.migration;

import com.example.demo.migration.csv.CsvWriter;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.mapping.BeneficiaryDetailsSchema;
import com.example.demo.migration.mapping.CustomerInfoSchema;
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
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
		String propsContent = String.format(
				"db.url=%s\n"
				// The Transactions fixtures have no transactionDate, so they'd all fall outside the default window.
				+ "transaction.months=0\n"
				+ "banks.column.id=bankId\n"
				+ "banks.column.name=bankName\n"
				+ "banks.column.swiftCode=swift\n"
				+ "banks.column.institution=instType\n"
				+ "banks.column.allowInstantTransfer=instantOk\n"
				+ "banks.column.code=bcode\n"
				+ "countryPartner.column.id=countryId\n"
				+ "countryPartner.column.name=label\n",
				connection.getMetaData().getURL());
		Files.write(propsFile, propsContent.getBytes(StandardCharsets.UTF_8));
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

			st.execute("CREATE TABLE dbo.Users (\n"
					+ "  id BIGINT, userName VARCHAR(50), country_id BIGINT, mobileNumber VARCHAR(30),\n"
					+ "  primaryAccountNumber VARCHAR(30), dateRegistered TIMESTAMP, lastLogin TIMESTAMP,\n"
					+ "  ussdLastLogin TIMESTAMP, defaultLanguage VARCHAR(20), timeToBeginTransact TIMESTAMP,\n"
					+ "  registrationMode VARCHAR(20), hasMobile BOOLEAN, hasUssd BOOLEAN, loginDisabled BOOLEAN,\n"
					+ "  isDeleted BOOLEAN, migratedPin BOOLEAN, ussdDisabled BOOLEAN, imsinumber VARCHAR(30),\n"
					+ "  reasonForProfileDeletion VARCHAR(100), profileDeletionDate TIMESTAMP\n"
					+ ")");
			st.execute("INSERT INTO dbo.Users VALUES\n"
					+ "  (100, 'R000100', 1, '26772754869', '0044501047348', '2026-06-16 13:05:21.865', NULL, NULL,\n"
					+ "   'ENGLISH', NULL, 'OTP', TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, NULL, NULL, NULL),\n"
					+ "  (200, 'R000200', 2, '260973641672', '9998887776', '2026-05-01 08:00:00.000',\n"
					+ "   '2026-07-01 09:00:00.000', NULL, NULL, NULL, 'OTP', TRUE, FALSE, FALSE, FALSE, FALSE, TRUE,\n"
					+ "   '650109169194084', NULL, NULL),\n"
					+ "  (300, 'R000300', 3, '265994885882', '1125501060384', '2026-06-03 16:01:24.863', NULL, NULL,\n"
					+ "   'FR', NULL, 'OTP', TRUE, TRUE, TRUE, FALSE, TRUE, FALSE, NULL, NULL, NULL),\n"
					+ "  (400, 'R000400', 888, '26770000000', NULL, '2026-01-01 00:00:00.000', NULL, NULL, NULL, NULL,\n"
					+ "   'OTP', TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, NULL, NULL, NULL),\n"
					+ "  (250, 'R000250', 4, '260971111111', NULL, '2026-07-01 00:00:00.000', NULL, NULL, NULL, NULL,\n"
					+ "   'OTP', TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, NULL, NULL, NULL)\n");

			st.execute("CREATE TABLE dbo.Banks (bankId BIGINT, bankName VARCHAR(100), swift VARCHAR(20), instType VARCHAR(20), instantOk BOOLEAN, bcode VARCHAR(20))");
			st.execute("INSERT INTO dbo.Banks VALUES\n"
					+ "  (88, 'First National Bank Botswana', 'FIRNBWGX', 'BANK', NULL, 'FNBB'),\n"
					+ "  (114, 'Airtel Money', NULL, 'WALLET', FALSE, 'ZM-AIRTEL'),\n"
					+ "  (115, 'Zamtel Wallet', NULL, 'WALLET', TRUE, 'ZM-ZAMTEL'),\n"
					+ "  (21, 'Standard Chartered Zambia', 'SCBLZMLX', 'BANK', TRUE, 'SCBZM'),\n"
					+ "  (22, 'Zambia National Commercial Bank', 'ZNCOZMLX', 'BANK', FALSE, 'ZNCO'),\n"
					+ "  (200, 'Malawi Instant Bank', 'MWIXXXXX', 'BANK', TRUE, 'MWINSTANT'),\n"
					+ "  (201, 'Malawi Standard Bank', 'MWSTXXXX', 'BANK', FALSE, 'MWSTD')\n");

			st.execute("CREATE TABLE dbo.Billers (id BIGINT, gatewayCode VARCHAR(50), name VARCHAR(100))");
			st.execute("INSERT INTO dbo.Billers VALUES\n"
					+ "  (10, 'COMVIVAAGENT', 'Simpo Cash'),\n"
					+ "  (11, 'Random Biller', 'Random Biller'),\n"
					+ "  (12, 'Orange-B2C', 'Orange B2C'),\n"
					+ "  (13, 'Other BW Biller', 'Other BW Biller'),\n"
					+ "  (5, 'ESCOMPAY', 'ESCOM'),\n"
					+ "  (7, 'WBPAY', 'Water Board'),\n"
					+ "  (90, 'BW-ORANGE-B2C', 'Orange Money'),\n"
					+ "  (115, 'BW-MYZAKA-BW', 'Mascom MyZaka'),\n"
					+ "  (32, 'TNMBUN', 'TNM Bundles'),\n"
					+ "  (95, 'CITYOFBULAWAYO', 'City of Bulawayo'),\n"
					+ "  (200, 'NYARADZO', 'Nyaradzo Funeral Services'),\n"
					+ "  (202, 'ZIMRA', 'Zimra'),\n"
					+ "  (203, 'CIMAS', 'CIMAS Medical Aid Society'),\n"
					+ "  (204, 'AIRTEL', 'Airtel Bundles'),\n"
					+ "  (4, 'MWB', 'Water Board Postpaid Bills')\n");

			st.execute("CREATE TABLE dbo.Products (id BIGINT, gatewayCode VARCHAR(50), name VARCHAR(100))");
			st.execute("INSERT INTO dbo.Products VALUES\n"
					+ "  (14, 'TNMAIR', 'TNM Airtime'),\n"
					+ "  (15, 'AIRB2W', 'Airtel B2W'),\n"
					+ "  (16, 'UNKNOWNCODE', 'Unknown Product'),\n"
					+ "  (69, 'TNMB2W', 'Bank to Mpamba Wallet'),\n"
					+ "  (10083, 'AIRB2W', 'Bank to Airtel Money'),\n"
					+ "  (10144, 'BW-ORANGE-B2C', 'Orange Money Bank to Wallet'),\n"
					+ "  (115, 'BW-MASCOM-WALLET', 'Mascom Wallet'),\n"
					+ "  (10209, 'ZIMRA-DOMESTIC', 'Domestic Payments'),\n"
					+ "  (10208, 'ZIMRA-CUSTOM', 'Custom Payments'),\n"
					+ "  (10266, 'COMVIVAB2W', 'SIMPO Transfer/Cashout'),\n"
					+ "  (10296, 'COMVIVAAGENT', 'Simpo Cash Agent Deposit'),\n"
					+ "  (10328, 'BW-MYZAKA-B2W', 'MYZAKA'),\n"
					+ "  (10378, 'AIRTEL', 'Airtel Agents'),\n"
					+ "  (10260, 'TNMAGENTS', 'TNM Agents')\n");

			st.execute("CREATE TABLE dbo.BankBranches (id BIGINT, name VARCHAR(100), bank_id BIGINT, code VARCHAR(20), gatewayCode VARCHAR(20))");
			st.execute("INSERT INTO dbo.BankBranches VALUES\n"
					+ "  (27, 'FRANCISTOWN(2917)', 88, 'B027', '2917'),\n"
					+ "  (99, 'SOME OTHER BRANCH', 999, 'B099', '9901'),\n"
					+ "  (500, 'PURE DIGIT CODE BRANCH', 88, '004', '5001'),\n"
					+ "  (501, 'LETTER PREFIX CODE BRANCH', 88, 'B112', '5011')\n");

			st.execute("CREATE TABLE dbo.Devices (\n"
					+ "  id BIGINT, uuid VARCHAR(64), customer_id BIGINT, registrationCompleted TIMESTAMP,\n"
					+ "  registrationStarted TIMESTAMP, active BOOLEAN\n"
					+ ")");
			st.execute("INSERT INTO dbo.Devices VALUES\n"
					+ "  (1, 'DEVICE-UUID-100', 100, '2026-06-16 13:06:00.000', '2026-06-16 13:05:00.000', TRUE),\n"
					+ "  (2, 'OLD-INACTIVE', 300, '2026-07-01 00:00:00.000', '2026-07-01 00:00:00.000', FALSE),\n"
					+ "  (3, 'ACTIVE-DEVICE', 300, '2026-06-01 00:00:00.000', '2026-06-01 00:00:00.000', TRUE)\n");

			st.execute("CREATE TABLE dbo.TransferBeneficiaries (\n"
					+ "  id BIGINT, accountName VARCHAR(100), accountNumber VARCHAR(50), bank_id BIGINT,\n"
					+ "  customer_id BIGINT, accountCurrency VARCHAR(10), transactionType VARCHAR(20), bankBranch_id BIGINT\n"
					+ ")");
			st.execute("INSERT INTO dbo.TransferBeneficiaries VALUES\n"
					+ "  (1, 'Cable Masters', '1810264', 88, 200, 'BWP', 'EFT', 27),\n"
					+ "  (2, 'Mismatch Case', '555', 88, 200, 'BWP', 'EFT', 99),\n"
					+ "  (3, 'No Branch Case', '777', 88, 200, 'BWP', 'EFT', NULL),\n"
					+ "  (4, 'Zambia Wallet Instant Guy', '111', 115, 250, 'ZMW', 'INSTANT', NULL),\n"
					+ "  (5, 'Zambia Wallet NoFlag Guy', '112', 114, 250, 'ZMW', 'INSTANT', NULL),\n"
					+ "  (6, 'Zambia NonWallet Instant Guy', '222', 21, 250, 'ZMW', 'INSTANT', NULL),\n"
					+ "  (7, 'Zambia RTGS Guy', '333', 21, 250, 'ZMW', 'RTGS', NULL),\n"
					+ "  (8, 'Malawi RTGS Guy', '444', 88, 100, 'MWK', 'RTGS', NULL),\n"
					+ "  (9, 'Malawi Instant Guy', '555', 200, 100, 'MWK', 'INSTANT', NULL),\n"
					+ "  (10, 'Malawi Local Guy', '666', NULL, 100, 'MWK', 'LOCAL', NULL),\n"
					+ "  (11, 'Unknown Type', '777', 88, 300, 'MWK', 'WEIRD', NULL),\n"
					+ "  (12, 'Unresolved Customer', '888', 88, 999, 'MWK', 'LOCAL', NULL),\n"
					+ "  (13, 'Zambia RTGS Wallet Guy', '999', 115, 250, 'ZMW', 'RTGS', NULL),\n"
					+ "  (14, 'Zambia RTGS NoInstant Guy', '1010', 22, 250, 'ZMW', 'RTGS', NULL),\n"
					+ "  (15, 'Malawi School Fees Guy', '1111', NULL, 100, 'MWK', 'SchoolTransaction', NULL)\n");

			st.execute("CREATE TABLE dbo.PaymentBeneficiaries (\n"
					+ "  id BIGINT, additionalParams VARCHAR(2000), customer_id BIGINT, biller_id BIGINT, product_id BIGINT,\n"
					+ "  quickPay BOOLEAN, createDate TIMESTAMP, destinationAccountNumber VARCHAR(50), destinationAccountName VARCHAR(100),\n"
					+ "  customerReferece VARCHAR(50)\n"
					+ ")");
			st.execute("INSERT INTO dbo.PaymentBeneficiaries VALUES\n"
					+ "  (500, '{\"userName\":\"R0498292\",\"accountNumber\":\"0001503165339\",\"currencyCode\":\"MWK\",\"customerName\":\"EMMANUEL KALULU\"}',\n"
					+ "   300, 5, NULL, FALSE, '2026-03-20 14:05:38.456', NULL, NULL, NULL),\n"
					+ "  (501, '{\"userName\":\"R0512210\",\"toAccountName\":\"Nathan Direct\",\"accountNumber\":\"999\",\"currencyCode\":\"MWK\"}',\n"
					+ "   300, 6, NULL, TRUE, '2026-03-23 08:57:08.711', '8888', 'Override Name', NULL),\n"
					+ "  (502, 'not-json', 999, 7, NULL, FALSE, '2026-01-01 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (503, '{\"userName\":\"R000100\",\"customerName\":\"MALAWI WALLET GUY\",\"currencyCode\":\"MWK\"}',\n"
					+ "   100, 5, 69, FALSE, '2026-04-01 00:00:00.000', NULL, NULL, 'REF-503'),\n"
					+ "  (504, '{\"userName\":\"R000100\",\"customerName\":\"MALAWI NONWALLET GUY\",\"currencyCode\":\"MWK\"}',\n"
					+ "   100, 11, NULL, FALSE, '2026-04-02 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (505, '{\"userName\":\"R000200\",\"customerName\":\"BOTSWANA WALLET GUY\",\"currencyCode\":\"BWP\"}',\n"
					+ "   200, 90, 10144, FALSE, '2026-04-03 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (506, '{\"userName\":\"R000200\",\"customerName\":\"BOTSWANA NONWALLET GUY\",\"currencyCode\":\"BWP\"}',\n"
					+ "   200, 12, NULL, FALSE, '2026-04-04 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (507, '{\"userName\":\"R000250\",\"customerName\":\"ZAMBIA GUY\",\"currencyCode\":\"ZMW\"}',\n"
					+ "   250, 10, NULL, FALSE, '2026-04-05 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (508, '{\"userName\":\"R000100\",\"customerName\":\"MALAWI GW GUY\",\"currencyCode\":\"MWK\",\"productGatewayCode\":\"MW-ESCOM-GW-42\"}',\n"
					+ "   100, 32, NULL, FALSE, '2026-04-06 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (509, '{\"userName\":\"R000300\",\"customerName\":\"BULAWAYO GUY 1\",\"currencyCode\":\"ZWG\",\"utilityType\":\"Electricity\"}',\n"
					+ "   300, 95, 10209, FALSE, '2026-04-07 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (510, '{\"userName\":\"R000300\",\"customerName\":\"BULAWAYO GUY 2\",\"currencyCode\":\"ZWG\",\"utilityType\":\"Water\"}',\n"
					+ "   300, 95, 10208, FALSE, '2026-04-08 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (511, '{\"userName\":\"R000300\",\"customerName\":\"ZIMBABWE UTILITY GUY\",\"currencyCode\":\"ZWG\",\"utilityType\":\"Commercial\"}',\n"
					+ "   300, 95, NULL, FALSE, '2026-04-09 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (512, '{\"userName\":\"R000300\",\"customerName\":\"NYARADZO GUY\",\"currencyCode\":\"ZWG\"}',\n"
					+ "   300, 200, NULL, FALSE, '2026-04-10 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (513, '{\"userName\":\"R000300\",\"customerName\":\"ZIMRA GUY NO PRODUCT\",\"currencyCode\":\"ZWG\"}',\n"
					+ "   300, 202, NULL, FALSE, '2026-04-12 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (514, '{\"userName\":\"R000100\",\"customerName\":\"AIRTEL BUNDLE GUY\",\"currencyCode\":\"MWK\",\"productGatewayCode\":\"AIRTEL-BUNDLE-7\"}',\n"
					+ "   100, 204, NULL, FALSE, '2026-04-14 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (515, '{\"userName\":\"R000100\",\"customerName\":\"NYARADZO WRONG COUNTRY GUY\",\"currencyCode\":\"MWK\"}',\n"
					+ "   100, 200, NULL, FALSE, '2026-04-15 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (516, '{\"userName\":\"R000300\",\"customerName\":\"ZIMRA GUY DOMESTIC PRODUCT\",\"currencyCode\":\"ZWG\"}',\n"
					+ "   300, 202, 10209, FALSE, '2026-04-16 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (517, '{\"userName\":\"R000300\",\"customerName\":\"ZIMRA GUY CUSTOM PRODUCT\",\"currencyCode\":\"ZWG\"}',\n"
					+ "   300, 202, 10208, FALSE, '2026-04-17 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (518, '{\"userName\":\"R000100\",\"customerName\":\"MALAWI WALLET PRODUCT NONWALLET BILLER GUY\",\"currencyCode\":\"MWK\"}',\n"
					+ "   100, 11, 10266, FALSE, '2026-04-18 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (519, '{\"userName\":\"R000200\",\"customerName\":\"BOTSWANA WALLET BILLER NONWALLET PRODUCT GUY\",\"currencyCode\":\"BWP\"}',\n"
					+ "   200, 90, 999999, FALSE, '2026-04-19 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (520, '{\"userName\":\"R000100\",\"customerName\":\"WATER BOARD GUY\",\"currencyCode\":\"MWK\"}',\n"
					+ "   100, 4, 777, FALSE, '2026-04-20 00:00:00.000', NULL, NULL, NULL),\n"
					+ "  (521, '{\"userName\":\"R000300\",\"customerName\":\"CIMAS GUY\",\"currencyCode\":\"ZWG\",\"referenceType\":\"Member Number\"}',\n"
					+ "   300, 203, NULL, FALSE, '2026-04-21 00:00:00.000', NULL, NULL, NULL)\n");

			st.execute("CREATE TABLE dbo.Transactions (\n"
					+ "  DTYPE VARCHAR(30), id BIGINT, amount NUMERIC(19,2), charge NUMERIC(19,2), bankReference VARCHAR(255),\n"
					+ "  deviceUUID VARCHAR(255), narration VARCHAR(255), sourceAccount VARCHAR(255), status VARCHAR(255),\n"
					+ "  statusCode VARCHAR(255), statusDescription VARCHAR(255), transactionDate TIMESTAMP,\n"
					+ "  destinationAccount VARCHAR(255), transferProcessor VARCHAR(255), customerReference VARCHAR(255),\n"
					+ "  finalResponseCode VARCHAR(255), gatewayReference VARCHAR(255), initialResponseCode VARCHAR(255),\n"
					+ "  customer_id BIGINT, destinationBank_id BIGINT, biller_id BIGINT, product_id BIGINT,\n"
					+ "  phoneNumber VARCHAR(255), country_id BIGINT, receiptNo VARCHAR(255), tokens VARCHAR(255),\n"
					+ "  destinationAccountName VARCHAR(255), channel VARCHAR(255), user_id BIGINT, sourceAccountCurrency VARCHAR(50)\n"
					+ ")");
			st.execute("INSERT INTO dbo.Transactions\n"
					+ "  (DTYPE, id, amount, charge, customer_id, destinationBank_id, biller_id, product_id, country_id, user_id, sourceAccountCurrency)\n"
					+ "VALUES\n"
					+ "  ('LocalTransfer', 9001, 100.50, 0, 100, NULL, NULL, NULL, 1, 100, 'MWK'),\n"
					+ "  ('BillPayment', 9002, 500, 0, 100, NULL, 10, NULL, 1, 100, 'MWK'),\n"
					+ "  ('BillPayment', 9003, 500, 0, 100, NULL, 11, NULL, 1, 100, 'MWK'),\n"
					+ "  ('BillPayment', 9004, 500, 0, 100, NULL, 90, NULL, 2, 100, 'BWP'),\n"
					+ "  ('BillPayment', 9005, 500, 0, 100, NULL, 13, NULL, 2, 100, 'BWP'),\n"
					+ "  ('BillPayment', 9006, 500, 0, 100, NULL, NULL, NULL, 3, 100, 'ZWG'),\n"
					+ "  ('BillPayment', 9007, 500, 0, 100, NULL, NULL, NULL, 4, 100, 'ZMW'),\n"
					+ "  ('AirtimePurchase', 9008, 100, NULL, 100, NULL, NULL, 14, 1, 100, 'MWK'),\n"
					+ "  ('AirtimePurchase', 9009, 100, NULL, 100, NULL, NULL, 15, 1, 100, 'MWK'),\n"
					+ "  ('AirtimePurchase', 9010, 100, NULL, 100, NULL, NULL, 16, 1, 100, 'MWK'),\n"
					+ "  ('AirtimePurchase', 9011, 100, NULL, 100, NULL, NULL, NULL, 2, 100, 'BWP'),\n"
					+ "  ('AirtimePurchase', 9012, 100, NULL, 100, NULL, NULL, NULL, 3, 100, 'ZWG'),\n"
					+ "  ('AirtimePurchase', 9013, 100, NULL, 100, NULL, NULL, NULL, 4, 100, 'ZMW'),\n"
					+ "  ('InterBankTransfer', 9014, 1000, 0, 100, 200, NULL, NULL, 1, 100, 'MWK'),\n"
					+ "  ('InterBankTransfer', 9015, 1000, 0, 100, 201, NULL, NULL, 1, 100, 'MWK'),\n"
					+ "  ('InterBankTransfer', 9016, 1000, 0, 100, 88, NULL, NULL, 2, 100, 'BWP'),\n"
					+ "  ('InterBankTransfer', 9017, 1000, 0, 100, NULL, NULL, NULL, 3, 100, 'ZWG'),\n"
					+ "  ('InterBankTransfer', 9018, 1000, 0, 100, 88, NULL, NULL, 3, 100, 'ZWG'),\n"
					+ "  ('InterBankTransfer', 9019, 1000, 0, 100, 114, NULL, NULL, 4, 100, 'ZMW'),\n"
					+ "  ('InterBankTransfer', 9020, 1000, 0, 100, 21, NULL, NULL, 4, 100, 'ZMW'),\n"
					+ "  ('InterBankTransfer', 9021, 1000, 0, 100, 22, NULL, NULL, 4, 100, 'ZMW'),\n"
					+ "  ('WeirdType', 9022, 1000, 0, 100, NULL, NULL, NULL, 1, 100, 'MWK'),\n"
					+ "  ('LocalTransfer', 9023, 100, 0, 999, NULL, NULL, NULL, 1, 100, 'MWK'),\n"
					+ "  ('LocalTransfer', 9024, 100, 0, 100, NULL, NULL, NULL, 99, 100, 'MWK'),\n"
					+ "  ('InterBankTransfer', 9025, 1000, 0, 100, 987654, NULL, NULL, 1, 100, 'MWK'),\n"
					+ "  ('BillPayment', 9026, 500, 0, 100, NULL, 999, NULL, 3, 100, 'ZWG'),\n"
					+ "  ('InterBankTransfer', 9027, 1000, 0, 100, 115, NULL, NULL, 4, 100, 'ZMW'),\n"
					+ "  ('BillPayment', 9028, 500, 0, 100, NULL, 10, 10296, 1, 100, 'MWK'),\n"
					+ "  ('BillPayment', 9029, 500, 0, 100, NULL, 90, 10144, 2, 100, 'BWP'),\n"
					+ "  ('BillPayment', 9030, 500, 0, 100, NULL, 10, 14, 1, 100, 'MWK')\n");
		}
	}

	@Test
	void migratesTransferAndPaymentBeneficiariesIntoOneCsv() throws Exception {
		UsersRepository users = UsersRepository.load(connection, config.usersTable());
		BanksRepository banks = BanksRepository.load(connection, config);
		BankBranchesRepository branches = BankBranchesRepository.load(connection, config.bankBranchesTable());
		BillersRepository billers = BillersRepository.load(connection, config);
		ProductsRepository products = ProductsRepository.load(connection, config);

		assertTrue(banks.isAvailable());
		assertEquals(7, banks.size());
		assertTrue(billers.isAvailable());
		assertEquals(15, billers.size());
		assertTrue(products.isAvailable());
		assertEquals(14, products.size());

		WarningCollector transferWarnings = new WarningCollector("TransferBeneficiaries");
		WarningCollector paymentWarnings = new WarningCollector("PaymentBeneficiaries");
		Path outputPath = tempDir.resolve("beneficiary_details.csv");
		String runTimestamp = "2026-07-20 10:00:00.000";

		int transferCount;
		int paymentCount;
		try (CsvWriter writer = new CsvWriter(outputPath, BeneficiaryDetailsSchema.HEADER)) {
			transferCount = TransferBeneficiarySource.migrate(connection, config, users, banks, branches, writer, runTimestamp, transferWarnings);
			paymentCount = PaymentBeneficiarySource.migrate(connection, config, users, billers, products, writer, runTimestamp, paymentWarnings);
		}

		assertEquals(15, transferCount);
		assertEquals(22, paymentCount);

		Map<String, CSVRecord> byId = readCsvById(outputPath, BeneficiaryDetailsSchema.HEADER, "beneficiary_id");
		assertEquals(37, byId.size());

		// Botswana EFT, valid branch match. customer_country_code now comes from the fixed
		// IswCountry table (country_id=2 -> BW), not the dynamic CountryPartner lookup.
		CSVRecord r1 = byId.get("1");
		assertEquals("R000200", r1.get("customer_id"));
		assertEquals("BW", r1.get("customer_country_code"));
		assertEquals("OBE", r1.get("beneficiary_type"));
		assertEquals("First National Bank Botswana", r1.get("bank_name"));
		assertEquals("FIRNBWGX", r1.get("swift_code"));
		assertEquals("FRANCISTOWN(2917)", r1.get("branch_name"));
		assertEquals("true", r1.get("has_branches"));
		assertEquals("2917", r1.get("branch_code"));
		assertEquals("", r1.get("bank_gateway_code"));
		assertEquals("true", r1.get("is_active"));
		assertEquals("false", r1.get("is_deleted"));
		assertEquals("2026-07-20 10:00:00.000", r1.get("created_at"));

		// Botswana EFT, branch belongs to a different bank -> validation failure, blank
		// branch_name, but branch_code is still resolved (it's independent of that validation).
		CSVRecord r2 = byId.get("2");
		assertEquals("", r2.get("branch_name"));
		assertEquals("false", r2.get("has_branches"));
		assertEquals("9901", r2.get("branch_code"));

		// Botswana EFT, no bankBranch_id at all.
		CSVRecord r3 = byId.get("3");
		assertEquals("", r3.get("branch_name"));
		assertEquals("", r3.get("branch_code"));

		// Zambia INSTANT + wallet bank + allowInstantTransfer=1 -> IWL. wallet_type/wallet_code come
		// from Banks.name/Banks.code (per MigrationCorrection_Wallets.docx - wallet_type previously
		// held Banks.code with no wallet_code populated at all); bank_gateway_code separately comes
		// from Banks.code too (it's an INSTANT row), which is why it coincides with wallet_code here.
		CSVRecord r4 = byId.get("4");
		assertEquals("IWL", r4.get("beneficiary_type"));
		assertEquals("Zamtel Wallet", r4.get("wallet_type"));
		assertEquals("ZM-ZAMTEL", r4.get("wallet_code"));
		assertEquals("ZM-ZAMTEL", r4.get("bank_gateway_code"));
		assertEquals("Zamtel Wallet", r4.get("bank_name"));

		// Zambia INSTANT + wallet bank but allowInstantTransfer=0 -> OBR: for Zambia,
		// allowInstantTransfer=0 always means OBR regardless of institution (WALLET or BANK) - see
		// BeneficiaryTypeMapper; wallet_type/wallet_code stay blank since that population also
		// requires allowInstantTransfer=1. bank_gateway_code is still populated (it only depends on
		// the row being INSTANT, not on the derived beneficiary_type).
		CSVRecord r5 = byId.get("5");
		assertEquals("OBR", r5.get("beneficiary_type"));
		assertEquals("", r5.get("wallet_type"));
		assertEquals("", r5.get("wallet_code"));
		assertEquals("ZM-AIRTEL", r5.get("bank_gateway_code"));

		// Zambia INSTANT + non-wallet bank -> OBI, no wallet_type, bank_gateway_code from Banks.code.
		CSVRecord r6 = byId.get("6");
		assertEquals("OBI", r6.get("beneficiary_type"));
		assertEquals("", r6.get("wallet_type"));
		assertEquals("SCBZM", r6.get("bank_gateway_code"));

		// Zambia RTGS against a BANK-institution bank with allowInstantTransfer=1 -> OBI, not OBR:
		// for Zambia, INSTANT and RTGS both resolve purely from the bank's allowInstantTransfer +
		// institution, ignoring which of the two old transactionType values the row actually was
		// (the doc's dangling "Exception - Zambia" note under RTGS was incomplete, not a no-op -
		// see BeneficiaryTypeMapper). bank_gateway_code still comes from Banks.swiftCode (the
		// gatewayCode column) since that's keyed off the raw RTGS/INSTANT field, not the derived type.
		CSVRecord r7 = byId.get("7");
		assertEquals("OBI", r7.get("beneficiary_type"));
		assertEquals("SCBLZMLX", r7.get("bank_gateway_code"));

		// Malawi RTGS -> OBR, bank_gateway_code from Banks.swiftCode.
		CSVRecord r8 = byId.get("8");
		assertEquals("MW", r8.get("customer_country_code"));
		assertEquals("OBR", r8.get("beneficiary_type"));
		assertEquals("FIRNBWGX", r8.get("bank_gateway_code"));

		// Malawi INSTANT -> OBI (no Zambia-only wallet exception applies), bank_gateway_code from Banks.code.
		CSVRecord r9 = byId.get("9");
		assertEquals("OBI", r9.get("beneficiary_type"));
		assertEquals("MWINSTANT", r9.get("bank_gateway_code"));

		// LOCAL -> WIB, no bank_gateway_code.
		CSVRecord r10 = byId.get("10");
		assertEquals("WIB", r10.get("beneficiary_type"));
		assertEquals("", r10.get("bank_gateway_code"));

		// Zambia RTGS against a WALLET-institution bank with allowInstantTransfer=1 -> IWL, same as
		// the equivalent Zambia INSTANT+wallet case above (r4) - proves the unified bank-based rule
		// applies to RTGS rows too, not just INSTANT ones.
		CSVRecord r13 = byId.get("13");
		assertEquals("IWL", r13.get("beneficiary_type"));
		assertEquals("Zamtel Wallet", r13.get("wallet_type"));
		assertEquals("ZM-ZAMTEL", r13.get("wallet_code"));

		// Zambia RTGS against a bank with allowInstantTransfer=0 -> OBR (unchanged from before).
		CSVRecord r14 = byId.get("14");
		assertEquals("OBR", r14.get("beneficiary_type"));
		assertEquals("", r14.get("wallet_type"));
		assertEquals("ZNCOZMLX", r14.get("bank_gateway_code"));

		// SchoolTransaction -> STRAN, unconditionally, no bank_gateway_code.
		CSVRecord r15 = byId.get("15");
		assertEquals("STRAN", r15.get("beneficiary_type"));
		assertEquals("", r15.get("bank_gateway_code"));

		// Unrecognized transactionType -> blank.
		assertEquals("", byId.get("11").get("beneficiary_type"));

		// Unresolved customer_id -> blank customer fields.
		CSVRecord r12 = byId.get("12");
		assertEquals("", r12.get("customer_id"));
		assertEquals("", r12.get("customer_country_code"));

		// PaymentBeneficiaries: JSON-derived fields, offset id. customer_country_code now comes
		// from the fixed IswCountry table too (customer 300's country_id=3 -> ZW), for consistency
		// with TransferBeneficiaries' rows in this same merged file.
		long offset = config.paymentBeneficiaryIdOffset();
		CSVRecord p1 = byId.get(String.valueOf(500 + offset));
		assertEquals("R0498292", p1.get("customer_id"));
		assertEquals("ZW", p1.get("customer_country_code"));
		assertEquals("EMMANUEL KALULU", p1.get("beneficiary_name"));
		assertEquals("0001503165339", p1.get("account_number"));
		assertEquals("MWK", p1.get("beneficiary_currency"));
		assertEquals("false", p1.get("is_favourite"));
		assertEquals("5", p1.get("biller_id"));
		assertEquals("2026-03-20 14:05:38.456", p1.get("created_at"));
		// Zimbabwe: beneficiary_type is always BILL, regardless of biller_id; beneficiary_country
		// mirrors customer_country_code; nickname comes from additionalParams.customerName;
		// biller_name is resolved from Billers (biller_id 5 exists in this table too).
		assertEquals("BILL", p1.get("beneficiary_type"));
		assertEquals("ZW", p1.get("beneficiary_country"));
		assertEquals("EMMANUEL KALULU", p1.get("nickname"));
		assertEquals("ESCOM", p1.get("biller_name"));
		assertEquals("SYSTEM", p1.get("created_by"));
		assertEquals("SYSTEM", p1.get("last_modified_by"));
		assertEquals(runTimestamp, p1.get("last_modified_at"));
		// Zimbabwe, but biller name ("ESCOM") doesn't match any admin_remarks rule -> stays blank.
		assertEquals("", p1.get("admin_remarks"));
		// beneficiary_type is BILL -> bank_gateway_code comes from Billers.gatewayCode (biller_id=5 -> ESCOMPAY).
		assertEquals("ESCOMPAY", p1.get("bank_gateway_code"));

		// destinationAccountNumber/Name override JSON when present.
		CSVRecord p2 = byId.get(String.valueOf(501 + offset));
		assertEquals("Override Name", p2.get("beneficiary_name"));
		assertEquals("8888", p2.get("account_number"));
		assertEquals("true", p2.get("is_favourite"));
		// biller_id 6 isn't in the Billers table -> biller_name left blank (separate warning).
		assertEquals("", p2.get("biller_name"));

		// Invalid JSON + unresolved customer -> blank customer_id.
		CSVRecord p3 = byId.get(String.valueOf(502 + offset));
		assertEquals("", p3.get("customer_id"));
		// Unresolved customer -> no country, so beneficiary_type/beneficiary_country stay blank too.
		assertEquals("", p3.get("beneficiary_type"));
		assertEquals("", p3.get("beneficiary_country"));

		// Malawi, product_id=69 resolves to gatewayCode "TNMB2W", one of the fixed wallet product
		// gateway codes (WalletProductMapper) -> IWL, with wallet_type/wallet_code from the same
		// Products lookup (see p21/p22 below for a case where beneficiary_type and wallet_type/
		// wallet_code resolve independently instead).
		CSVRecord p4 = byId.get(String.valueOf(503 + offset));
		assertEquals("MW", p4.get("beneficiary_country"));
		assertEquals("IWL", p4.get("beneficiary_type"));
		assertEquals("Bank to Mpamba Wallet", p4.get("wallet_type"));
		assertEquals("TNMB2W", p4.get("wallet_code"));
		assertEquals("ESCOM", p4.get("biller_name"));
		assertEquals("REF-503", p4.get("biller_input_value"));
		assertEquals("MALAWI WALLET GUY", p4.get("nickname"));
		assertEquals("MALAWI WALLET GUY", p4.get("beneficiary_name"));
		// beneficiary_type is IWL (not BILL) -> bank_gateway_code stays blank (wallet_code carries the gateway code instead).
		assertEquals("", p4.get("bank_gateway_code"));

		// Malawi, product_id=NULL doesn't resolve to a wallet product -> BILL, no wallet_type/wallet_code.
		CSVRecord p5 = byId.get(String.valueOf(504 + offset));
		assertEquals("BILL", p5.get("beneficiary_type"));
		assertEquals("", p5.get("wallet_type"));
		assertEquals("", p5.get("wallet_code"));
		assertEquals("Random Biller", p5.get("biller_name"));

		// Botswana, biller_id=90 resolves to gatewayCode "BW-ORANGE-B2C" -> IWL; wallet_type/
		// wallet_code from Products because product_id=10144 also resolves to gatewayCode
		// "BW-ORANGE-B2C", one of the fixed wallet product gateway codes.
		CSVRecord p6 = byId.get(String.valueOf(505 + offset));
		assertEquals("BW", p6.get("beneficiary_country"));
		assertEquals("IWL", p6.get("beneficiary_type"));
		assertEquals("Orange Money Bank to Wallet", p6.get("wallet_type"));
		assertEquals("BW-ORANGE-B2C", p6.get("wallet_code"));

		// Botswana, biller_id=12 doesn't resolve to BW-ORANGE-B2C/BW-MYZAKA-BW -> BILL.
		CSVRecord p7 = byId.get(String.valueOf(506 + offset));
		assertEquals("BILL", p7.get("beneficiary_type"));
		assertEquals("", p7.get("wallet_type"));

		// Zambia -> BILL unconditionally, regardless of biller_id.
		CSVRecord p8 = byId.get(String.valueOf(507 + offset));
		assertEquals("ZM", p8.get("beneficiary_country"));
		assertEquals("BILL", p8.get("beneficiary_type"));
		assertEquals("", p8.get("wallet_type"));

		// Malawi + Billers.gatewayCode "TNMBUN" -> admin_remarks from additionalParams.productGatewayCode.
		CSVRecord p9 = byId.get(String.valueOf(508 + offset));
		assertEquals("MW-ESCOM-GW-42", p9.get("admin_remarks"));

		// Zimbabwe + Billers.gatewayCode "CITYOFBULAWAYO" -> admin_remarks always from
		// additionalParams.utilityType; product_id=10209 does NOT trigger an override (that product
		// actually belongs to Zimra per a real Products extract, not City of Bulawayo).
		CSVRecord p10 = byId.get(String.valueOf(509 + offset));
		assertEquals("Electricity", p10.get("admin_remarks"));

		// Same for product_id=10208 - still plain utilityType, no "Custom" override here.
		CSVRecord p11 = byId.get(String.valueOf(510 + offset));
		assertEquals("Water", p11.get("admin_remarks"));

		// Zimbabwe + Billers.gatewayCode "CITYOFBULAWAYO", no product_id at all -> same
		// utilityType passthrough.
		CSVRecord p12 = byId.get(String.valueOf(511 + offset));
		assertEquals("Commercial", p12.get("admin_remarks"));

		// Zimbabwe + Billers.gatewayCode "NYARADZO" -> admin_remarks hardcoded to "1".
		CSVRecord p13 = byId.get(String.valueOf(512 + offset));
		assertEquals("1", p13.get("admin_remarks"));
		assertEquals("NYARADZO", p13.get("bank_gateway_code"));

		// Zimbabwe + Billers.gatewayCode "ZIMRA", no product_id -> admin_remarks defaults to
		// static "Domestic".
		CSVRecord p14 = byId.get(String.valueOf(513 + offset));
		assertEquals("Domestic", p14.get("admin_remarks"));

		// Malawi + Billers.gatewayCode "AIRTEL" -> admin_remarks from additionalParams.productGatewayCode.
		CSVRecord p15 = byId.get(String.valueOf(514 + offset));
		assertEquals("AIRTEL-BUNDLE-7", p15.get("admin_remarks"));
		assertEquals("AIRTEL", p15.get("bank_gateway_code"));

		// Same "Nyaradzo" biller, but the customer resolves to Malawi, not Zimbabwe -> the country
		// gate isn't satisfied, so admin_remarks stays blank even though the gatewayCode matches.
		CSVRecord p16 = byId.get(String.valueOf(515 + offset));
		assertEquals("", p16.get("admin_remarks"));

		// Zimbabwe + Billers.gatewayCode "ZIMRA" -> admin_remarks is always static "Domestic",
		// regardless of product_id (10209, "Domestic Payments").
		CSVRecord p19 = byId.get(String.valueOf(516 + offset));
		assertEquals("Domestic", p19.get("admin_remarks"));

		// Same rule, product_id=10208 ("Custom Payments") -> still "Domestic", proving the rule
		// doesn't branch on the resolved product at all.
		CSVRecord p20 = byId.get(String.valueOf(517 + offset));
		assertEquals("Domestic", p20.get("admin_remarks"));

		// Malawi, product_id=10266 resolves to gatewayCode "COMVIVAB2W", one of the fixed wallet
		// product gateway codes -> routes beneficiary_type to IWL regardless of biller_id, and
		// wallet_type/wallet_code resolve from the same Products lookup; bank_gateway_code stays
		// blank since the row is IWL, not BILL.
		CSVRecord p21 = byId.get(String.valueOf(518 + offset));
		assertEquals("IWL", p21.get("beneficiary_type"));
		assertEquals("SIMPO Transfer/Cashout", p21.get("wallet_type"));
		assertEquals("COMVIVAB2W", p21.get("wallet_code"));
		assertEquals("", p21.get("bank_gateway_code"));

		// Botswana, biller_id=90 resolves to gatewayCode "BW-ORANGE-B2C" -> beneficiary_type is IWL
		// via the biller-gateway-code check, but product_id=999999 doesn't resolve in Products at
		// all, so wallet_type/wallet_code stay blank - the biller-gateway-code check and the
		// wallet-product check are independent OR'd paths to IWL, not just two views of the same
		// data.
		CSVRecord p22 = byId.get(String.valueOf(519 + offset));
		assertEquals("IWL", p22.get("beneficiary_type"));
		assertEquals("", p22.get("wallet_type"));
		assertEquals("", p22.get("wallet_code"));

		// Malawi + Billers.gatewayCode "MWB" (Water Board Postpaid Bills) -> admin_remarks is the
		// row's own product_id, not additionalParams-derived like the other rules.
		CSVRecord p23 = byId.get(String.valueOf(520 + offset));
		assertEquals("777", p23.get("admin_remarks"));

		// Zimbabwe + Billers.gatewayCode "CIMAS" -> admin_remarks from additionalParams.referenceType.
		CSVRecord p24 = byId.get(String.valueOf(521 + offset));
		assertEquals("Member Number", p24.get("admin_remarks"));

		// TransferBeneficiaries warnings: mismatch(row2), missing branch(row3), unknown type(row11), unresolved customer(row12).
		assertEquals(4, transferWarnings.size(), "transfer warnings: " + transferWarnings.all());
		// PaymentBeneficiaries warnings: invalid json(row502) + unresolved customer fallback(row502) + biller_id 6 not found(row501).
		assertEquals(3, paymentWarnings.size(), "payment warnings: " + paymentWarnings.all());
	}

	@Test
	void splitsBeneficiaryDetailsAndTransactionsByCountryWhenRequested() throws Exception {
		Path beneficiaryBase = tempDir.resolve("beneficiary_details_split.csv");
		Path transactionBase = tempDir.resolve("transaction_split.csv");
		config.override("output", beneficiaryBase.toString());
		config.override("output.transactions", transactionBase.toString());

		new MigrationRunner(connection, config).run(true, false, true, true);

		// No merged file should be written in split mode.
		assertFalse(Files.exists(beneficiaryBase));
		assertFalse(Files.exists(transactionBase));

		Path beneficiaryMw = tempDir.resolve("beneficiary_details_split_MW.csv");
		Path beneficiaryBw = tempDir.resolve("beneficiary_details_split_BW.csv");
		Path beneficiaryZw = tempDir.resolve("beneficiary_details_split_ZW.csv");
		Path beneficiaryZm = tempDir.resolve("beneficiary_details_split_ZM.csv");
		Path beneficiaryUnknown = tempDir.resolve("beneficiary_details_split_UNKNOWN.csv");
		assertTrue(Files.exists(beneficiaryMw));
		assertTrue(Files.exists(beneficiaryBw));
		assertTrue(Files.exists(beneficiaryZw));
		assertTrue(Files.exists(beneficiaryZm));
		assertTrue(Files.exists(beneficiaryUnknown));

		// Same 15 TransferBeneficiaries + 22 PaymentBeneficiaries rows as the merged-file test,
		// just spread across per-country files instead of one beneficiary_details.csv.
		int totalBeneficiaryRows = readCsvById(beneficiaryMw, BeneficiaryDetailsSchema.HEADER, "beneficiary_id").size()
				+ readCsvById(beneficiaryBw, BeneficiaryDetailsSchema.HEADER, "beneficiary_id").size()
				+ readCsvById(beneficiaryZw, BeneficiaryDetailsSchema.HEADER, "beneficiary_id").size()
				+ readCsvById(beneficiaryZm, BeneficiaryDetailsSchema.HEADER, "beneficiary_id").size()
				+ readCsvById(beneficiaryUnknown, BeneficiaryDetailsSchema.HEADER, "beneficiary_id").size();
		assertEquals(37, totalBeneficiaryRows);

		// Row 1 (Botswana EFT, customer 200) lands in the BW file, and nowhere else.
		assertTrue(readCsvById(beneficiaryBw, BeneficiaryDetailsSchema.HEADER, "beneficiary_id").containsKey("1"));
		assertFalse(readCsvById(beneficiaryMw, BeneficiaryDetailsSchema.HEADER, "beneficiary_id").containsKey("1"));
		// Row 12 (unresolved customer_id) has no resolvable country -> the UNKNOWN file.
		assertTrue(readCsvById(beneficiaryUnknown, BeneficiaryDetailsSchema.HEADER, "beneficiary_id").containsKey("12"));

		Path transactionMw = tempDir.resolve("transaction_split_MW.csv");
		Path transactionBw = tempDir.resolve("transaction_split_BW.csv");
		Path transactionZw = tempDir.resolve("transaction_split_ZW.csv");
		Path transactionZm = tempDir.resolve("transaction_split_ZM.csv");
		Path transactionUnknown = tempDir.resolve("transaction_split_UNKNOWN.csv");
		assertTrue(Files.exists(transactionMw));
		assertTrue(Files.exists(transactionBw));
		assertTrue(Files.exists(transactionZw));
		assertTrue(Files.exists(transactionZm));
		assertTrue(Files.exists(transactionUnknown));

		int totalTransactionRows = readCsvById(transactionMw, TransactionSchema.HEADER, "id").size()
				+ readCsvById(transactionBw, TransactionSchema.HEADER, "id").size()
				+ readCsvById(transactionZw, TransactionSchema.HEADER, "id").size()
				+ readCsvById(transactionZm, TransactionSchema.HEADER, "id").size()
				+ readCsvById(transactionUnknown, TransactionSchema.HEADER, "id").size();
		assertEquals(30, totalTransactionRows);

		// Transaction 9024 has an unrecognized country_id (99) -> the UNKNOWN file.
		assertTrue(readCsvById(transactionUnknown, TransactionSchema.HEADER, "id").containsKey("9024"));
	}

	@Test
	void migratesUsersIntoCustomerInfoCsv() throws Exception {
		UsersRepository users = UsersRepository.load(connection, config.usersTable());
		BankBranchesRepository branches = BankBranchesRepository.load(connection, config.bankBranchesTable());
		DevicesRepository devices = DevicesRepository.load(connection, config.devicesTable());
		CountryPartnerRepository countries = CountryPartnerRepository.load(connection, config);

		assertEquals(5, users.size());
		assertEquals(2, devices.size());

		WarningCollector warnings = new WarningCollector("Users");
		Path outputPath = tempDir.resolve("customer_info.csv");

		int count;
		try (CsvWriter writer = new CsvWriter(outputPath, CustomerInfoSchema.HEADER)) {
			count = CustomerInfoSource.migrate(config, users, branches, devices, countries, writer, "2026-07-20 10:00:00.000", warnings);
		}
		assertEquals(5, count);

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

		// User 250: added for the beneficiary_details fixed-country tests; country_id=4 isn't in
		// this test's CountryPartner table either (only 1-3), so it's unresolved here too - this
		// mapper is untouched by the fixed IswCountry change, it still uses CountryPartner.
		CSVRecord u250 = byId.get("250");
		assertEquals("", u250.get("country_code"));
		assertEquals("", u250.get("branch_id"));

		// Warnings: unresolved branch(200), unresolved country(400), unresolved country(250).
		// User 400/250 have no account number so no branch warning is raised for them (nothing to derive from).
		assertEquals(3, warnings.size(), "warnings: " + warnings.all());
	}

	@Test
	void migratesTransactionsWithDTypeRules() throws Exception {
		UsersRepository users = UsersRepository.load(connection, config.usersTable());
		BanksRepository banks = BanksRepository.load(connection, config);
		BillersRepository billers = BillersRepository.load(connection, config);
		ProductsRepository products = ProductsRepository.load(connection, config);

		assertTrue(billers.isAvailable());
		assertEquals(15, billers.size());
		assertTrue(products.isAvailable());
		assertEquals(14, products.size());

		WarningCollector warnings = new WarningCollector("Transactions");
		Path outputPath = tempDir.resolve("transaction.csv");

		int count;
		try (CsvWriter writer = new CsvWriter(outputPath, TransactionSchema.HEADER)) {
			count = TransactionSource.migrate(connection, config, users, banks, billers, products, writer, warnings);
		}
		assertEquals(30, count);

		Map<String, CSVRecord> byId = readCsvById(outputPath, TransactionSchema.HEADER, "id");

		// LocalTransfer -> WIB, unconditionally; customerid resolved via Users.userName.
		assertEquals("WIB", byId.get("9001").get("dtype"));
		assertEquals("R000100", byId.get("9001").get("customerid"));
		assertEquals("MW", byId.get("9001").get("country_code"));

		// BillPayment, Malawi: wallet gateway code -> IWL, everything else -> BILL.
		assertEquals("IWL", byId.get("9002").get("dtype"));
		// dtype IWL (not BILL) -> biller_gateway_code stays blank.
		assertEquals("", byId.get("9002").get("biller_gateway_code"));
		// product_id is NULL here, so wallet_type/wallet_code fall back to the wallet biller's own
		// Billers.name/gatewayCode (biller_id=10).
		assertEquals("Simpo Cash", byId.get("9002").get("wallet_type"));
		assertEquals("COMVIVAAGENT", byId.get("9002").get("wallet_code"));
		assertEquals("BILL", byId.get("9003").get("dtype"));
		// dtype BILL -> biller_gateway_code from Billers.gatewayCode (biller_id=11 -> "Random Biller").
		assertEquals("Random Biller", byId.get("9003").get("biller_gateway_code"));

		// BillPayment, Botswana: wallet gateway code -> IWL, everything else -> BILL.
		assertEquals("IWL", byId.get("9004").get("dtype"));
		assertEquals("BILL", byId.get("9005").get("dtype"));

		// BillPayment, Zimbabwe/Zambia: always BILL regardless of biller.
		assertEquals("BILL", byId.get("9006").get("dtype"));
		assertEquals("BILL", byId.get("9007").get("dtype"));
		// Zimbabwe isn't Malawi/Botswana (product join) or Zambia (bank join) -> wallet fields blank.
		assertEquals("", byId.get("9006").get("wallet_type"));

		// AirtimePurchase, Malawi: the same wallet-product gatewayCode check as wallet_type/wallet_code
		// (not Billers) decides BILL vs IWL - product_id=14 (TNMAIR) isn't a wallet product -> BILL;
		// product_id=15 (AIRB2W) is -> IWL; product_id=16 (UNKNOWNCODE) resolves in Products but isn't
		// a wallet product either -> BILL (no longer left blank/warned - only an unresolvable
		// product_id is now).
		assertEquals("BILL", byId.get("9008").get("dtype"));
		assertEquals("IWL", byId.get("9009").get("dtype"));
		assertEquals("BILL", byId.get("9010").get("dtype"));

		// AirtimePurchase, Botswana/Zimbabwe/Zambia: always BILL.
		assertEquals("BILL", byId.get("9011").get("dtype"));
		assertEquals("BILL", byId.get("9012").get("dtype"));
		assertEquals("BILL", byId.get("9013").get("dtype"));

		// InterBankTransfer, Malawi: Banks.allowInstantTransfer decides OBI/OBR.
		assertEquals("OBI", byId.get("9014").get("dtype"));
		assertEquals("OBR", byId.get("9015").get("dtype"));
		assertEquals("First National Bank Botswana", byId.get("9016").get("destination_bank_id"));

		// InterBankTransfer, Botswana: always OBE.
		assertEquals("OBE", byId.get("9016").get("dtype"));

		// InterBankTransfer, Zimbabwe: destinationBank_id NULL -> OBI (ZIPIT), else OBR (RTGS).
		// Zimbabwe ZIPIT (no destination bank) -> OBZ, not OBI.
		assertEquals("OBZ", byId.get("9017").get("dtype"));
		assertEquals("OBR", byId.get("9018").get("dtype"));

		// InterBankTransfer, Zambia: wallet bank wins over allowInstantTransfer -> IWL; otherwise the flag decides.
		assertEquals("IWL", byId.get("9019").get("dtype"));
		// destinationBank_id=114 (Airtel Money) is a wallet institution but allowInstantTransfer=0,
		// so the dtype IWL rule (wallet alone) fires but the separate wallet_type/wallet_code rule
		// (wallet AND allowInstantTransfer=1, per MigrationCorrection_Wallets.docx) doesn't -> blank.
		assertEquals("", byId.get("9019").get("wallet_type"));
		assertEquals("", byId.get("9019").get("wallet_code"));
		assertEquals("OBI", byId.get("9020").get("dtype"));
		assertEquals("OBR", byId.get("9021").get("dtype"));

		// Unrecognized DTYPE -> blank dtype.
		assertEquals("", byId.get("9022").get("dtype"));

		// Unresolved customer_id -> blank customerid, dtype still derived normally.
		assertEquals("", byId.get("9023").get("customerid"));
		assertEquals("WIB", byId.get("9023").get("dtype"));

		// Unrecognized country_id -> blank country_code; LocalTransfer's dtype is unconditional so it's unaffected.
		assertEquals("", byId.get("9024").get("country_code"));
		assertEquals("WIB", byId.get("9024").get("dtype"));

		// destinationBank_id not found in Banks -> blank destination_bank_id AND blank dtype (two distinct warnings).
		assertEquals("", byId.get("9025").get("destination_bank_id"));
		assertEquals("", byId.get("9025").get("dtype"));

		// BillPayment, Zimbabwe (always BILL) with an unresolvable biller_id -> biller_gateway_code
		// left blank, with its own warning (distinct from dtype resolution, which doesn't need Billers here).
		assertEquals("BILL", byId.get("9026").get("dtype"));
		assertEquals("", byId.get("9026").get("biller_gateway_code"));

		// InterBankTransfer, Zambia, destinationBank_id=115 (Zamtel Wallet: wallet institution AND
		// allowInstantTransfer=1) -> dtype IWL (wallet alone, unchanged rule) AND wallet_type/
		// wallet_code populated from Banks.name/Banks.code (the "AND" rule from
		// MigrationCorrection_Wallets.docx), unlike 9019 above where the AND fails.
		assertEquals("IWL", byId.get("9027").get("dtype"));
		assertEquals("Zamtel Wallet", byId.get("9027").get("wallet_type"));
		assertEquals("ZM-ZAMTEL", byId.get("9027").get("wallet_code"));

		// BillPayment, Malawi, biller_id=10 (gatewayCode "COMVIVAAGENT") -> dtype IWL; product_id=10296 resolves to
		// gatewayCode "COMVIVAAGENT", one of the fixed wallet product gateway codes
		// (WalletProductMapper) -> wallet_type/wallet_code from Products.name/Products.gatewayCode.
		assertEquals("IWL", byId.get("9028").get("dtype"));
		assertEquals("Simpo Cash Agent Deposit", byId.get("9028").get("wallet_type"));
		assertEquals("COMVIVAAGENT", byId.get("9028").get("wallet_code"));

		// BillPayment, Botswana, biller_id=90 (gatewayCode "BW-ORANGE-B2C") -> dtype IWL; product_id=10144 resolves
		// to gatewayCode "BW-ORANGE-B2C", also one of the fixed wallet product gateway codes ->
		// wallet_type/wallet_code from Products.
		assertEquals("IWL", byId.get("9029").get("dtype"));
		assertEquals("Orange Money Bank to Wallet", byId.get("9029").get("wallet_type"));
		assertEquals("BW-ORANGE-B2C", byId.get("9029").get("wallet_code"));

		// BillPayment, Malawi, product_id=14 resolves to "TNM Airtime"/gatewayCode "TNMAIR" - a real
		// Products row, but its gatewayCode isn't one of the fixed wallet product gateway codes, so
		// wallet_type/wallet_code come from the wallet biller (biller_id=10) rather than Products (dtype IWL is unaffected - it doesn't depend on Products
		// here). Proves the match is on the resolved product's gatewayCode, not merely on whether
		// product_id resolves at all.
		assertEquals("IWL", byId.get("9030").get("dtype"));
		assertEquals("Simpo Cash", byId.get("9030").get("wallet_type"));
		assertEquals("COMVIVAAGENT", byId.get("9030").get("wallet_code"));

		// Warnings: unrecognized DTYPE(9022), unresolved customer_id(9023), unrecognized
		// country_id(9024), unresolved destinationBank_id x2(9025), unresolved biller_id for
		// biller_gateway_code(9026). (9010's product_id now resolves to a real, non-wallet product,
		// so it's a plain BILL with no warning - see the AirtimePurchase assertions above.)
		assertEquals(6, warnings.size(), "warnings: " + warnings.all());
	}

	@Test
	void appliesTransactionCorrectionsForWalletBillersSchoolTransfersAndPendingAcknowledgement() throws Exception {
		try (Statement st = connection.createStatement()) {
			st.execute("INSERT INTO dbo.Billers VALUES\n"
					+ "  (20, 'TNMAGENTS', 'TNM Agents'),\n"
					+ "  (21, 'COMVIVAB2W', 'Simpo Transfer'),\n"
					+ "  (22, 'BW-MYZAKA-B2W', 'Mascom MyZaka')\n");
			st.execute("INSERT INTO dbo.Transactions\n"
					+ "  (DTYPE, id, amount, charge, customer_id, destinationBank_id, biller_id, product_id, country_id, user_id, sourceAccountCurrency, status)\n"
					+ "VALUES\n"
					+ "  ('BillPayment', 9101, 500, 0, 100, NULL, 20, NULL, 1, 100, 'MWK', 'SUCCESSFUL'),\n"
					+ "  ('BillPayment', 9102, 500, 0, 100, NULL, 21, NULL, 1, 100, 'MWK', 'SUCCESSFUL'),\n"
					+ "  ('BillPayment', 9103, 500, 0, 100, NULL, 22, NULL, 2, 100, 'BWP', 'SUCCESSFUL'),\n"
					+ "  ('SchoolTransaction', 9104, 750, 0, 100, NULL, NULL, NULL, 1, 100, 'MWK', 'SUCCESSFUL'),\n"
					+ "  ('LocalTransfer', 9105, 10, 0, 100, NULL, NULL, NULL, 2, 100, 'BWP', 'Payment pending acknowledgement'),\n"
					+ "  ('LocalTransfer', 9106, 10, 0, 100, NULL, NULL, NULL, 4, 100, 'ZMW', 'PAYMENT_PENDING_ACKNOWLEDGEMENT'),\n"
					+ "  ('LocalTransfer', 9107, 10, 0, 100, NULL, NULL, NULL, 1, 100, 'MWK', 'Payment pending acknowledgement'),\n"
					+ "  ('LocalTransfer', 9108, 10, 0, 100, NULL, NULL, NULL, 2, 100, 'BWP', 'FAILED'),\n"
					+ "  ('LocalTransfer', 9109, 10, 0, 100, NULL, NULL, NULL, 2, 100, 'BWP', 'Pending')\n");
		}
		UsersRepository users = UsersRepository.load(connection, config.usersTable());
		BanksRepository banks = BanksRepository.load(connection, config);
		BillersRepository billers = BillersRepository.load(connection, config);
		ProductsRepository products = ProductsRepository.load(connection, config);

		Path outputPath = tempDir.resolve("transaction_corrections.csv");
		try (CsvWriter writer = new CsvWriter(outputPath, TransactionSchema.HEADER)) {
			TransactionSource.migrate(connection, config, users, banks, billers, products, writer, new WarningCollector("Transactions"));
		}
		Map<String, CSVRecord> byId = readCsvById(outputPath, TransactionSchema.HEADER, "id");

		// Malawi wallet billers (gatewayCode TNMAGENTS/COMVIVAB2W) -> IWL, with wallet_type/wallet_code
		// carried over from the biller since product_id doesn't resolve to a wallet product.
		assertEquals("IWL", byId.get("9101").get("dtype"));
		assertEquals("TNM Agents", byId.get("9101").get("wallet_type"));
		assertEquals("TNMAGENTS", byId.get("9101").get("wallet_code"));
		assertEquals("", byId.get("9101").get("biller_gateway_code"));
		assertEquals("IWL", byId.get("9102").get("dtype"));
		assertEquals("COMVIVAB2W", byId.get("9102").get("wallet_code"));

		// Botswana wallet biller BW-MYZAKA-B2W -> IWL.
		assertEquals("IWL", byId.get("9103").get("dtype"));
		assertEquals("BW-MYZAKA-B2W", byId.get("9103").get("wallet_code"));

		// SchoolTransaction -> WIB.
		assertEquals("WIB", byId.get("9104").get("dtype"));

		// Payment pending acknowledgement counts as successful in Botswana/Zambia only.
		assertEquals("SUCCESSFUL", byId.get("9105").get("status"));
		assertEquals("SUCCESSFUL", byId.get("9106").get("status"));
		assertEquals("Payment pending acknowledgement", byId.get("9107").get("status"));

		// --status-filter=SUCCESSFUL keeps the BW/ZM pending-acknowledgement rows (9105/9106) but not
		// the Malawi one (9107) or the failed row (9108); FAILED is unaffected.
		config.override("status.filter", "SUCCESSFUL");
		Path successPath = tempDir.resolve("transaction_success.csv");
		try (CsvWriter writer = new CsvWriter(successPath, TransactionSchema.HEADER)) {
			TransactionSource.migrate(connection, config, users, banks, billers, products, writer, new WarningCollector("Transactions"));
		}
		Map<String, CSVRecord> successful = readCsvById(successPath, TransactionSchema.HEADER, "id");
		assertEquals(java.util.Set.of("9101", "9102", "9103", "9104", "9105", "9106"), successful.keySet());

		config.override("status.filter", "FAILED");
		Path failedPath = tempDir.resolve("transaction_failed.csv");
		try (CsvWriter writer = new CsvWriter(failedPath, TransactionSchema.HEADER)) {
			TransactionSource.migrate(connection, config, users, banks, billers, products, writer, new WarningCollector("Transactions"));
		}
		assertEquals(java.util.Set.of("9108"), readCsvById(failedPath, TransactionSchema.HEADER, "id").keySet());

		// PENDING matches status "PENDING" only - the BW/ZM pending-acknowledgement rows are successful.
		config.override("status.filter", "pending");
		Path pendingPath = tempDir.resolve("transaction_pending.csv");
		try (CsvWriter writer = new CsvWriter(pendingPath, TransactionSchema.HEADER)) {
			TransactionSource.migrate(connection, config, users, banks, billers, products, writer, new WarningCollector("Transactions"));
		}
		assertEquals(java.util.Set.of("9109"), readCsvById(pendingPath, TransactionSchema.HEADER, "id").keySet());
	}

	@Test
	void restrictsTransactionsToTheConfiguredNumberOfMonths() throws Exception {
		LocalDate today = LocalDate.now();
		try (Statement st = connection.createStatement()) {
			st.execute("DELETE FROM dbo.Transactions");
			st.execute("INSERT INTO dbo.Transactions\n"
					+ "  (DTYPE, id, amount, charge, customer_id, country_id, user_id, sourceAccountCurrency, transactionDate)\n"
					+ "VALUES\n"
					+ "  ('LocalTransfer', 9201, 10, 0, 100, 1, 100, 'MWK', '" + today.atTime(1, 0) + "'),\n"
					+ "  ('LocalTransfer', 9202, 10, 0, 100, 1, 100, 'MWK', '" + today.minusMonths(3).atStartOfDay() + "'),\n"
					+ "  ('LocalTransfer', 9203, 10, 0, 100, 1, 100, 'MWK', '" + today.minusMonths(3).minusDays(1).atTime(23, 59, 59) + "'),\n"
					+ "  ('LocalTransfer', 9204, 10, 0, 100, 1, 100, 'MWK', '" + today.minusMonths(5).atStartOfDay() + "'),\n"
					+ "  ('LocalTransfer', 9205, 10, 0, 100, 1, 100, 'MWK', NULL)\n");
		}
		UsersRepository users = UsersRepository.load(connection, config.usersTable());
		BanksRepository banks = BanksRepository.load(connection, config);
		BillersRepository billers = BillersRepository.load(connection, config);
		ProductsRepository products = ProductsRepository.load(connection, config);

		// Unset, the window defaults to 3 months: the cutoff day counts, the day before it and NULL dates don't.
		Path defaultProps = tempDir.resolve("default-months.properties");
		Files.write(defaultProps, "db.url=unused\n".getBytes(StandardCharsets.UTF_8));
		Path defaultPath = tempDir.resolve("transaction_default_months.csv");
		try (CsvWriter writer = new CsvWriter(defaultPath, TransactionSchema.HEADER)) {
			TransactionSource.migrate(connection, MigrationConfig.load(defaultProps), users, banks, billers, products, writer, new WarningCollector("Transactions"));
		}
		assertEquals(java.util.Set.of("9201", "9202"), readCsvById(defaultPath, TransactionSchema.HEADER, "id").keySet());

		config.override("transaction.months", "6");
		Path sixMonthsPath = tempDir.resolve("transaction_six_months.csv");
		try (CsvWriter writer = new CsvWriter(sixMonthsPath, TransactionSchema.HEADER)) {
			TransactionSource.migrate(connection, config, users, banks, billers, products, writer, new WarningCollector("Transactions"));
		}
		assertEquals(java.util.Set.of("9201", "9202", "9203", "9204"), readCsvById(sixMonthsPath, TransactionSchema.HEADER, "id").keySet());

		// Combines with the other filters: the date parameter binds after the status and customer ones.
		config.override("transaction.months", "3");
		config.override("customer.ids", "100");
		config.override("status.filter", "SUCCESSFUL");
		try (Statement st = connection.createStatement()) {
			st.execute("UPDATE dbo.Transactions SET status = 'SUCCESSFUL' WHERE id IN (9201, 9203)");
		}
		Path combinedPath = tempDir.resolve("transaction_combined_filters.csv");
		try (CsvWriter writer = new CsvWriter(combinedPath, TransactionSchema.HEADER)) {
			TransactionSource.migrate(connection, config, users, banks, billers, products, writer, new WarningCollector("Transactions"));
		}
		assertEquals(java.util.Set.of("9201"), readCsvById(combinedPath, TransactionSchema.HEADER, "id").keySet());

		config.override("transaction.months", "-1");
		assertThrows(IllegalArgumentException.class, () -> config.transactionMonths());
	}

	@Test
	void filtersEveryOutputByCustomerIdsWhenRequested() throws Exception {
		UsersRepository users = UsersRepository.load(connection, config.usersTable());
		BanksRepository banks = BanksRepository.load(connection, config);
		BankBranchesRepository branches = BankBranchesRepository.load(connection, config.bankBranchesTable());
		BillersRepository billers = BillersRepository.load(connection, config);
		ProductsRepository products = ProductsRepository.load(connection, config);
		DevicesRepository devices = DevicesRepository.load(connection, config.devicesTable());
		CountryPartnerRepository countries = CountryPartnerRepository.load(connection, config);
		String runTimestamp = "2026-07-20 10:00:00.000";

		config.override("customer.ids", "100");

		// TransferBeneficiaries: only customer_id=100 rows (ids 8, 9, 10, 15) - the other 11 rows
		// (customer_id 200/250/300/999) are excluded.
		Path beneficiaryPath = tempDir.resolve("beneficiary_filtered.csv");
		int transferCount;
		int paymentCount;
		try (CsvWriter writer = new CsvWriter(beneficiaryPath, BeneficiaryDetailsSchema.HEADER)) {
			transferCount = TransferBeneficiarySource.migrate(connection, config, users, banks, branches, writer, runTimestamp, new WarningCollector("TransferBeneficiaries"));
			// PaymentBeneficiaries: only customer_id=100 rows (503, 504, 508, 514, 515, 518, 520).
			paymentCount = PaymentBeneficiarySource.migrate(connection, config, users, billers, products, writer, runTimestamp, new WarningCollector("PaymentBeneficiaries"));
		}
		assertEquals(4, transferCount);
		assertEquals(7, paymentCount);
		Map<String, CSVRecord> beneficiaryById = readCsvById(beneficiaryPath, BeneficiaryDetailsSchema.HEADER, "beneficiary_id");
		assertTrue(beneficiaryById.containsKey("8"));
		assertFalse(beneficiaryById.containsKey("1"), "customer_id=200 row must be excluded");
		assertFalse(beneficiaryById.containsKey("4"), "customer_id=250 row must be excluded");
		assertFalse(beneficiaryById.containsKey("11"), "customer_id=300 row must be excluded");

		// customer_info (Users): only the id=100 row.
		Path customerInfoPath = tempDir.resolve("customer_info_filtered.csv");
		int customerCount;
		try (CsvWriter writer = new CsvWriter(customerInfoPath, CustomerInfoSchema.HEADER)) {
			customerCount = CustomerInfoSource.migrate(config, users, branches, devices, countries, writer, runTimestamp, new WarningCollector("Users"));
		}
		assertEquals(1, customerCount);
		assertTrue(readCsvById(customerInfoPath, CustomerInfoSchema.HEADER, "id").containsKey("100"));

		// Transactions: every fixture row except 9023 (customer_id=999, the unresolved-customer
		// warning case) is customer_id=100, so 29 of the 30 rows come through.
		Path transactionPath = tempDir.resolve("transaction_filtered.csv");
		int transactionCount;
		try (CsvWriter writer = new CsvWriter(transactionPath, TransactionSchema.HEADER)) {
			transactionCount = TransactionSource.migrate(connection, config, users, banks, billers, products, writer, new WarningCollector("Transactions"));
		}
		assertEquals(29, transactionCount);
		assertFalse(readCsvById(transactionPath, TransactionSchema.HEADER, "id").containsKey("9023"), "customer_id=999 row must be excluded");

		// Filtering to a customer_id with no Transactions rows at all -> zero rows written.
		config.override("customer.ids", "12345");
		Path transactionNonePath = tempDir.resolve("transaction_filtered_none.csv");
		int noMatchCount;
		try (CsvWriter writer = new CsvWriter(transactionNonePath, TransactionSchema.HEADER)) {
			noMatchCount = TransactionSource.migrate(connection, config, users, banks, billers, products, writer, new WarningCollector("Transactions"));
		}
		assertEquals(0, noMatchCount);

		// Blank filter -> every record, same as if --customer-ids had never been passed.
		config.override("customer.ids", "");
		Path beneficiaryUnfilteredPath = tempDir.resolve("beneficiary_unfiltered.csv");
		int transferCountUnfiltered;
		try (CsvWriter writer = new CsvWriter(beneficiaryUnfilteredPath, BeneficiaryDetailsSchema.HEADER)) {
			transferCountUnfiltered = TransferBeneficiarySource.migrate(connection, config, users, banks, branches, writer, runTimestamp, new WarningCollector("TransferBeneficiaries"));
		}
		assertEquals(15, transferCountUnfiltered);
	}

	@Test
	void resolvesUserNamesAndPerMigrationFiltersForCustomerIds() throws Exception {
		UsersRepository users = UsersRepository.load(connection, config.usersTable());
		BanksRepository banks = BanksRepository.load(connection, config);
		BankBranchesRepository branches = BankBranchesRepository.load(connection, config.bankBranchesTable());
		BillersRepository billers = BillersRepository.load(connection, config);
		ProductsRepository products = ProductsRepository.load(connection, config);
		String runTimestamp = "2026-07-20 10:00:00.000";

		// "R000100" is Users.userName for id=100 - resolves to the same rows as filtering by the
		// raw numeric id (see filtersEveryOutputByCustomerIdsWhenRequested's transferCount=4 for "100").
		config.override("customer.ids", "R000100");
		Path userNamePath = tempDir.resolve("beneficiary_username_filtered.csv");
		int transferCountByUserName;
		try (CsvWriter writer = new CsvWriter(userNamePath, BeneficiaryDetailsSchema.HEADER)) {
			transferCountByUserName = TransferBeneficiarySource.migrate(connection, config, users, banks, branches, writer, runTimestamp, new WarningCollector("TransferBeneficiaries"));
		}
		assertEquals(4, transferCountByUserName);

		// A raw numeric id and a userName in the same list resolve together: customer_id=200 rows
		// (1, 2, 3) + customer_id=100 rows (8, 9, 10, 15) = 7.
		config.override("customer.ids", "200,R000100");
		Path mixedPath = tempDir.resolve("beneficiary_mixed_filtered.csv");
		int transferCountMixed;
		try (CsvWriter writer = new CsvWriter(mixedPath, BeneficiaryDetailsSchema.HEADER)) {
			transferCountMixed = TransferBeneficiarySource.migrate(connection, config, users, banks, branches, writer, runTimestamp, new WarningCollector("TransferBeneficiaries"));
		}
		assertEquals(7, transferCountMixed);

		// An unresolvable userName fails the run with a clear error instead of silently migrating
		// everyone (dropping the filter) or every customer_id column value literally (it isn't one).
		config.override("customer.ids", "NOT-A-REAL-USER");
		Path badFilterPath = tempDir.resolve("beneficiary_bad_filter.csv");
		try (CsvWriter writer = new CsvWriter(badFilterPath, BeneficiaryDetailsSchema.HEADER)) {
			IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
					() -> TransferBeneficiarySource.migrate(connection, config, users, banks, branches, writer, runTimestamp, new WarningCollector("TransferBeneficiaries")));
			assertTrue(ex.getMessage().contains("NOT-A-REAL-USER"), "error should name the bad token: " + ex.getMessage());
		}

		// --beneficiary-customer-ids / --transaction-customer-ids scope independently of
		// --customer-ids, and fall back to it when not set.
		config.override("customer.ids", "100");
		config.override("beneficiary.customer.ids", "200");
		Path scopedBeneficiaryPath = tempDir.resolve("beneficiary_scoped.csv");
		int transferCountScoped;
		try (CsvWriter writer = new CsvWriter(scopedBeneficiaryPath, BeneficiaryDetailsSchema.HEADER)) {
			// beneficiary.customer.ids=200 overrides customer.ids=100 for this migration only.
			transferCountScoped = TransferBeneficiarySource.migrate(connection, config, users, banks, branches, writer, runTimestamp, new WarningCollector("TransferBeneficiaries"));
		}
		assertEquals(3, transferCountScoped, "customer_id=200 rows (1, 2, 3), not customer_id=100's");

		Path scopedTransactionPath = tempDir.resolve("transaction_scoped.csv");
		int transactionCountFallback;
		try (CsvWriter writer = new CsvWriter(scopedTransactionPath, TransactionSchema.HEADER)) {
			// transaction.customer.ids was never set, so this still falls back to customer.ids=100.
			transactionCountFallback = TransactionSource.migrate(connection, config, users, banks, billers, products, writer, new WarningCollector("Transactions"));
		}
		assertEquals(29, transactionCountFallback, "falls back to customer.ids=100, same as filtersEveryOutputByCustomerIdsWhenRequested");
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
				st.execute("CREATE TABLE dbo.Banks (\n"
						+ "  id BIGINT, code VARCHAR(20), deleted BOOLEAN, enabled BOOLEAN, gatewayCode VARCHAR(20),\n"
						+ "  name VARCHAR(100), processor VARCHAR(20), country_id BIGINT, routingNumber VARCHAR(20),\n"
						+ "  bankIBRefNumber VARCHAR(20), branchCode VARCHAR(20), hasBranches BOOLEAN,\n"
						+ "  allowsInstantTransfer BOOLEAN, institution VARCHAR(20), shortName VARCHAR(100)\n"
						+ ")");
				st.execute("INSERT INTO dbo.Banks VALUES\n"
						+ "  (1, 'B001', FALSE, TRUE, 'FMERMWMW', 'FIRST MERCHANT BANK', 'NATIVE', 1, 'FMERMWMW', NULL, NULL, FALSE, FALSE, 'BANK', 'FIRST MERCHANT BANK'),\n"
						+ "  (114, 'ZM-NFS-AIRTEL', FALSE, TRUE, NULL, 'AIRTEL', 'NATIVE', 4, NULL, NULL, NULL, TRUE, TRUE, 'WALLET', 'AIRTEL')\n");
			}

			Path propsFile = tempDir.resolve("banks-defaults.properties");
			Files.write(propsFile, ("db.url=" + conn.getMetaData().getURL() + "\n").getBytes(StandardCharsets.UTF_8));
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
