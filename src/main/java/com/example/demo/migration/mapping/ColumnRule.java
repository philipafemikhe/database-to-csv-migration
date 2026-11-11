package com.example.demo.migration.mapping;

/**
 * One row of a one-to-one "Old MB column -> New MB column" mapping sheet
 * (MobileBanking_Users_Devices_OldUSSDAccount_Mapping.xlsx), applied by {@link ColumnRuleMapper}.
 */
public final class ColumnRule {

	/** The sheet's "Handling" column. */
	public enum Handling {
		/**
		 * "same value" / "remove exponential value for numeric values": copied as-is. Numbers are
		 * never written in exponential notation (OutputRecord renders every value plain), so the
		 * exponential-removal columns need no extra handling beyond this.
		 */
		COPY,
		/** "boolean value 0 = FALSE , 1 = TRUE , Blank = FALSE". */
		BOOLEAN,
		/** "default value is 0": copied as-is, with a NULL/blank written as 0. */
		DEFAULT_ZERO
	}

	private final String source;
	private final String target;
	private final Handling handling;

	private ColumnRule(String source, String target, Handling handling) {
		this.source = source;
		this.target = target;
		this.handling = handling;
	}

	public static ColumnRule copy(String source, String target) {
		return new ColumnRule(source, target, Handling.COPY);
	}

	public static ColumnRule bool(String source, String target) {
		return new ColumnRule(source, target, Handling.BOOLEAN);
	}

	public static ColumnRule defaultZero(String source, String target) {
		return new ColumnRule(source, target, Handling.DEFAULT_ZERO);
	}

	public static String[] header(ColumnRule[] rules) {
		String[] header = new String[rules.length];
		for (int i = 0; i < rules.length; i++) {
			header[i] = rules[i].target;
		}
		return header;
	}

	public String source() {
		return source;
	}

	public String target() {
		return target;
	}

	public Handling handling() {
		return handling;
	}
}
