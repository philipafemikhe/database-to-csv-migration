package com.example.demo.migration.mapping;

/** The 30-column layout from Transactions_MappingDocument.xlsx's "Target Field" list, plus "id". */
public final class TransactionSchema {

	public static final String[] HEADER = {
			"id", "dtype", "amount", "charge", "bankreference", "deviceuuid", "narration", "sourceaccount", "status",
			"statuscode", "statusdescription", "transactiondate", "destinationaccount", "transferprocessor",
			"customerreference", "finalresponsecode", "gatewayreference", "initialresponsecode", "customerid",
			"destination_bank_id", "biller_id", "product_id", "phonenumber", "country_code", "receiptno", "tokens",
			"destinationaccountname", "channel", "userid", "sourceaccountcurrency"
	};

	private TransactionSchema() {
	}
}
