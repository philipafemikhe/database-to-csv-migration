package com.example.demo.migration.repo;

import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.model.BankBranchRecord;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class BankBranchesRepository {

	private final Map<Long, BankBranchRecord> byId = new HashMap<>();
	private final Map<String, BankBranchRecord> byRawCode = new HashMap<>();
	private final Map<String, BankBranchRecord> byDigitsOnlyCode = new HashMap<>();

	private BankBranchesRepository() {
	}

	public static BankBranchesRepository load(Connection connection, String table) throws SQLException {
		BankBranchesRepository repo = new BankBranchesRepository();
		String sql = "SELECT id, name, bank_id, code FROM " + table;
		try (Statement st = connection.createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				long id = rs.getLong("id");
				String name = JdbcUtils.getString(rs, "name");
				Long bankId = JdbcUtils.getLong(rs, "bank_id");
				String code = JdbcUtils.getString(rs, "code");
				BankBranchRecord branch = new BankBranchRecord(id, name, bankId, code);
				repo.byId.put(id, branch);
				if (code != null) {
					repo.byRawCode.putIfAbsent(code.toUpperCase(Locale.ROOT), branch);
					String digitsOnly = code.replaceAll("\\D", "");
					if (!digitsOnly.isEmpty()) {
						repo.byDigitsOnlyCode.putIfAbsent(digitsOnly, branch);
					}
				}
			}
		}
		return repo;
	}

	public BankBranchRecord findById(Long id) {
		return id == null ? null : byId.get(id);
	}

	/**
	 * Best-effort match of a branch code prefix (e.g. the leading digits of an account number)
	 * against BankBranches.code, trying an exact (case-insensitive) match first, then a
	 * digits-only comparison to tolerate letter-prefixed codes like "B024".
	 */
	public BankBranchRecord findByCodePrefix(String prefix) {
		if (prefix == null || prefix.isBlank()) {
			return null;
		}
		BankBranchRecord exact = byRawCode.get(prefix.toUpperCase(Locale.ROOT));
		if (exact != null) {
			return exact;
		}
		String digitsOnly = prefix.replaceAll("\\D", "");
		return digitsOnly.isEmpty() ? null : byDigitsOnlyCode.get(digitsOnly);
	}

	public int size() {
		return byId.size();
	}
}
