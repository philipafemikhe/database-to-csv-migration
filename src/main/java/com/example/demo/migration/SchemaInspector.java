package com.example.demo.migration;

import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.db.MigrationConfig;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * Backs {@code --describe-schema}: prints the actual columns (and a few sample rows) of the
 * Banks and CountryPartner tables, whose exact schema wasn't available while building this tool.
 * Run this against a real environment before a live migration to confirm/adjust the
 * banks.column.* and countryPartner.column.* overrides in the config file.
 *
 * This writes directly to stdout rather than through the logger: it's a one-shot diagnostic
 * report for a human to read, not a progress/event log.
 */
public final class SchemaInspector {

	private static final int SAMPLE_ROWS = 5;

	private SchemaInspector() {
	}

	public static void describe(Connection connection, MigrationConfig config) throws Exception {
		describeTable(connection, "Banks", config.banksTable());
		describeTable(connection, "CountryPartner", config.countryPartnerTable());
	}

	private static void describeTable(Connection connection, String label, String table) throws Exception {
		System.out.println("=== " + label + " (" + table + ") ===");
		String sql = "SELECT TOP " + SAMPLE_ROWS + " * FROM " + table;
		try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
			List<String> columns = JdbcUtils.columnNames(rs);
			System.out.println("Columns: " + columns);
			int shown = 0;
			while (rs.next() && shown < SAMPLE_ROWS) {
				StringBuilder row = new StringBuilder("  ");
				for (String column : columns) {
					row.append(column).append('=').append(rs.getString(column)).append(' ');
				}
				System.out.println(row);
				shown++;
			}
		} catch (Exception e) {
			System.out.println("  Could not query " + table + ": " + e.getMessage());
		}
		System.out.println();
	}
}
