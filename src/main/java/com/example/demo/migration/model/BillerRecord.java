package com.example.demo.migration.model;

public final class BillerRecord {

	private final long id;
	private final String gatewayCode;
	private final String name;

	public BillerRecord(long id, String gatewayCode, String name) {
		this.id = id;
		this.gatewayCode = gatewayCode;
		this.name = name;
	}

	public long id() {
		return id;
	}

	public String gatewayCode() {
		return gatewayCode;
	}

	public String name() {
		return name;
	}
}
