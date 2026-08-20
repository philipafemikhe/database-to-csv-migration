package com.example.demo.migration.repo;

import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.db.MigrationConfig;
import com.example.demo.migration.model.ProductRecord;
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
 * The Products table schema isn't documented anywhere in this project, so its columns are
 * resolved dynamically (case-insensitive), the same approach as Banks/CountryPartner/Billers.
 */
public final class ProductsRepository {

	private static final Logger log = LoggerFactory.getLogger(ProductsRepository.class);

	private final Map<Long, ProductRecord> byId = new HashMap<>();
	private boolean available = true;

	private ProductsRepository() {
	}

	public static ProductsRepository load(Connection connection, MigrationConfig config) {
		ProductsRepository repo = new ProductsRepository();
		String sql = "SELECT * FROM " + config.productsTable();
		try (Statement st = connection.createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			List<String> columns = JdbcUtils.columnNames(rs);
			String idCol = JdbcUtils.resolveColumn(columns, config.productsColumnId(), "id", "product_id");
			String gatewayCodeCol = JdbcUtils.resolveColumn(columns, config.productsColumnGatewayCode(), "gatewayCode", "gateway_code");
			String nameCol = JdbcUtils.resolveColumn(columns, config.productsColumnName(), "name");

			if (idCol == null) {
				log.warn("Products table '{}' has no resolvable id column; dtype rules that key off the product gateway "
						+ "code will fall back to their default. Columns found: {}", config.productsTable(), columns);
				repo.available = false;
				return repo;
			}
			log.info("Products table '{}' resolved columns: id={}, gatewayCode={}, name={}", config.productsTable(), idCol, gatewayCodeCol, nameCol);

			while (rs.next()) {
				long id = rs.getLong(idCol);
				String gatewayCode = gatewayCodeCol == null ? null : JdbcUtils.getString(rs, gatewayCodeCol);
				String name = nameCol == null ? null : JdbcUtils.getString(rs, nameCol);
				repo.byId.put(id, new ProductRecord(id, gatewayCode, name));
			}
		} catch (SQLException e) {
			log.warn("Could not read Products table '{}' ({}); dtype rules that key off the product gateway code will "
					+ "fall back to their default.", config.productsTable(), e.getMessage());
			repo.available = false;
		}
		return repo;
	}

	public boolean isAvailable() {
		return available;
	}

	public ProductRecord findById(Long id) {
		return id == null ? null : byId.get(id);
	}

	public int size() {
		return byId.size();
	}
}
