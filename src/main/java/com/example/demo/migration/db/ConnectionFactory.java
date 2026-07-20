package com.example.demo.migration.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ConnectionFactory {

	private static final Logger log = LoggerFactory.getLogger(ConnectionFactory.class);

	private ConnectionFactory() {
	}

	public static Connection open(MigrationConfig config) throws SQLException {
		try {
			Class.forName(config.dbDriver());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("JDBC driver not found on classpath: " + config.dbDriver(), e);
		}

		String user = config.dbUsername();
		// Safe to log: the username/password are passed as separate JDBC connection
		// properties in this codebase, never embedded in the URL string itself.
		log.info("Connecting to {} as {}", config.dbUrl(), user == null ? "(no username configured)" : user);

		Connection connection = user == null
				? DriverManager.getConnection(config.dbUrl())
				: DriverManager.getConnection(config.dbUrl(), user, config.dbPassword());
		log.info("Connected.");
		return connection;
	}
}
