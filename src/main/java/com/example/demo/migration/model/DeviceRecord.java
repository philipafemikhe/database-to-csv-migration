package com.example.demo.migration.model;

import java.time.LocalDateTime;

public final class DeviceRecord {

	private final long id;
	private final String uuid;
	private final Long customerId;
	private final LocalDateTime registrationCompleted;
	private final LocalDateTime registrationStarted;
	private final Boolean active;

	public DeviceRecord(long id, String uuid, Long customerId, LocalDateTime registrationCompleted,
			LocalDateTime registrationStarted, Boolean active) {
		this.id = id;
		this.uuid = uuid;
		this.customerId = customerId;
		this.registrationCompleted = registrationCompleted;
		this.registrationStarted = registrationStarted;
		this.active = active;
	}

	public long id() {
		return id;
	}

	public String uuid() {
		return uuid;
	}

	public Long customerId() {
		return customerId;
	}

	public LocalDateTime registrationCompleted() {
		return registrationCompleted;
	}

	public LocalDateTime registrationStarted() {
		return registrationStarted;
	}

	public Boolean active() {
		return active;
	}
}
