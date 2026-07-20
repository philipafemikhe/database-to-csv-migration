package com.example.demo.migration.model;

import java.time.LocalDateTime;

public record DeviceRecord(long id, String uuid, Long customerId, LocalDateTime registrationCompleted,
		LocalDateTime registrationStarted, Boolean active) {
}
