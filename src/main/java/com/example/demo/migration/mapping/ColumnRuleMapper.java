package com.example.demo.migration.mapping;

import com.example.demo.migration.WarningCollector;

import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Applies a {@link ColumnRule} list to one source row. {@code resolvedColumns} maps each rule's
 * source column to the actual result-set column name, or to null when the source table doesn't
 * have that column (the target column is then written blank, or FALSE/0 per its handling).
 *
 * Copied values keep their source value, formatted the same way as the rest of this tool's
 * output: timestamps as {@link TimestampFormat}, dates as yyyy-MM-dd, bits as 1/0, numbers in
 * plain (never exponential) notation, strings trimmed.
 */
public final class ColumnRuleMapper {

	private ColumnRuleMapper() {
	}

	public static OutputRecord map(ResultSet rs, long rowId, ColumnRule[] rules, String[] header,
			Map<String, String> resolvedColumns, WarningCollector warnings) throws SQLException {
		OutputRecord record = new OutputRecord(header);
		for (ColumnRule rule : rules) {
			String column = resolvedColumns.get(rule.source());
			Object raw = column == null ? null : copyValue(rs.getObject(column));
			switch (rule.handling()) {
				case BOOLEAN:
					record.set(rule.target(), toBooleanFlag(raw, rowId, rule.source(), warnings));
					break;
				case DEFAULT_ZERO:
					record.set(rule.target(), raw == null ? "0" : raw);
					break;
				default:
					record.set(rule.target(), raw);
					break;
			}
		}
		return record;
	}

	/** The source value in output form, or null for NULL/blank. */
	private static Object copyValue(Object value) throws SQLException {
		if (value == null) {
			return null;
		}
		if (value instanceof Clob) {
			Clob clob = (Clob) value;
			value = clob.getSubString(1, (int) clob.length());
		}
		if (value instanceof String) {
			String s = ((String) value).trim();
			return s.isEmpty() || "NULL".equalsIgnoreCase(s) ? null : s;
		}
		if (value instanceof Timestamp) {
			return TimestampFormat.format(((Timestamp) value).toLocalDateTime());
		}
		if (value instanceof LocalDateTime) {
			return TimestampFormat.format((LocalDateTime) value);
		}
		if (value instanceof java.sql.Date) {
			return ((java.sql.Date) value).toLocalDate().toString();
		}
		if (value instanceof LocalDate) {
			return value.toString();
		}
		if (value instanceof Boolean) {
			return ((Boolean) value) ? "1" : "0";
		}
		return value;
	}

	/**
	 * 0 = FALSE, 1 = TRUE, blank = FALSE. Any other value can't be mapped faithfully, so it's
	 * written as TRUE (non-zero) when numeric, or kept as-is otherwise - and warned either way.
	 */
	private static String toBooleanFlag(Object value, long rowId, String column, WarningCollector warnings) {
		if (value == null) {
			return "FALSE";
		}
		String s = value instanceof BigDecimal ? ((BigDecimal) value).stripTrailingZeros().toPlainString() : String.valueOf(value).trim();
		if (s.equals("0") || s.equalsIgnoreCase("false")) {
			return "FALSE";
		}
		if (s.equals("1") || s.equalsIgnoreCase("true")) {
			return "TRUE";
		}
		if (value instanceof Number) {
			warnings.warn(rowId, column + " value " + s + " is not 0/1; written as TRUE");
			return "TRUE";
		}
		warnings.warn(rowId, column + " value '" + s + "' is not 0/1/blank; copied as-is");
		return s;
	}
}
