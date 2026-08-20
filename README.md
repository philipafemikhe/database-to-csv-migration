# database-to-csv-migration

Migrates records from the old SQL Server schema into the new-model CSVs:

- `dbo.TransferBeneficiaries` + `dbo.PaymentBeneficiaries` → `beneficiary_details.csv`
- `dbo.Users` → `customer_info.csv`
- `dbo.Transactions` → `transaction.csv`

Ships as one self-contained jar — connects to the source database directly, no intermediate
exports needed.

## Build

```
./mvnw package
```

Produces `target/beneficiary-migration.jar` (all dependencies, including the SQL Server JDBC
driver, are bundled in).

## Configure

Copy `migration.properties.example` to `migration.properties` (git-ignored — it holds real DB
credentials) and fill in `db.url` / `db.username` / `db.password`. Everything else has a
sensible default; see the comments in the example file.

The `Banks`, `CountryPartner`, `Billers`, and `Products` table schemas weren't available while
building this tool, so their column names are resolved dynamically at runtime. Before a real run, use:

```
java -jar target/beneficiary-migration.jar --config=migration.properties --describe-schema
```

to print the actual columns of those tables, and adjust `banks.column.*` /
`countryPartner.column.*` / `billers.column.*` / `products.column.*` in your properties file if
the defaults don't match.

## Run

```
java -jar target/beneficiary-migration.jar --config=migration.properties
```

Runs all three migrations in one pass (reusing the same DB connection and already-loaded
reference data) and writes:
- `output` (default `beneficiary_details_migrated.csv`)
- `output.customerInfo` (default `customer_info_migrated.csv`)
- `output.transactions` (default `transaction_migrated.csv`)

each with its own sibling `*.warnings.log` listing rows that couldn't be fully resolved.

Useful flags:
- `--output=path.csv` / `--output-customers=path.csv` / `--output-transactions=path.csv` —
  override any output path for this run.
- `--limit=N` — cap rows read per source table (dry run against a test DB).
- `--skip-beneficiaries` / `--skip-customers` / `--skip-transactions` — run just a subset of the
  three migrations.
- `--split-by-country` — write `beneficiary_details` and `transaction` as one file per country
  (e.g. `beneficiary_details_migrated_MW.csv`, `_BW.csv`, `_ZW.csv`, `_ZM.csv`) instead of a
  single merged file, keyed off the same fixed IswCountry code each row already carries
  (`customer_country_code` for beneficiary_details, `country_code` for transaction). Rows with no
  resolvable country go to a `_UNKNOWN` file so row counts still reconcile. `customer_info` is
  never split - it's grouped by a different, dynamic `CountryPartner` lookup, not the fixed
  IswCountry table, so it has no consistent country code to split on.

## Logging

Console output is timestamped and leveled (`[INFO]`/`[WARN]`/`[ERROR]`), e.g.:

```
2026-07-20 19:54:26.594 [INFO] BanksRepository - Banks table 'dbo.Banks' resolved columns: id=id, name=name, swiftCode=gatewayCode, institution=institution
2026-07-20 19:54:26.600 [WARN] TransferBeneficiaries - TransferBeneficiaries id=2: bankBranch_id 99 belongs to bank 999, not row's bank_id 88; branch_name left blank
2026-07-20 19:54:26.600 [INFO] TransferBeneficiarySource - TransferBeneficiaries: completed, 9 rows in 2 ms (4 warning(s))
```

Every row-level validation warning is logged live (at WARN, as it happens) *and* collected into
that migration's `*.warnings.log`, so a support engineer tailing a long production run sees
problems as they occur rather than only after the whole run finishes. Each of the three source
tables logs a progress line every 5,000 rows, so a long run never goes silent. A failed run logs
one clear `[ERROR] Migration failed after <ms>: <reason>` line (with the full stack trace
attached) instead of an unformatted Java exception dump.

## Code layout

```
MigrationCli        entrypoint only - parse args, load config, open the DB connection, delegate
MigrationRunner      orchestrates one run: loads shared reference data once, drives all migrations
SchemaInspector       backs --describe-schema
WarningCollector      collects + immediately logs one source table's validation warnings
ValidationWarning     one warning; also writes the warnings.log file

cli/      argument parsing (ArgParser -> CliOptions)
db/       JDBC plumbing (MigrationConfig, ConnectionFactory, JdbcUtils)
repo/     one repository per lookup table, loaded fully into memory (Users, Banks, BankBranches,
          CountryPartner, Devices, Billers, Products)
source/   one class per source table, streams rows via JDBC and drives the matching mapper
mapping/  the actual field-mapping logic + small shared helpers (BeneficiaryTypeMapper,
          LanguageCode, TimestampFormat, IswCountry, TransactionDTypeMapper) + output schemas
model/    plain records for the repo-loaded lookup data
json/     additionalParams JSON parsing for PaymentBeneficiaries
csv/      the output CSV writer
```

## Mapping notes

### beneficiary_details (TransferBeneficiaries + PaymentBeneficiaries)

Field mapping comes from `TransferBeneficaries_Migration_Logic.xlsx` in `src/main/resources/`.

- `customer_id` comes from `Users.userName`, looked up by the beneficiary row's `customer_id`.
- `customer_country_code` comes from `Users.country_id`, mapped via `IswCountry`'s fixed 1-4
  table (1=Malawi/MW, 2=Botswana/BW, 3=Zimbabwe/ZW, 4=Zambia/ZM) - the same fixed table the
  `Transactions.dtype` rules use, confirmed identical across both mapping docs, so it's a
  system-wide constant rather than a dynamic `CountryPartner` lookup. Applied to both
  `TransferBeneficiaries` and `PaymentBeneficiaries` rows for consistency, since they share this
  column in the same merged output file (only `TransferBeneficiaries` is covered by the doc, but
  leaving `PaymentBeneficiaries` on the old dynamic lookup would produce mismatched values for the
  same column in the same CSV).
- `beneficiary_type`: `LOCAL`→`WIB`, `EFT`→`OBE`, `INSTANT`→`OBI`, `RTGS`→`OBR` - all
  unconditional **except** Zambia `INSTANT`: a wallet-institution bank with
  `allowInstantTransfer=1` maps to `IWL` instead (both conditions required; a Zambia wallet bank
  with `allowInstantTransfer=0` stays `OBI`, unlike the old behavior where wallet alone was
  enough). The doc's "Exception – Zambia" note under `RTGS` doesn't change the result - it just
  reaffirms `OBR` for a `BANK`-institution/`allowInstantTransfer=1` bank, as opposed to the
  `INSTANT` wallet exception which does override.
- `bank_name` / `swift_code` come from the `Banks` table by `bank_id` (`swift_code` is actually
  `Banks.gatewayCode`, see `banks.column.swiftCode`).
- `wallet_type` ← `Banks.code`, only when Zambia + wallet institution + `allowInstantTransfer=1`
  (same condition as the `beneficiary_type` exception above); blank otherwise. Uses the `code`
  column, not the bank's name.
- `bank_gateway_code` ← `Banks.swiftCode` (the `gatewayCode` column) for `RTGS` rows, `Banks.code`
  for `INSTANT` rows (wallet or not - both draw from `code`); blank for `LOCAL`/`EFT`.
- `branch_name` / `has_branches` are only populated for Botswana `EFT` rows, and only after
  validating the branch's `bank_id` matches the beneficiary row's `bank_id`.
- `branch_code` ← `BankBranches.gatewayCode`, looked up by `bankBranch_id` - independent of the
  Botswana/EFT validation above (populated for any country/type whenever `bankBranch_id` resolves,
  even when the bank-id mismatch check above blanks out `branch_name`). Previously this was the
  raw `bankBranch_id` passed straight through; now it's the branch's own gateway code.
- `PaymentBeneficiaries` rows have no bank/branch reference at all, so those fields are left
  blank for them; `customer_id`/`beneficiary_name`/`account_number`/`beneficiary_currency` are
  parsed from the `additionalParams` JSON blob (falling back to `destinationAccountNumber` /
  `destinationAccountName` when present). Their `beneficiary_id` is offset by
  `payment.id.offset` to avoid colliding with `TransferBeneficiaries` ids in the merged output.
  Field mapping for the rest of their columns comes from
  `PaymentBeneficiaries_Inerswitch_MigrationLogic.xlsx.docx` in `src/main/resources/`:
  - `beneficiary_type` (see `PaymentBeneficiaryTypeMapper`): Malawi → `IWL` only when
    (`biller_id=5` AND `product_id=69`) OR (`biller_id=7` AND `product_id=10083`), else `BILL`;
    Botswana → `IWL` when `biller_id` IN (90, 115), else `BILL`; Zimbabwe/Zambia → always `BILL`;
    blank when the customer's country can't be resolved.
  - `beneficiary_country` uses the same fixed `IswCountry` lookup as `customer_country_code`.
  - `nickname` ← `additionalParams.customerName` (unlike `beneficiary_name`, this doesn't fall
    back to `destinationAccountName`/other JSON keys).
  - `wallet_type` / `wallet_code` ← `Products.name` / `Products.gatewayCode`, looked up by
    `product_id`, only for the Malawi/Botswana biller/product combinations above that resolve to
    `IWL`; blank otherwise.
  - `biller_name` ← `Billers.name`, looked up by `biller_id`.
  - `biller_input_value` ← `customerReferece` (sic - that's the actual column name on the source
    table).
  - `created_by` / `last_modified_by` ← `SYSTEM`; `last_modified_at` ← the migration run
    timestamp (same value as `TransferBeneficiaries.created_at`).

### customer_info (Users)

- Direct copies: `customer_id`←`userName`, `mobile_number`/`ussd_mobile_number`←`mobileNumber`,
  `account_id`/`primary_account_id`/`default_account_id`←`primaryAccountNumber`,
  `imsi_number`←`imsinumber`, `registration_mode`, `has_mobile`, `has_ussd`,
  `is_disabled`←`loginDisabled`, `is_profile_deleted`←`isDeleted`,
  `is_ussd_disabled`←`ussdDisabled`, `is_migrated_user_via_pwd`←`migratedPin`,
  `reason_for_profile_deletion`, `profile_deletion_date`, timestamps (`last_login`,
  `ussd_last_login`, `time_to_begin_transact`).
- `country_code` / `default_country_code` come from a `CountryPartner` lookup by `country_id`.
- `default_language` is normalized from a full name (`ENGLISH`) to a short code (`EN`); unknown
  values pass through unchanged.
- `registered_on` / `ussd_registered_on` / `created_date` all mirror `dateRegistered` (the old
  schema has no separate USSD-registration or created-audit timestamp).
- `branch_id` is a **best-effort** lookup: the account number's leading 3 characters are matched
  against `BankBranches.code` (exact, then digits-only, to tolerate letter-prefixed codes like
  `"B024"`). Unmatched rows are logged to the warnings file with `branch_id` left blank — check
  that log after a real run and adjust before trusting this field.
- `device_id` comes from `dbo.Devices` (most recent/active device by `customer_id`), falling
  back to `mobileNumber` when the customer has no device record.
- `account_id_ussd` is set to the account number only when `hasUssd` is true.
- Migration provenance, not preserved data: `is_migrated_user=true`, `migrated_on`/`updated_date`
  = the run timestamp, `created_by`/`updated_by="MIGRATION"`, `unique_id` = a freshly generated
  UUID + account number. `is_approval_required`/`reset_imsi`/`is_aop_user` default to `false`.
- Everything else in the target schema (`free_text_*`, biometric/face-id fields, onboarding
  fields, `is_admin_approved`, `is_ussd_active`/`ussd_active_on`, `profile_photo`,
  `default_theme`) has no source in the old schema and is left blank.

### transaction (Transactions)

Field mapping and the `dtype` derivation rules both come from the two mapping documents in
`src/main/resources/` (`Transactions_MappingDocument.xlsx`, `DTypeMappingRulesforTransactions.docx`);
the target column names below (`customerid`, `destination_bank_id`, etc.) are copied verbatim
from that spreadsheet's "Target Field" column, inconsistent casing and all.

- Most fields (`amount`, `charge`, `bankreference`, `deviceuuid`, `narration`, `sourceaccount`,
  `status`, `statuscode`, `statusdescription`, `transactiondate`, `destinationaccount`,
  `transferprocessor`, `customerreference`, `finalresponsecode`, `gatewayreference`,
  `initialresponsecode`, `phonenumber`, `receiptno`, `tokens`, `destinationaccountname`,
  `channel`, `sourceaccountcurrency`) are direct copies of the matching `Transactions` column.
- `customerid` ← `Users.userName`, looked up by `customer_id` (not a raw id copy).
- `userid` / `biller_id` / `product_id` ← `user_id` / `biller_id` / `product_id`, copied verbatim
  as raw ids (unlike `customerid`, these are **not** resolved to a name).
- `destination_bank_id` ← `Banks.name`, looked up by `destinationBank_id` — despite the field
  name, the mapping doc calls for the bank's name here, not its id.
- `country_code` ← `country_id`, via a fixed table given directly in the DType rules doc
  (1=Malawi/MW, 2=Botswana/BW, 3=Zimbabwe/ZW, 4=Zambia/ZM) — unlike beneficiary/customer_info,
  this does **not** go through the dynamic `CountryPartner` lookup.
- `dtype` is derived from the old `DTYPE` (ISW transaction type) plus country and biller/bank
  lookups, per `DTypeMappingRulesforTransactions.docx`:
  - `LocalTransfer` → `WIB`, unconditionally.
  - `BillPayment` → Zimbabwe/Zambia: always `BILL`. Malawi/Botswana: `Billers.gatewayCode`
    (looked up by `biller_id`) decides — Malawi's `"Simpo Cash"`/`"Simpo Agent Deposits"` and
    Botswana's `"Orange-B2C"` map to `IWL`; everything else (including an unresolved biller) is
    `BILL`.
  - `AirtimePurchase` → Botswana/Zimbabwe/Zambia: always `BILL`. Malawi: `Products.gatewayCode`
    (looked up by `product_id`, **not** `Billers.gatewayCode`/`biller_id` - Airtime keys off the
    product, unlike BillPayment which keys off the biller) decides — `TNMAIR`/`ATLAIR` → `BILL`,
    `AIRB2W`/`TNMB2W` → `IWL`; an unrecognized code is left blank (warned), since the doc gives no
    "all others" fallback here (unlike BillPayment).
  - `InterBankTransfer` → Botswana: always `OBE`. Zimbabwe: `destinationBank_id` is `NULL` →
    `OBI` (ZIPIT), otherwise `OBR` (RTGS) — no bank lookup needed. Malawi:
    `Banks.allowInstantTransfer` by `destinationBank_id` (`true`→`OBI`, `false`→`OBR`). Zambia:
    a wallet-institution bank always wins → `IWL`; otherwise the same `allowInstantTransfer` flag
    (`true`→`OBI`, `false`→`OBR`) — the same wallet-clubbing behavior as
    `beneficiary_type`/`BeneficiaryTypeMapper` for Zambia.
  - Anything else (unrecognized `DTYPE`, unresolved country, or a lookup that can't be resolved)
    is logged as a warning and left blank rather than guessed.
- `Banks.allowInstantTransfer` (config: `banks.column.allowInstantTransfer`, default
  `allowsInstantTransfer`) and the `Billers`/`Products` tables (schema undocumented, resolved
  dynamically like `Banks`/`CountryPartner` — see `billers.column.*`/`products.column.*`) are the
  new lookups this migration needed beyond what beneficiary_details/customer_info already used.
