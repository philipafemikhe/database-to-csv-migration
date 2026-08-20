package com.example.demo.migration.csv;

import com.example.demo.migration.mapping.OutputRecord;

import java.io.IOException;

/** Common surface for {@link CsvWriter} (one merged file) and {@link PerCountryCsvWriter} (one file per country). */
public interface RecordWriter extends AutoCloseable {

	void write(OutputRecord record) throws IOException;

	@Override
	void close() throws IOException;
}
