package com.example.demo.migration.model;

/** {@code label} is whatever text column (name/code) identifies the country, used for business-rule matching. */
public final class CountryPartnerRecord {

	private final long id;
	private final String label;

	public CountryPartnerRecord(long id, String label) {
		this.id = id;
		this.label = label;
	}

	public long id() {
		return id;
	}

	public String label() {
		return label;
	}
}
