package com.example.demo.migration.repo;

import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.model.BillerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Billers table schema isn't documented anywhere in this project, so its columns are
 * resolved dynamically (case-insensitive), the same approach as Banks/CountryPartner.
 */
public final class BillersRepository {

	private static final Logger log = LoggerFactory.getLogger(BillersRepository.class);

	private final Map<Long, BillerRecord> byId = new HashMap<>();
	private boolean available = true;

	private BillersRepository() {
	}

	public static BillersRepository load(Connection connection, MigrationConfig config) {
		BillersRepository repo = new BillersRepository();
		String sql = "SELECT * FROM " + config.billersTable();
		try (Statement st = connection.createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			List<String> columns = JdbcUtils.columnNames(rs);
			String idCol = JdbcUtils.resolveColumn(columns, config.billersColumnId(), "id", "biller_id");
			String gatewayCodeCol = JdbcUtils.resolveColumn(columns, config.billersColumnGatewayCode(), "gatewayCode", "gateway_code");
			String nameCol = JdbcUtils.resolveColumn(columns, config.billersColumnName(), "name");

			if (idCol == null) {
				log.warn("Billers table '{}' has no resolvable id column; dtype rules that key off the biller gateway code "
						+ "will fall back to their default. Columns found: {}", config.billersTable(), columns);
				repo.available = false;
				return repo;
			}
			log.info("Billers table '{}' resolved columns: id={}, gatewayCode={}, name={}", config.billersTable(), idCol, gatewayCodeCol, nameCol);

			while (rs.next()) {
				long id = rs.getLong(idCol);
				String gatewayCode = gatewayCodeCol == null ? null : JdbcUtils.getString(rs, gatewayCodeCol);
				String name = nameCol == null ? null : JdbcUtils.getString(rs, nameCol);
				repo.byId.put(id, new BillerRecord(id, gatewayCode, name));
			}
		} catch (SQLException e) {
			log.warn("Could not read Billers table '{}' ({}); dtype rules that key off the biller gateway code will fall "
					+ "back to their default.", config.billersTable(), e.getMessage());
			repo.available = false;
		}
		return repo;
	}

	public boolean isAvailable() {
		return available;
	}

	public BillerRecord findById(Long id) {
		return id == null ? null : byId.get(id);
	}

	public int size() {
		return byId.size();
	}
}
