package com.example.demo.migration.mapping;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Old Users.defaultLanguage stores full names (e.g. "ENGLISH"); the target expects short codes. */
public final class LanguageCode {

	private static final Map<String, String> KNOWN = buildKnown();

	private static Map<String, String> buildKnown() {
		Map<String, String> known = new HashMap<>();
		known.put("ENGLISH", "EN");
		known.put("FRENCH", "FR");
		known.put("PORTUGUESE", "PT");
		known.put("SHONA", "SN");
		known.put("NDEBELE", "ND");
		known.put("CHICHEWA", "NY");
		return Collections.unmodifiableMap(known);
	}

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
