package com.example.demo.migration.model;

public final class BankRecord {

	private final long id;
	private final String name;
	private final String swiftCode;
	private final boolean wallet;
	private final Boolean allowInstantTransfer;
	private final String code;

	public BankRecord(long id, String name, String swiftCode, boolean wallet, Boolean allowInstantTransfer, String code) {
		this.id = id;
		this.name = name;
		this.swiftCode = swiftCode;
		this.wallet = wallet;
		this.allowInstantTransfer = allowInstantTransfer;
		this.code = code;
	}

	public long id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String swiftCode() {
		return swiftCode;
	}

	public boolean wallet() {
		return wallet;
	}

	public Boolean allowInstantTransfer() {
		return allowInstantTransfer;
	}

	public String code() {
		return code;
	}
}
