package com.example.demo.migration.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Connection and behavior settings, loaded from a properties file (--config=path).
 * Individual values may be overridden from the command line.
 *
 * Expected properties - see migration.properties.example for the full, documented list:
 *   db.url, db.username, db.password, db.driver
 *   output, output.customerInfo, payment.id.offset, row.limit
 *   users.table, transferBeneficiaries.table, paymentBeneficiaries.table, bankBranches.table,
 *     devices.table
 *   banks.table, banks.column.id, banks.column.name, banks.column.swiftCode,
 *     banks.column.institution, banks.wallet.marker
 *   countryPartner.table, countryPartner.column.id, countryPartner.column.name
 */
public final class MigrationConfig {

	private final Properties props;

	private MigrationConfig(Properties props) {
		this.props = props;
	}

	public static MigrationConfig load(Path path) throws IOException {
		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(path)) {
			props.load(in);
		}
		return new MigrationConfig(props);
	}

	public void override(String key, String value) {
		if (value != null) {
			props.setProperty(key, value);
		}
	}

	private String get(String key, String defaultValue) {
		return props.getProperty(key, defaultValue);
	}

	private String require(String key) {
		String v = props.getProperty(key);
		if (v == null || v.isBlank()) {
			throw new IllegalStateException("Missing required config property: " + key);
		}
		return v;
	}

	public String dbUrl() {
		return require("db.url");
	}

	public String dbUsername() {
		return get("db.username", null);
	}

	public String dbPassword() {
		return get("db.password", null);
	}

	public String dbDriver() {
		return get("db.driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
	}

	public String outputPath() {
		return get("output", "beneficiary_details_migrated.csv");
	}

	public String customerInfoOutputPath() {
		return get("output.customerInfo", "customer_info_migrated.csv");
	}

	public long paymentBeneficiaryIdOffset() {
		return Long.parseLong(get("payment.id.offset", "900000000"));
	}

	public Integer rowLimit() {
		String v = get("row.limit", null);
		return v == null ? null : Integer.parseInt(v);
	}

	public String usersTable() {
		return get("users.table", "dbo.Users");
	}

	public String transferBeneficiariesTable() {
		return get("transferBeneficiaries.table", "dbo.TransferBeneficiaries");
	}

	public String paymentBeneficiariesTable() {
		return get("paymentBeneficiaries.table", "dbo.PaymentBeneficiaries");
	}

	public String devicesTable() {
		return get("devices.table", "dbo.Devices");
	}

	public String bankBranchesTable() {
		return get("bankBranches.table", "dbo.BankBranches");
	}

	public String banksTable() {
		return get("banks.table", "dbo.Banks");
	}

	public String banksColumnId() {
		return get("banks.column.id", "id");
	}

	public String banksColumnName() {
		return get("banks.column.name", "name");
	}

	public String banksColumnSwiftCode() {
		// The Banks export we've seen has no dedicated swiftCode column: the SWIFT/BIC-format
		// value (e.g. "FMERMWMW", "BKIDBWGX") lives in gatewayCode (duplicated in routingNumber).
		return get("banks.column.swiftCode", "gatewayCode");
	}

	public String banksColumnInstitution() {
		return get("banks.column.institution", "institution");
	}

	public String banksWalletMarker() {
		return get("banks.wallet.marker", "WALLET");
	}

	public String countryPartnerTable() {
		return get("countryPartner.table", "dbo.CountryPartner");
	}

	public String countryPartnerColumnId() {
		return get("countryPartner.column.id", "id");
	}

	public String countryPartnerColumnName() {
		// customer_info.country_code and beneficiary rules both want a short code (e.g. "BW",
		// "ZW"), not the full country name - prefer a "code" column over "name".
		return get("countryPartner.column.name", "code");
	}
}
