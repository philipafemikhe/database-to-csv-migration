package com.example.demo.migration.mapping;

import static com.example.demo.migration.mapping.ColumnRule.bool;
import static com.example.demo.migration.mapping.ColumnRule.copy;

/** dbo.Devices -> devices, per the "Devices" sheet of MobileBanking_Users_Devices_OldUSSDAccount_Mapping.xlsx. */
public final class DevicesSchema {

	public static final ColumnRule[] RULES = {
			copy("id", "id"),
			bool("active", "active"),
			copy("deviceModel", "device_model"),
			copy("deviceState", "device_state"),
			copy("latitude", "latitude"),
			copy("longitude", "longitude"),
			copy("nickname", "nickname"),
			copy("registrationCompleted", "registration_completed"),
			copy("registrationStarted", "registration_started"),
			copy("uuid", "uuid"),
			copy("customer_id", "customer_id")
	};

	public static final String[] HEADER = ColumnRule.header(RULES);

	private DevicesSchema() {
	}
}
