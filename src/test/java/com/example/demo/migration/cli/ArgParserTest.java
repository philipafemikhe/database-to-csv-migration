package com.example.demo.migration.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgParserTest {

	@Test
	void runsEveryMigrationWhenNoSkipFlagIsGiven() {
		CliOptions options = ArgParser.parse(new String[] {"--config=migration.properties"});
		assertFalse(options.skipBeneficiaries());
		assertFalse(options.skipCustomers());
		assertFalse(options.skipTransactions());
		assertFalse(options.skipUsers());
		assertFalse(options.skipDevices());
		assertFalse(options.skipOldUssdAccounts());
	}

	@Test
	void eachSkipFlagOnlySkipsItsOwnMigration() {
		CliOptions users = ArgParser.parse(new String[] {"--skip-users"});
		assertTrue(users.skipUsers());
		assertFalse(users.skipDevices());
		assertFalse(users.skipOldUssdAccounts());
		assertFalse(users.skipCustomers(), "--skip-users must not skip customer_info");

		CliOptions devices = ArgParser.parse(new String[] {"--skip-devices"});
		assertTrue(devices.skipDevices());
		assertFalse(devices.skipUsers());
		assertFalse(devices.skipOldUssdAccounts());

		CliOptions oldUssd = ArgParser.parse(new String[] {"--skip-old-ussd-accounts"});
		assertTrue(oldUssd.skipOldUssdAccounts());
		assertFalse(oldUssd.skipUsers());
		assertFalse(oldUssd.skipDevices());

		CliOptions customers = ArgParser.parse(new String[] {"--skip-customers"});
		assertTrue(customers.skipCustomers());
		assertFalse(customers.skipUsers(), "--skip-customers must not skip users");
	}

	@Test
	void allThreeNewMigrationsCanBeSkippedTogether() {
		CliOptions options = ArgParser.parse(new String[] {"--skip-users", "--skip-devices", "--skip-old-ussd-accounts"});
		assertTrue(options.skipUsers());
		assertTrue(options.skipDevices());
		assertTrue(options.skipOldUssdAccounts());
		assertFalse(options.skipBeneficiaries());
		assertFalse(options.skipCustomers());
		assertFalse(options.skipTransactions());
	}
}
