package com.example.demo.migration.db;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class JdbcUtils {

	private JdbcUtils() {
	}

	public static Long getLong(ResultSet rs, String column) throws SQLException {
		long v = rs.getLong(column);
		return rs.wasNull() ? null : v;
	}

	public static String getString(ResultSet rs, String column) throws SQLException {
		String v = rs.getString(column);
		if (v == null) {
			return null;
		}
		v = v.trim();
		if (v.isEmpty() || "NULL".equalsIgnoreCase(v)) {
			return null;
		}
		return v;
	}

	public static Boolean getBoolean(ResultSet rs, String column) throws SQLException {
		boolean v = rs.getBoolean(column);
		return rs.wasNull() ? null : v;
	}

	public static LocalDateTime getTimestamp(ResultSet rs, String column) throws SQLException {
		Timestamp v = rs.getTimestamp(column);
		return v == null ? null : v.toLocalDateTime();
	}

	public static List<String> columnNames(ResultSet rs) throws SQLException {
		ResultSetMetaData md = rs.getMetaData();
		List<String> names = new ArrayList<>();
		for (int i = 1; i <= md.getColumnCount(); i++) {
			names.add(md.getColumnLabel(i));
		}
		return names;
	}

	/**
	 * Case-insensitive lookup of a column name against the result set's actual columns,
	 * used for tables whose exact schema isn't known ahead of time (e.g. Banks, CountryPartner).
	 */
	public static String resolveColumn(List<String> actualColumns, String preferred, String... fallbacks) {
		String resolved = findIgnoreCase(actualColumns, preferred);
		if (resolved != null) {
			return resolved;
		}
		for (String fallback : fallbacks) {
			resolved = findIgnoreCase(actualColumns, fallback);
			if (resolved != null) {
				return resolved;
			}
		}
		return null;
	}

	private static String findIgnoreCase(List<String> actualColumns, String name) {
		if (name == null) {
			return null;
		}
		for (String c : actualColumns) {
			if (c.equalsIgnoreCase(name)) {
				return c;
			}
		}
		return null;
	}
}
