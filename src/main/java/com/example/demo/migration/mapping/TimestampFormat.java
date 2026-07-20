package com.example.demo.migration.mapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class TimestampFormat {

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

	private TimestampFormat() {
	}

	public static String format(LocalDateTime value) {
		return value == null ? null : value.format(FORMAT);
	}

	public static String now() {
		return format(LocalDateTime.now());
	}
}
