package com.example.demo.migration;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record ValidationWarning(String source, long oldId, String message) {

	@Override
	public String toString() {
		return source + " id=" + oldId + ": " + message;
	}

	/**
	 * Writes one warning per line to "<output's base name>.warnings.log", next to the given
	 * output file, and returns the path written to.
	 */
	public static Path writeLog(Path outputPath, List<ValidationWarning> warnings) throws IOException {
		String fileName = outputPath.getFileName().toString();
		String base = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
		Path warningsPath = outputPath.resolveSibling(base + ".warnings.log");
		try (BufferedWriter w = Files.newBufferedWriter(warningsPath, StandardCharsets.UTF_8)) {
			for (ValidationWarning warning : warnings) {
				w.write(warning.toString());
				w.newLine();
			}
		}
		return warningsPath;
	}
}
