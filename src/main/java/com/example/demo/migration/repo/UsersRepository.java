package com.example.demo.migration.repo;

import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.model.UserRecord;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class UsersRepository {

	private final Map<Long, UserRecord> byId = new HashMap<>();

	private UsersRepository() {
	}

	public static UsersRepository load(Connection connection, String table) throws SQLException {
		UsersRepository repo = new UsersRepository();
		String sql = "SELECT id, userName, country_id, mobileNumber, primaryAccountNumber, dateRegistered, "
				+ "lastLogin, ussdLastLogin, defaultLanguage, timeToBeginTransact, registrationMode, hasMobile, "
				+ "hasUssd, loginDisabled, isDeleted, migratedPin, ussdDisabled, imsinumber, "
				+ "reasonForProfileDeletion, profileDeletionDate FROM " + table;
		try (Statement st = connection.createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				long id = rs.getLong("id");
				repo.byId.put(id, new UserRecord(
						id,
						JdbcUtils.getString(rs, "userName"),
						JdbcUtils.getLong(rs, "country_id"),
						JdbcUtils.getString(rs, "mobileNumber"),
						JdbcUtils.getString(rs, "primaryAccountNumber"),
						JdbcUtils.getTimestamp(rs, "dateRegistered"),
						JdbcUtils.getTimestamp(rs, "lastLogin"),
						JdbcUtils.getTimestamp(rs, "ussdLastLogin"),
						JdbcUtils.getString(rs, "defaultLanguage"),
						JdbcUtils.getTimestamp(rs, "timeToBeginTransact"),
						JdbcUtils.getString(rs, "registrationMode"),
						JdbcUtils.getBoolean(rs, "hasMobile"),
						JdbcUtils.getBoolean(rs, "hasUssd"),
						JdbcUtils.getBoolean(rs, "loginDisabled"),
						JdbcUtils.getBoolean(rs, "isDeleted"),
						JdbcUtils.getBoolean(rs, "migratedPin"),
						JdbcUtils.getBoolean(rs, "ussdDisabled"),
						JdbcUtils.getString(rs, "imsinumber"),
						JdbcUtils.getString(rs, "reasonForProfileDeletion"),
						JdbcUtils.getTimestamp(rs, "profileDeletionDate")));
			}
		}
		return repo;
	}

	public UserRecord findById(Long id) {
		return id == null ? null : byId.get(id);
	}

	public Collection<UserRecord> all() {
		return byId.values();
	}

	public int size() {
		return byId.size();
	}
}
