package com.example.demo.migration.repo;

import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.model.BankRecord;
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
 * The Banks table schema isn't documented alongside Users/TransferBeneficiaries/etc.,
 * so column names are resolved dynamically (case-insensitive) against configured
 * preferences, with a couple of common-sense fallbacks.
 */
public final class BanksRepository {

	private static final Logger log = LoggerFactory.getLogger(BanksRepository.class);

	private final Map<Long, BankRecord> byId = new HashMap<>();
	private boolean available = true;

	private BanksRepository() {
	}

	public static BanksRepository load(Connection connection, MigrationConfig config) {
		BanksRepository repo = new BanksRepository();
		String sql = "SELECT * FROM " + config.banksTable();
		try (Statement st = connection.createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			List<String> columns = JdbcUtils.columnNames(rs);
			String idCol = JdbcUtils.resolveColumn(columns, config.banksColumnId(), "id", "bank_id");
			String nameCol = JdbcUtils.resolveColumn(columns, config.banksColumnName(), "name", "bankName");
			String swiftCol = JdbcUtils.resolveColumn(columns, config.banksColumnSwiftCode(), "gatewayCode", "routingNumber", "swiftCode", "swift_code", "swift");
			String institutionCol = JdbcUtils.resolveColumn(columns, config.banksColumnInstitution(), "institution", "type", "institutionType");

			if (idCol == null) {
				log.warn("Banks table '{}' has no resolvable id column; bank_name/swift_code/wallet_type will be left blank for all rows. Columns found: {}",
						config.banksTable(), columns);
				repo.available = false;
				return repo;
			}
			log.info("Banks table '{}' resolved columns: id={}, name={}, swiftCode={}, institution={}",
					config.banksTable(), idCol, nameCol, swiftCol, institutionCol);

			while (rs.next()) {
				long id = rs.getLong(idCol);
				String name = nameCol == null ? null : JdbcUtils.getString(rs, nameCol);
				String swift = swiftCol == null ? null : JdbcUtils.getString(rs, swiftCol);
				String institution = institutionCol == null ? null : JdbcUtils.getString(rs, institutionCol);
				boolean wallet = institution != null && institution.toUpperCase().contains(config.banksWalletMarker().toUpperCase());
				repo.byId.put(id, new BankRecord(id, name, swift, wallet));
			}
		} catch (SQLException e) {
			log.warn("Could not read Banks table '{}' ({}); bank_name/swift_code/wallet_type will be left blank for all rows.",
					config.banksTable(), e.getMessage());
			repo.available = false;
		}
		return repo;
	}

	public boolean isAvailable() {
		return available;
	}

	public BankRecord findById(Long id) {
		return id == null ? null : byId.get(id);
	}

	public int size() {
		return byId.size();
	}
}
