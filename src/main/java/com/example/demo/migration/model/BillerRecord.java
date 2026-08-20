package com.example.demo.migration.model;

public final class BillerRecord {

	private final long id;
	private final String gatewayCode;

	public BillerRecord(long id, String gatewayCode) {
		this.id = id;
		this.gatewayCode = gatewayCode;
	}

	public long id() {
		return id;
	}

	public String gatewayCode() {
		return gatewayCode;
	}
}
