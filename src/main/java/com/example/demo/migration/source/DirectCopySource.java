package com.example.demo.migration.source;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.csv.RecordWriter;
import com.example.demo.migration.db.CustomerIdFilter;
import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.mapping.ColumnRule;
import com.example.demo.migration.mapping.ColumnRuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Streams a source table through a {@link ColumnRule} list - backs the column-for-column Users,
 * Devices and Old_Ussd_Account migrations. Selects {@code *} and matches each rule's source column
 * case-insensitively, so a column the mapping sheet lists but the table doesn't have (e.g. a
 * column added after the table scripts were taken) is logged once and written blank/FALSE/0
 * instead of failing the run.
 */
public final class DirectCopySource {

	private static final Logger log = LoggerFactory.getLogger(DirectCopySource.class);
	private static final int PROGRESS_INTERVAL = 5000;

	private DirectCopySource() {
	}

	/**
	 * @param filterColumn the column {@code filterIds} restricts (e.g. "id" or "customer_id"), or null
	 * @param filterIds    ids to restrict to; empty means every row
	 * @param exclusion    an extra SQL condition every row must satisfy (e.g. not owned by a disabled user), or null
	 */
	public static int migrate(Connection connection, String label, String table, ColumnRule[] rules, String[] header,
			String filterColumn, List<Long> filterIds, String exclusion, Integer limit, RecordWriter writer,
			WarningCollector warnings) throws SQLException, IOException {

		String top = limit == null ? "" : "TOP (" + limit + ") ";
		boolean filtered = filterColumn != null && !filterIds.isEmpty();
		List<String> conditions = new ArrayList<>();
		if (filtered) {
			conditions.add(filterColumn + " IN " + CustomerIdFilter.inClausePlaceholders(filterIds.size()));
		}
		if (exclusion != null) {
			conditions.add(exclusion);
		}
		String sql = "SELECT " + top + "* FROM " + table
				+ (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions));

		long start = System.currentTimeMillis();
		int count = 0;
		try (PreparedStatement st = connection.prepareStatement(sql)) {
			st.setFetchSize(1000);
			if (filtered) {
				for (int i = 0; i < filterIds.size(); i++) {
					st.setLong(i + 1, filterIds.get(i));
				}
			}
			try (ResultSet rs = st.executeQuery()) {
				Map<String, String> resolved = resolveColumns(JdbcUtils.columnNames(rs), rules, label, table);
				String idColumn = resolved.get("id");
				while (rs.next()) {
					long rowId = idColumn == null ? count + 1 : rs.getLong(idColumn);
					writer.write(ColumnRuleMapper.map(rs, rowId, rules, header, resolved, warnings));
					count++;
					if (count % PROGRESS_INTERVAL == 0) {
						log.info("{}: {} rows migrated so far...", label, count);
					}
				}
			}
		}
		log.info("{}: completed, {} rows in {} ms ({} warning(s))", label, count, System.currentTimeMillis() - start, warnings.size());
		return count;
	}

	private static Map<String, String> resolveColumns(List<String> actual, ColumnRule[] rules, String label, String table) {
		Map<String, String> resolved = new HashMap<>();
		List<String> missing = new ArrayList<>();
		for (ColumnRule rule : rules) {
			String column = JdbcUtils.resolveColumn(actual, rule.source());
			if (column == null) {
				missing.add(rule.source());
			}
			resolved.put(rule.source(), column);
		}
		if (!missing.isEmpty()) {
			log.warn("{}: {} has no column(s) {}; their target columns are written blank (FALSE for booleans, 0 for defaulted)",
					label, table, missing);
		}
		return resolved;
	}
}
