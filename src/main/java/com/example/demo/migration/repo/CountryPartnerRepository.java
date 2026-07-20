package com.example.demo.migration.repo;

import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.model.CountryPartnerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Schema isn't documented, so the "code"-ish column is resolved dynamically, same as Banks. */
public final class CountryPartnerRepository {

	private static final Logger log = LoggerFactory.getLogger(CountryPartnerRepository.class);

	private final Map<Long, CountryPartnerRecord> byId = new HashMap<>();
	private boolean available = true;

	private CountryPartnerRepository() {
	}

	public static CountryPartnerRepository load(Connection connection, MigrationConfig config) {
		CountryPartnerRepository repo = new CountryPartnerRepository();
		String sql = "SELECT * FROM " + config.countryPartnerTable();
		try (Statement st = connection.createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			List<String> columns = JdbcUtils.columnNames(rs);
			String idCol = JdbcUtils.resolveColumn(columns, config.countryPartnerColumnId(), "id");
			String nameCol = JdbcUtils.resolveColumn(columns, config.countryPartnerColumnName(), "code", "countryCode", "name", "countryName");

			if (idCol == null || nameCol == null) {
				log.warn("CountryPartner table '{}' columns could not be resolved; customer_country_code/country_code will be left "
						+ "blank and the Zambia/Botswana-specific beneficiary rules will not trigger. Columns found: {}",
						config.countryPartnerTable(), columns);
				repo.available = false;
				return repo;
			}
			log.info("CountryPartner table '{}' resolved columns: id={}, code={}", config.countryPartnerTable(), idCol, nameCol);

			while (rs.next()) {
				long id = rs.getLong(idCol);
				String label = JdbcUtils.getString(rs, nameCol);
				repo.byId.put(id, new CountryPartnerRecord(id, label));
			}
		} catch (SQLException e) {
			log.warn("Could not read CountryPartner table '{}' ({}); customer_country_code/country_code will be left blank "
					+ "and the Zambia/Botswana-specific beneficiary rules will not trigger.", config.countryPartnerTable(), e.getMessage());
			repo.available = false;
		}
		return repo;
	}

	public boolean isAvailable() {
		return available;
	}

	public CountryPartnerRecord findById(Long id) {
		return id == null ? null : byId.get(id);
	}

	public int size() {
		return byId.size();
	}
}
