package com.example.demo.migration.cli;

import java.util.Optional;

/** Typed view over the parsed command-line flags. Built by {@link ArgParser}. */
public final class CliOptions {

	private final boolean help;
	private final String configPath;
	private final String output;
	private final String outputCustomers;
	private final String outputTransactions;
	private final String limit;
	private final boolean describeSchema;
	private final boolean skipBeneficiaries;
	private final boolean skipCustomers;
	private final boolean skipTransactions;
	private final boolean splitByCountry;
	private final String statusFilter;
	private final String customerIds;
	private final String beneficiaryCustomerIds;
	private final String transactionCustomerIds;
	private final String customerUserNames;
	private final String beneficiaryCustomerUserNames;
	private final String transactionCustomerUserNames;
	private final String transactionMonths;

	CliOptions(boolean help, String configPath, String output, String outputCustomers, String outputTransactions,
			String limit, boolean describeSchema, boolean skipBeneficiaries, boolean skipCustomers, boolean skipTransactions,
			boolean splitByCountry, String statusFilter, String customerIds, String beneficiaryCustomerIds,
			String transactionCustomerIds, String customerUserNames, String beneficiaryCustomerUserNames,
			String transactionCustomerUserNames, String transactionMonths) {
		this.help = help;
		this.configPath = configPath;
		this.output = output;
		this.outputCustomers = outputCustomers;
		this.outputTransactions = outputTransactions;
		this.limit = limit;
		this.describeSchema = describeSchema;
		this.skipBeneficiaries = skipBeneficiaries;
		this.skipCustomers = skipCustomers;
		this.skipTransactions = skipTransactions;
		this.splitByCountry = splitByCountry;
		this.statusFilter = statusFilter;
		this.customerIds = customerIds;
		this.beneficiaryCustomerIds = beneficiaryCustomerIds;
		this.transactionCustomerIds = transactionCustomerIds;
		this.customerUserNames = customerUserNames;
		this.beneficiaryCustomerUserNames = beneficiaryCustomerUserNames;
		this.transactionCustomerUserNames = transactionCustomerUserNames;
		this.transactionMonths = transactionMonths;
	}

	public boolean help() {
		return help;
	}

	public Optional<String> configPath() {
		return Optional.ofNullable(configPath);
	}

	public Optional<String> output() {
		return Optional.ofNullable(output);
	}

	public Optional<String> outputCustomers() {
		return Optional.ofNullable(outputCustomers);
	}

	public Optional<String> outputTransactions() {
		return Optional.ofNullable(outputTransactions);
	}

	public Optional<String> limit() {
		return Optional.ofNullable(limit);
	}

	public boolean describeSchema() {
		return describeSchema;
	}

	public boolean skipBeneficiaries() {
		return skipBeneficiaries;
	}

	public boolean skipCustomers() {
		return skipCustomers;
	}

	public boolean skipTransactions() {
		return skipTransactions;
	}

	public boolean splitByCountry() {
		return splitByCountry;
	}

	public Optional<String> statusFilter() {
		return Optional.ofNullable(statusFilter);
	}

	public Optional<String> customerIds() {
		return Optional.ofNullable(customerIds);
	}

	public Optional<String> beneficiaryCustomerIds() {
		return Optional.ofNullable(beneficiaryCustomerIds);
	}

	public Optional<String> transactionCustomerIds() {
		return Optional.ofNullable(transactionCustomerIds);
	}

	public Optional<String> customerUserNames() {
		return Optional.ofNullable(customerUserNames);
	}

	public Optional<String> beneficiaryCustomerUserNames() {
		return Optional.ofNullable(beneficiaryCustomerUserNames);
	}

	public Optional<String> transactionCustomerUserNames() {
		return Optional.ofNullable(transactionCustomerUserNames);
	}

	public Optional<String> transactionMonths() {
		return Optional.ofNullable(transactionMonths);
	}
}
