package com.example.demo.migration;

import com.example.demo.migration.cli.ArgParser;
import com.example.demo.migration.cli.CliOptions;
import com.example.demo.migration.db.ConnectionFactory;
import com.example.demo.migration.db.MigrationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.sql.Connection;

/**
 * Entrypoint only: parse args, load config, open the DB connection, and hand off to
 * {@link SchemaInspector} (--describe-schema) or {@link MigrationRunner} (the actual migration).
 * All orchestration logic lives in those two classes, not here.
 *
 * Migrates the old SQL Server schema into the new-model CSVs:
 *   - dbo.TransferBeneficiaries + dbo.PaymentBeneficiaries -> beneficiary_details.csv
 *   - dbo.Users -> customer_info.csv
 *   - dbo.Transactions -> transaction.csv
 *
 * Usage:
 *   java -jar beneficiary-migration.jar --config=migration.properties [options]
 *   java -jar beneficiary-migration.jar --config=migration.properties --describe-schema
 */
public final class MigrationCli {

	private static final Logger log = LoggerFactory.getLogger(MigrationCli.class);

	public static void main(String[] args) {
		CliOptions options = ArgParser.parse(args);

		if (options.help()) {
			printUsage();
			return;
		}

		if (!options.configPath().isPresent()) {
			log.error("Missing required --config=<path/to/migration.properties>");
			printUsage();
			System.exit(1);
			return;
		}

		long start = System.currentTimeMillis();
		try {
			MigrationConfig config = MigrationConfig.load(Paths.get(options.configPath().get()));
			options.output().ifPresent(v -> config.override("output", v));
			options.outputCustomers().ifPresent(v -> config.override("output.customerInfo", v));
			options.outputTransactions().ifPresent(v -> config.override("output.transactions", v));
			options.limit().ifPresent(v -> config.override("row.limit", v));

			try (Connection connection = ConnectionFactory.open(config)) {
				if (options.describeSchema()) {
					SchemaInspector.describe(connection, config);
				} else {
					new MigrationRunner(connection, config).run(!options.skipBeneficiaries(), !options.skipCustomers(), !options.skipTransactions(), options.splitByCountry());
				}
			}
			log.info("Done in {} ms", System.currentTimeMillis() - start);
		} catch (Exception e) {
			log.error("Migration failed after {} ms: {}", System.currentTimeMillis() - start, e.getMessage(), e);
			System.exit(1);
		}
	}

	private static void printUsage() {
		System.out.println(
				"Usage:\n"
				+ "  java -jar beneficiary-migration.jar --config=migration.properties [options]\n"
				+ "  java -jar beneficiary-migration.jar --config=migration.properties --describe-schema\n"
				+ "\n"
				+ "  --config              Path to a properties file with db.url/db.username/db.password and other settings.\n"
				+ "                         See migration.properties.example for the full list.\n"
				+ "  --output               Overrides 'output': where to write beneficiary_details.csv.\n"
				+ "  --output-customers      Overrides 'output.customerInfo': where to write customer_info.csv.\n"
				+ "  --output-transactions   Overrides 'output.transactions': where to write transaction.csv.\n"
				+ "  --limit                Overrides 'row.limit': cap the number of rows read per source table (testing).\n"
				+ "  --skip-beneficiaries    Don't migrate TransferBeneficiaries/PaymentBeneficiaries this run.\n"
				+ "  --skip-customers        Don't migrate Users this run.\n"
				+ "  --skip-transactions     Don't migrate Transactions this run.\n"
				+ "  --split-by-country      Write beneficiary_details and transaction as one file per country (e.g.\n"
				+ "                         beneficiary_details_migrated_MW.csv) instead of a single merged file. Rows\n"
				+ "                         with no resolvable country go to a _UNKNOWN file. customer_info is never\n"
				+ "                         split (it's grouped by a different, dynamic country lookup).\n"
				+ "  --describe-schema       Prints the columns found in the Banks, CountryPartner, and Billers tables, then\n"
				+ "                         exits, without running the migration. Use this first to confirm/adjust the\n"
				+ "                         banks.column.*, countryPartner.column.*, and billers.column.* overrides in\n"
				+ "                         your config file.\n");
	}
}
