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

	CliOptions(boolean help, String configPath, String output, String outputCustomers, String outputTransactions,
			String limit, boolean describeSchema, boolean skipBeneficiaries, boolean skipCustomers, boolean skipTransactions) {
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
}
