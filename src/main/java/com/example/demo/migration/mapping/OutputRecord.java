package com.example.demo.migration.mapping;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** One output row, keyed by target column name. Unset columns default to "" when written. */
public final class OutputRecord {

	/**
	 * Canonical scientific notation as emitted by Excel/Java (e.g. 2.63771234567E+11, 1E+11). Requires a
	 * mantissa decimal point or an explicit exponent sign so short alphanumeric codes like "12E4" are left alone.
	 */
	private static final Pattern SCIENTIFIC = Pattern.compile("-?\\d(?:\\.\\d+[eE][+-]?|[eE][+-])\\d+");

	private final String[] header;
	private final Map<String, String> values = new LinkedHashMap<>();

	public OutputRecord(String[] header) {
		this.header = header;
	}

	public OutputRecord set(String column, Object value) {
		values.put(column, format(value));
		return this;
	}

	/** Renders numbers in plain notation — never exponential (e.g. 1000 rather than 1E+3). */
	private static String format(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof BigDecimal) {
			return ((BigDecimal) value).toPlainString();
		}
		if ((value instanceof Double || value instanceof Float) && Double.isFinite(((Number) value).doubleValue())) {
			return new BigDecimal(value.toString()).toPlainString();
		}
		String s = String.valueOf(value);
		if (SCIENTIFIC.matcher(s).matches()) {
			return new BigDecimal(s).toPlainString();
		}
		return s;
	}

	/** The value previously set for this column, or null if it was never set. */
	public String get(String column) {
		return values.get(column);
	}

	public String[] header() {
		return header;
	}

	public String[] toRow() {
		String[] row = new String[header.length];
		for (int i = 0; i < header.length; i++) {
			row[i] = values.getOrDefault(header[i], "");
		}
		return row;
	}
}
