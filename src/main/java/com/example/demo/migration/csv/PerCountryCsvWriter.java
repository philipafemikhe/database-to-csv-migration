package com.example.demo.migration.csv;

import com.example.demo.migration.mapping.OutputRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Routes each row to a sibling file named after its country code (e.g. base
 * "beneficiary_details_migrated.csv" + code "MW" -> "beneficiary_details_migrated_MW.csv"),
 * opening one {@link CsvWriter} per code lazily on first write. Rows whose country code is
 * blank/unresolved go to a "UNKNOWN" file so row counts still reconcile against the total.
 * The country code is either read from a column of the row itself, or derived from the row by a
 * function (for outputs that don't carry a country code column, e.g. users/devices).
 */
public final class PerCountryCsvWriter implements RecordWriter {

	private static final String UNKNOWN_CODE = "UNKNOWN";

	private final Path basePath;
	private final String[] header;
	private final Function<OutputRecord, String> countryOf;
	private final Map<String, CsvWriter> writers = new LinkedHashMap<>();

	public PerCountryCsvWriter(Path basePath, String[] header, String countryColumn) {
		this(basePath, header, record -> record.get(countryColumn));
	}

	public PerCountryCsvWriter(Path basePath, String[] header, Function<OutputRecord, String> countryOf) {
		this.basePath = basePath;
		this.header = header;
		this.countryOf = countryOf;
	}

	@Override
	public void write(OutputRecord record) throws IOException {
		String value = countryOf.apply(record);
		String code = value == null || value.trim().isEmpty() ? UNKNOWN_CODE : value;
		CsvWriter writer = writers.get(code);
		if (writer == null) {
			writer = new CsvWriter(pathFor(code), header);
			writers.put(code, writer);
		}
		writer.write(record);
	}

	/** Country code -> the file actually written for it, once writing is done. */
	public Map<String, Path> writtenFiles() {
		Map<String, Path> result = new LinkedHashMap<>();
		for (String code : writers.keySet()) {
			result.put(code, pathFor(code));
		}
		return result;
	}

	private Path pathFor(String code) {
		String fileName = basePath.getFileName().toString();
		int dot = fileName.lastIndexOf('.');
		String withSuffix = dot < 0 ? fileName + "_" + code : fileName.substring(0, dot) + "_" + code + fileName.substring(dot);
		return basePath.resolveSibling(withSuffix);
	}

	@Override
	public void close() throws IOException {
		IOException first = null;
		for (CsvWriter writer : writers.values()) {
			try {
				writer.close();
			} catch (IOException e) {
				if (first == null) {
					first = e;
				}
			}
		}
		if (first != null) {
			throw first;
		}
	}
}
