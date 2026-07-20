package com.example.demo.migration.repo;

import com.example.demo.migration.db.JdbcUtils;
import com.example.demo.migration.model.DeviceRecord;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps only the "best" device per customer: prefer an active device, then whichever
 * registered most recently (registrationCompleted, falling back to registrationStarted).
 */
public final class DevicesRepository {

	private final Map<Long, DeviceRecord> byCustomerId = new HashMap<>();

	private DevicesRepository() {
	}

	public static DevicesRepository load(Connection connection, String table) throws SQLException {
		DevicesRepository repo = new DevicesRepository();
		String sql = "SELECT id, uuid, customer_id, registrationCompleted, registrationStarted, active FROM " + table;
		try (Statement st = connection.createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			while (rs.next()) {
				Long customerId = JdbcUtils.getLong(rs, "customer_id");
				if (customerId == null) {
					continue;
				}
				DeviceRecord candidate = new DeviceRecord(
						rs.getLong("id"),
						JdbcUtils.getString(rs, "uuid"),
						customerId,
						JdbcUtils.getTimestamp(rs, "registrationCompleted"),
						JdbcUtils.getTimestamp(rs, "registrationStarted"),
						JdbcUtils.getBoolean(rs, "active"));
				repo.byCustomerId.merge(customerId, candidate, DevicesRepository::preferred);
			}
		}
		return repo;
	}

	private static DeviceRecord preferred(DeviceRecord current, DeviceRecord candidate) {
		boolean currentActive = Boolean.TRUE.equals(current.active());
		boolean candidateActive = Boolean.TRUE.equals(candidate.active());
		if (candidateActive != currentActive) {
			return candidateActive ? candidate : current;
		}
		return recency(candidate).isAfter(recency(current)) ? candidate : current;
	}

	private static LocalDateTime recency(DeviceRecord d) {
		if (d.registrationCompleted() != null) {
			return d.registrationCompleted();
		}
		return d.registrationStarted() != null ? d.registrationStarted() : LocalDateTime.MIN;
	}

	public DeviceRecord findByCustomerId(Long customerId) {
		return customerId == null ? null : byCustomerId.get(customerId);
	}

	public int size() {
		return byCustomerId.size();
	}
}
