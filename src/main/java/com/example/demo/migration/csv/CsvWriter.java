package com.example.demo.migration.csv;

import com.example.demo.migration.mapping.OutputRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CsvWriter implements AutoCloseable {

	private final String[] header;
	private final CSVPrinter printer;

	public CsvWriter(Path outputPath, String[] header) throws IOException {
		this.header = header;
		BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
		CSVFormat format = CSVFormat.DEFAULT.builder()
				.setHeader(header)
				.setQuoteMode(QuoteMode.MINIMAL)
				.build();
		this.printer = new CSVPrinter(writer, format);
	}

	public void write(OutputRecord record) throws IOException {
		if (record.header() != header) {
			throw new IllegalArgumentException("Record schema does not match this writer's header - wrong mapper/writer paired together");
		}
		printer.printRecord((Object[]) record.toRow());
	}

	@Override
	public void close() throws IOException {
		printer.close();
	}
}
