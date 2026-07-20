package com.example.demo.migration.model;

import java.time.LocalDateTime;

public record UserRecord(
		long id,
		String userName,
		Long countryId,
		String mobileNumber,
		String primaryAccountNumber,
		LocalDateTime dateRegistered,
		LocalDateTime lastLogin,
		LocalDateTime ussdLastLogin,
		String defaultLanguage,
		LocalDateTime timeToBeginTransact,
		String registrationMode,
		Boolean hasMobile,
		Boolean hasUssd,
		Boolean loginDisabled,
		Boolean isDeleted,
		Boolean migratedPin,
		Boolean ussdDisabled,
		String imsiNumber,
		String reasonForProfileDeletion,
		LocalDateTime profileDeletionDate) {
}
