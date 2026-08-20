package com.example.demo.migration.model;

import java.time.LocalDateTime;

public final class UserRecord {

	private final long id;
	private final String userName;
	private final Long countryId;
	private final String mobileNumber;
	private final String primaryAccountNumber;
	private final LocalDateTime dateRegistered;
	private final LocalDateTime lastLogin;
	private final LocalDateTime ussdLastLogin;
	private final String defaultLanguage;
	private final LocalDateTime timeToBeginTransact;
	private final String registrationMode;
	private final Boolean hasMobile;
	private final Boolean hasUssd;
	private final Boolean loginDisabled;
	private final Boolean isDeleted;
	private final Boolean migratedPin;
	private final Boolean ussdDisabled;
	private final String imsiNumber;
	private final String reasonForProfileDeletion;
	private final LocalDateTime profileDeletionDate;

	public UserRecord(
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
		this.id = id;
		this.userName = userName;
		this.countryId = countryId;
		this.mobileNumber = mobileNumber;
		this.primaryAccountNumber = primaryAccountNumber;
		this.dateRegistered = dateRegistered;
		this.lastLogin = lastLogin;
		this.ussdLastLogin = ussdLastLogin;
		this.defaultLanguage = defaultLanguage;
		this.timeToBeginTransact = timeToBeginTransact;
		this.registrationMode = registrationMode;
		this.hasMobile = hasMobile;
		this.hasUssd = hasUssd;
		this.loginDisabled = loginDisabled;
		this.isDeleted = isDeleted;
		this.migratedPin = migratedPin;
		this.ussdDisabled = ussdDisabled;
		this.imsiNumber = imsiNumber;
		this.reasonForProfileDeletion = reasonForProfileDeletion;
		this.profileDeletionDate = profileDeletionDate;
	}

	public long id() {
		return id;
	}

	public String userName() {
		return userName;
	}

	public Long countryId() {
		return countryId;
	}

	public String mobileNumber() {
		return mobileNumber;
	}

	public String primaryAccountNumber() {
		return primaryAccountNumber;
	}

	public LocalDateTime dateRegistered() {
		return dateRegistered;
	}

	public LocalDateTime lastLogin() {
		return lastLogin;
	}

	public LocalDateTime ussdLastLogin() {
		return ussdLastLogin;
	}

	public String defaultLanguage() {
		return defaultLanguage;
	}

	public LocalDateTime timeToBeginTransact() {
		return timeToBeginTransact;
	}

	public String registrationMode() {
		return registrationMode;
	}

	public Boolean hasMobile() {
		return hasMobile;
	}

	public Boolean hasUssd() {
		return hasUssd;
	}

	public Boolean loginDisabled() {
		return loginDisabled;
	}

	public Boolean isDeleted() {
		return isDeleted;
	}

	public Boolean migratedPin() {
		return migratedPin;
	}

	public Boolean ussdDisabled() {
		return ussdDisabled;
	}

	public String imsiNumber() {
		return imsiNumber;
	}

	public String reasonForProfileDeletion() {
		return reasonForProfileDeletion;
	}

	public LocalDateTime profileDeletionDate() {
		return profileDeletionDate;
	}
}
