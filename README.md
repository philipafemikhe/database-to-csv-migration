# database-to-csv-migration

Migrates records from the old SQL Server schema into the new-model CSVs:

- `dbo.TransferBeneficiaries` + `dbo.PaymentBeneficiaries` → `beneficiary_details.csv`
- `dbo.Users` → `customer_info.csv`

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

The `Banks` and `CountryPartner` table schemas weren't available while building this tool, so
their column names are resolved dynamically at runtime. Before a real run, use:

```
java -jar target/beneficiary-migration.jar --config=migration.properties --describe-schema
```

to print the actual columns of those two tables, and adjust `banks.column.*` /
`countryPartner.column.*` in your properties file if the defaults don't match.

## Run

```
java -jar target/beneficiary-migration.jar --config=migration.properties
```

Runs both migrations in one pass (reusing the same DB connection and already-loaded reference
data) and writes:
- `output` (default `beneficiary_details_migrated.csv`)
- `output.customerInfo` (default `customer_info_migrated.csv`)

each with its own sibling `*.warnings.log` listing rows that couldn't be fully resolved.

Useful flags:
- `--output=path.csv` / `--output-customers=path.csv` — override either output path for this run.
- `--limit=N` — cap rows read per source table (dry run against a test DB).
- `--skip-beneficiaries` / `--skip-customers` — run just one of the two migrations.

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
MigrationRunner      orchestrates one run: loads shared reference data once, drives both migrations
SchemaInspector       backs --describe-schema
WarningCollector      collects + immediately logs one source table's validation warnings
ValidationWarning     one warning; also writes the warnings.log file

cli/      argument parsing (ArgParser -> CliOptions)
db/       JDBC plumbing (MigrationConfig, ConnectionFactory, JdbcUtils)
repo/     one repository per lookup table, loaded fully into memory (Users, Banks, BankBranches,
          CountryPartner, Devices)
source/   one class per source table, streams rows via JDBC and drives the matching mapper
mapping/  the actual field-mapping logic + small shared helpers (BeneficiaryTypeMapper,
          CountryMatcher, LanguageCode, TimestampFormat) + output schemas
model/    plain records for the repo-loaded lookup data
json/     additionalParams JSON parsing for PaymentBeneficiaries
csv/      the output CSV writer
```

## Mapping notes

### beneficiary_details (TransferBeneficiaries + PaymentBeneficiaries)

- `customer_id` comes from `Users.userName`, looked up by the beneficiary row's `customer_id`.
- `customer_country_code` comes from a `CountryPartner` lookup by that same user's `country_id`
  (a short code like `"BW"`, not the raw numeric id).
- `beneficiary_type`: `LOCAL`→`WIB`, `RTGS`→`OBR`, `EFT`→`OBE`, `INSTANT`→`OBI`, except in
  Zambia where a wallet-institution bank maps `INSTANT`→`IWL` instead.
- `bank_name` / `swift_code` come from the `Banks` table by `bank_id`.
- `branch_name` / `has_branches` are only populated for Botswana `EFT` rows, and only after
  validating the branch's `bank_id` matches the beneficiary row's `bank_id`.
- `branch_code` is always the raw `bankBranch_id`, regardless of country/type.
- `PaymentBeneficiaries` rows have no bank/branch reference at all, so those fields are left
  blank for them; `customer_id`/`beneficiary_name`/`account_number`/`beneficiary_currency` are
  parsed from the `additionalParams` JSON blob (falling back to `destinationAccountNumber` /
  `destinationAccountName` when present). Their `beneficiary_id` is offset by
  `payment.id.offset` to avoid colliding with `TransferBeneficiaries` ids in the merged output.

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
