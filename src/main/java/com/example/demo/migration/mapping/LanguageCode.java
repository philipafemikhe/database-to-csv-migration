package com.example.demo.migration.mapping;

import java.util.Locale;
import java.util.Map;

/** Old Users.defaultLanguage stores full names (e.g. "ENGLISH"); the target expects short codes. */
public final class LanguageCode {

	private static final Map<String, String> KNOWN = Map.of(
			"ENGLISH", "EN",
			"FRENCH", "FR",
			"PORTUGUESE", "PT",
			"SHONA", "SN",
			"NDEBELE", "ND",
			"CHICHEWA", "NY");

	private LanguageCode() {
	}

	/** Returns the short code for a known full name, or the original value unchanged otherwise. */
	public static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String code = KNOWN.get(value.trim().toUpperCase(Locale.ROOT));
		return code != null ? code : value;
	}
}
