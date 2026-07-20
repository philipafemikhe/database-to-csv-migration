package com.example.demo.migration.mapping;

import java.util.LinkedHashMap;
import java.util.Map;

/** One output row, keyed by target column name. Unset columns default to "" when written. */
public final class OutputRecord {

	private final String[] header;
	private final Map<String, String> values = new LinkedHashMap<>();

	public OutputRecord(String[] header) {
		this.header = header;
	}

	public OutputRecord set(String column, Object value) {
		values.put(column, value == null ? "" : String.valueOf(value));
		return this;
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
