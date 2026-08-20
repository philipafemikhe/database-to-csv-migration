package com.example.demo.migration.model;

public final class BankBranchRecord {

	private final long id;
	private final String name;
	private final Long bankId;
	private final String code;
	private final String gatewayCode;

	public BankBranchRecord(long id, String name, Long bankId, String code, String gatewayCode) {
		this.id = id;
		this.name = name;
		this.bankId = bankId;
		this.code = code;
		this.gatewayCode = gatewayCode;
	}

	public long id() {
		return id;
	}

	public String name() {
		return name;
	}

	public Long bankId() {
		return bankId;
	}

	public String code() {
		return code;
	}

	public String gatewayCode() {
		return gatewayCode;
	}
}
