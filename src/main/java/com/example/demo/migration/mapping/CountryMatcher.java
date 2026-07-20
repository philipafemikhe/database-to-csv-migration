package com.example.demo.migration.mapping;

import java.util.Locale;
import java.util.Set;

/** Matches a CountryPartner label (name or code, schema unknown) against a country's known aliases. */
public final class CountryMatcher {

	private static final Set<String> ZAMBIA = Set.of("zambia", "zm", "zmw");
	private static final Set<String> BOTSWANA = Set.of("botswana", "bw", "bwp");

	private CountryMatcher() {
	}

	public static boolean isZambia(String label) {
		return matches(label, ZAMBIA);
	}

	public static boolean isBotswana(String label) {
		return matches(label, BOTSWANA);
	}

	private static boolean matches(String label, Set<String> aliases) {
		if (label == null) {
			return false;
		}
		String norm = label.trim().toLowerCase(Locale.ROOT);
		if (aliases.contains(norm)) {
			return true;
		}
		for (String alias : aliases) {
			if (alias.length() > 2 && norm.contains(alias)) {
				return true;
			}
		}
		return false;
	}
}
