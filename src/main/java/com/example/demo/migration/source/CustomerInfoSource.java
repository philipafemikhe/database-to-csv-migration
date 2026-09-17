package com.example.demo.migration.source;

import com.example.demo.migration.WarningCollector;
import com.example.demo.migration.csv.CsvWriter;
import com.example.demo.migration.db.CustomerIdFilter;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.mapping.CustomerInfoMapper;
import com.example.demo.migration.model.UserRecord;
import com.example.demo.migration.repo.BankBranchesRepository;
import com.example.demo.migration.repo.CountryPartnerRepository;
import com.example.demo.migration.repo.DevicesRepository;
import com.example.demo.migration.repo.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Users are already fully loaded in memory for the beneficiary lookups, so this just iterates them. */
public final class CustomerInfoSource {

	private static final Logger log = LoggerFactory.getLogger(CustomerInfoSource.class);
	private static final int PROGRESS_INTERVAL = 5000;

	private CustomerInfoSource() {
	}

	public static int migrate(MigrationConfig config, UsersRepository users, BankBranchesRepository branches, DevicesRepository devices,
			CountryPartnerRepository countries, CsvWriter writer, String runTimestamp, WarningCollector warnings) throws IOException {

		List<Long> customerIds = CustomerIdFilter.resolve(config.customerIds(), users);
		Set<Long> customerIdFilter = customerIds.isEmpty() ? null : new HashSet<>(customerIds);

		long start = System.currentTimeMillis();
		int count = 0;
		for (UserRecord user : users.all()) {
			if (customerIdFilter != null && !customerIdFilter.contains(user.id())) {
				continue;
			}
			writer.write(CustomerInfoMapper.map(user, branches, devices, countries, runTimestamp, warnings));
			count++;
			if (count % PROGRESS_INTERVAL == 0) {
				log.info("Users: {} rows migrated so far...", count);
			}
		}
		log.info("Users: completed, {} rows in {} ms ({} warning(s))", count, System.currentTimeMillis() - start, warnings.size());
		return count;
	}
}
