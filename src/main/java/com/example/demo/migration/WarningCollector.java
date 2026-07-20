package com.example.demo.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects validation warnings for one source table (e.g. "TransferBeneficiaries") so they can
 * be written to a warnings.log at the end of a run, while also logging each one immediately at
 * WARN level. That way a support engineer tailing the console during a long production run sees
 * problems as they happen - e.g. a bad config causing every row to fail validation shows up
 * after the first few rows, not only once the whole run has finished.
 */
public final class WarningCollector {

	private final String source;
	private final Logger logger;
	private final List<ValidationWarning> warnings = new ArrayList<>();

	public WarningCollector(String source) {
		this.source = source;
		this.logger = LoggerFactory.getLogger("migration." + source);
	}

	public void warn(long oldId, String message) {
		ValidationWarning warning = new ValidationWarning(source, oldId, message);
		warnings.add(warning);
		logger.warn(warning.toString());
	}

	public List<ValidationWarning> all() {
		return Collections.unmodifiableList(warnings);
	}

	public int size() {
		return warnings.size();
	}

	public boolean isEmpty() {
		return warnings.isEmpty();
	}
}
