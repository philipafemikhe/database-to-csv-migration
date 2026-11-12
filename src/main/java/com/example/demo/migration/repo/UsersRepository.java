package com.example.demo.migration.repo;

import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.model.UserRecord;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UsersRepository {

	/** Users flagged {@code disable_user = 1} are excluded from every migration output. */
	public static final String DISABLE_USER_COLUMN = "disable_user";

	private final Map<Long, UserRecord> byId = new HashMap<>();
	private final Map<String, UserRecord> byUserName = new HashMap<>();
	private final Set<Long> disabledIds = new HashSet<>();
	private final String table;
	/** SQL predicate that's true for a disabled Users row, or null when there's no disable_user column. */
	private String disabledSql;
	private String disableUserColumn;

	private UsersRepository(String table) {
		this.table = table;
	}

	public static UsersRepository load(Connection connection, String table) throws SQLException {
		UsersRepository repo = new UsersRepository(table);
		repo.disableUserColumn = disableUserColumn(connection, table);
		repo.disabledSql = disabledPredicate(connection, table, repo.disableUserColumn);
		if (repo.disabledSql != null) {
			try (Statement st = connection.createStatement();
					ResultSet rs = st.executeQuery("SELECT id FROM " + table + " WHERE " + repo.disabledSql)) {
				while (rs.next()) {
					repo.disabledIds.add(rs.getLong("id"));
				}
			}
		}
		String sql = "SELECT id, userName, country_id, mobileNumber, primaryAccountNumber, dateRegistered, "
				+ "lastLogin, ussdLastLogin, defaultLanguage, timeToBeginTransact, registrationMode, hasMobile, "
				+ "hasUssd, loginDisabled, isDeleted, migratedPin, ussdDisabled, imsinumber, "
				+ "reasonForProfileDeletion, profileDeletionDate FROM " + table;
		try (Statement st = connection.createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				long id = rs.getLong("id");
				UserRecord user = new UserRecord(
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
						JdbcUtils.getTimestamp(rs, "profileDeletionDate"));
				repo.byId.put(id, user);
				if (user.userName() != null) {
					repo.byUserName.put(user.userName(), user);
				}
			}
		}
		return repo;
	}

	private static String disableUserColumn(Connection connection, String table) throws SQLException {
		try (Statement st = connection.createStatement();
				ResultSet rs = st.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
			return JdbcUtils.resolveColumn(JdbcUtils.columnNames(rs), DISABLE_USER_COLUMN);
		}
	}

	/**
	 * Builds "disable_user is 1" for the column's actual type. disable_user isn't in the table scripts,
	 * so its type isn't known up front: a numeric/bit column compares to 1, while a text column is
	 * compared as text, so a stray non-numeric value can't fail the whole query on a conversion error.
	 */
	private static String disabledPredicate(Connection connection, String table, String column) throws SQLException {
		if (column == null) {
			return null;
		}
		try (Statement st = connection.createStatement();
				ResultSet rs = st.executeQuery("SELECT " + column + " FROM " + table + " WHERE 1 = 0")) {
			int type = rs.getMetaData().getColumnType(1);
			switch (type) {
				case Types.CHAR:
				case Types.VARCHAR:
				case Types.LONGVARCHAR:
				case Types.NCHAR:
				case Types.NVARCHAR:
				case Types.LONGNVARCHAR:
					return "LTRIM(RTRIM(" + column + ")) = '1'";
				default:
					return column + " = 1";
			}
		}
	}

	/** Whether the Users table has a disable_user column at all; without it no user is excluded. */
	public boolean hasDisableUserColumn() {
		return disabledSql != null;
	}

	public boolean isDisabled(long id) {
		return disabledIds.contains(id);
	}

	public Set<Long> disabledIds() {
		return Collections.unmodifiableSet(disabledIds);
	}

	/**
	 * SQL condition keeping only the Users rows that aren't disabled (NULL counts as not disabled),
	 * or null when the table has no disable_user column.
	 */
	public String activeUsersSql() {
		return disabledSql == null ? null : "(" + disableUserColumn + " IS NULL OR NOT (" + disabledSql + "))";
	}

	/**
	 * SQL condition keeping only rows whose {@code customerIdColumn} doesn't point at a disabled user
	 * (rows with no customer are kept), or null when the Users table has no disable_user column.
	 */
	public String notOwnedByDisabledUserSql(String customerIdColumn) {
		return disabledSql == null ? null
				: "(" + customerIdColumn + " IS NULL OR " + customerIdColumn + " NOT IN (SELECT id FROM " + table
						+ " WHERE " + disabledSql + "))";
	}

	/** Same as {@link #notOwnedByDisabledUserSql} but matching on Users.userName instead of id. */
	public String notDisabledUserNameSql(String userNameColumn) {
		return disabledSql == null ? null
				: "(" + userNameColumn + " IS NULL OR " + userNameColumn + " NOT IN (SELECT userName FROM " + table
						+ " WHERE " + disabledSql + " AND userName IS NOT NULL))";
	}

	public UserRecord findById(Long id) {
		return id == null ? null : byId.get(id);
	}

	public UserRecord findByUserName(String userName) {
		return userName == null ? null : byUserName.get(userName);
	}

	public Collection<UserRecord> all() {
		return byId.values();
	}

	public int size() {
		return byId.size();
	}
}
