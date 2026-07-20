package com.example.demo.migration.cli;

import java.util.HashMap;
import java.util.Map;

/** Parses {@code --key=value} / {@code --flag} style arguments into {@link CliOptions}. */
public final class ArgParser {

	private ArgParser() {
	}

	public static CliOptions parse(String[] args) {
		Map<String, String> raw = new HashMap<>();
		for (String arg : args) {
			if (!arg.startsWith("--")) {
				continue;
			}
			String body = arg.substring(2);
			int eq = body.indexOf('=');
			if (eq < 0) {
				raw.put(body, "true");
			} else {
				raw.put(body.substring(0, eq), body.substring(eq + 1));
			}
		}

		return new CliOptions(
				raw.containsKey("help"),
				raw.get("config"),
				raw.get("output"),
				raw.get("output-customers"),
				raw.get("limit"),
				raw.containsKey("describe-schema"),
				raw.containsKey("skip-beneficiaries"),
				raw.containsKey("skip-customers"));
	}
}
