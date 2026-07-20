package com.example.demo.migration.model;

/** {@code label} is whatever text column (name/code) identifies the country, used for business-rule matching. */
public record CountryPartnerRecord(long id, String label) {
}
