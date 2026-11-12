# database-to-csv-migration

Migrates records from the old SQL Server schema into the new-model CSVs:

- `dbo.TransferBeneficiaries` + `dbo.PaymentBeneficiaries` → `beneficiary_details.csv`
- `dbo.Users` → `customer_info.csv`
- `dbo.Transactions` → `transaction.csv`
- `dbo.Users` / `dbo.Devices` / `dbo.Old_Ussd_Account` → `users.csv` / `devices.csv` /
  `old_ussd_account.csv` (column-for-column copies; users and devices one file per country)

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

Runs every migration in one pass (reusing the same DB connection and already-loaded
reference data) and writes:
- `output` (default `beneficiary_details_migrated.csv`)
- `output.customerInfo` (default `customer_info_migrated.csv`)
- `output.transactions` (default `transaction_migrated.csv`)
- `output.users` (default `users_migrated.csv`, always split per country: `users_migrated_MW.csv`, ...)
- `output.devices` (default `devices_migrated.csv`, always split per country: `devices_migrated_MW.csv`, ...)
- `output.oldUssdAccounts` (default `old_ussd_account_migrated.csv`)

each with its own sibling `*.warnings.log` listing rows that couldn't be fully resolved.

Useful flags:
- `--output=path.csv` / `--output-customers=path.csv` / `--output-transactions=path.csv` /
  `--output-users=path.csv` / `--output-devices=path.csv` / `--output-old-ussd-accounts=path.csv` —
  override any output path for this run.
- `--limit=N` — cap rows read per source table (dry run against a test DB).
- `--status-filter=SUCCESSFUL` (or `SUCCESS`) / `--status-filter=FAILED` / `--status-filter=PENDING` — only migrate
  Transactions rows with that `status` (case-insensitive; `SUCCESS` is treated as an alias for
  `SUCCESSFUL`). Omit to migrate every status.
- `--transaction-months=N` (property `transaction.months`, default `3`) — only migrate Transactions
  rows from the last N months, counted from the start of the run date. Rows with a NULL
  `transactionDate` are excluded. `0` migrates every transaction.
- `--customer-ids=101,102,R0540050` — restrict every migration to just those customers by default.
  Each comma-separated token may be a raw numeric `customer_id`/`Users.id`, or a `Users.userName`
  (e.g. `R0540050`) resolved against the already-loaded Users table - an unresolvable userName
  fails the run with a clear error rather than silently migrating everyone. Users rows are always
  filtered by this value; TransferBeneficiaries/PaymentBeneficiaries/Transactions rows are too,
  unless overridden below. Omit (or pass an empty value) to migrate every customer.
- `--beneficiary-customer-ids=...` / `--transaction-customer-ids=...` — same format as
  `--customer-ids`, but scoped to just TransferBeneficiaries/PaymentBeneficiaries or just
  Transactions respectively, overriding `--customer-ids` for that one migration (e.g. migrate every
  customer's Transactions but only some customers' beneficiaries). Each falls back to
  `--customer-ids` when omitted, so passing only `--customer-ids` still restricts every migration
  as before.
- `--customer-usernames=R0540050,0999123456` (property `customer.usernames`) — like
  `--customer-ids`, but every token is looked up as a `Users.userName`, including all-digit
  userNames that `--customer-ids` would read as a raw numeric id. When both flags are passed, the
  filter is the union of the two. `--beneficiary-customer-usernames=...` /
  `--transaction-customer-usernames=...` are the per-migration counterparts. A migration's scoped
  pair (e.g. `--beneficiary-customer-ids` + `--beneficiary-customer-usernames`) replaces the general
  `--customer-ids` + `--customer-usernames` pair as soon as either scoped flag is set.
- `--skip-beneficiaries` / `--skip-customers` / `--skip-transactions` / `--skip-users` /
  `--skip-devices` / `--skip-old-ussd-accounts` — run just a subset of the migrations.
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

### Disabled users (`disable_user = 1`)

Users with `Users.disable_user = 1` are left out of every output, along with everything they own:
`users`, `customer_info`, `devices` (by `customer_id`), `old_ussd_account` (by `userName`),
`beneficiary_details` (Transfer and Payment beneficiaries, by `customer_id`) and `transaction`
(by `customer_id`). A NULL `disable_user` counts as not disabled, and rows whose `customer_id` is
NULL or not in Users are unaffected. If the Users table has no `disable_user` column, a warning is
logged and nothing is excluded.

### users / devices / old_ussd_account

Field mapping comes from `MobileBanking_Users_Devices_OldUSSDAccount_Mapping.xlsx` in
`src/main/resources/` (one sheet per table) - every column is renamed to snake_case and copied
as-is, except:

- "boolean value 0 = FALSE, 1 = TRUE, Blank = FALSE" columns (`has_ussd`, `old_ussd_account`,
  `has_mobile`, `ussd_disabled`, `is_deleted`, `disable_user`, `display_banner`,
  `force_password_reset`, and devices' `active`) are written `TRUE`/`FALSE`.
- `login_retries` is an integer count (`bigint` in the source) and is copied as-is.
- `login_retries` and `corporate_user_hierarchy` default to `0` when NULL.
- Numbers are never written in exponential notation (mobile/phone numbers, account numbers,
  `imsinumber`, `uuid`, ...), including text values an Excel round-trip stored as e.g.
  `2.60973641672E+11`.
- "same value" columns keep the source value: `bit` columns stay `1`/`0`, timestamps use the same
  `yyyy-MM-dd HH:mm:ss.SSS` format as the other outputs, `date` columns are `yyyy-MM-dd`.
- The source tables are read with `SELECT *`, so a column the sheet lists but the table doesn't have
  (e.g. `disable_user`/`display_banner`/`force_password_reset`/`migratedDate`/`imsi`, which aren't
  in `Migration_TableScripts.txt`) is logged once and written blank (`FALSE` for booleans).
- `users` and `devices` are always one file per country (regardless of `--split-by-country`), by the
  fixed `IswCountry` code of `Users.country_id` - for devices, the owning user's (`customer_id`).
  Unresolvable countries go to `_UNKNOWN`. `old_ussd_account` has no country link and is one file.
- `--customer-ids`/`--customer-usernames` restrict `users` (by `id`) and `devices` (by `customer_id`);
  `old_ussd_account` isn't filtered.

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
- `beneficiary_type`: `LOCAL`→`WIB`, `EFT`→`OBE`, `SchoolTransaction`→`STRAN` unconditionally. `INSTANT`/`RTGS`→`OBI`/`OBR`
  (respectively) for every country **except** Zambia, where both instead resolve the same way,
  purely from the row's bank (ignoring which of the two old `transactionType` values it actually
  was): `allowInstantTransfer=1` + `WALLET` institution → `IWL`; `allowInstantTransfer=1` + `BANK`
  institution → `OBI`; `allowInstantTransfer=0` → `OBR` (regardless of institution). When the bank
  or its `allowInstantTransfer` flag can't be resolved, Zambia falls back to the same default as
  every other country (`INSTANT`→`OBI`, `RTGS`→`OBR`). (An earlier version of this rule left
  Zambia `RTGS` unconditionally as `OBR`, reading the doc's dangling "Exception – Zambia" note
  under `RTGS` as a no-op reaffirmation; the note was actually incomplete in the doc, and the real
  target for a Zambia `RTGS` row with a `BANK`-institution/`allowInstantTransfer=1` bank is `OBI`,
  not `OBR`.)
- `bank_name` / `swift_code` come from the `Banks` table by `bank_id` (`swift_code` is actually
  `Banks.gatewayCode`, see `banks.column.swiftCode`).
- `wallet_type` / `wallet_code` ← `Banks.name` / `Banks.code`, only when Zambia + wallet
  institution + `allowInstantTransfer=1` (same condition as the `beneficiary_type` exception
  above); blank otherwise. Per `MigrationCorrection_Wallets.docx` - `wallet_type` previously held
  `Banks.code` with no separate `wallet_code` column populated at all.
- `bank_gateway_code` ← `Banks.swiftCode` (the `gatewayCode` column) for `RTGS` rows, `Banks.code`
  for `INSTANT` rows (wallet or not - both draw from `code`); blank for `LOCAL`/`EFT`/`SchoolTransaction`.
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
  - `beneficiary_type` (see `PaymentBeneficiaryTypeMapper`): keys off `Billers.gatewayCode`/
    `Products.gatewayCode`, not the raw `biller_id`/`product_id` - ids aren't stable across
    dev/production, unlike gateway codes (same convention as `admin_remarks`,
    `TransactionDTypeMapper`, and `WalletProductMapper`). Malawi → `IWL` when the row's resolved
    product is one of the eight wallet products below, else `BILL`. Botswana → `IWL` when
    `Billers.gatewayCode` is `BW-ORANGE-B2C` or `BW-MYZAKA-BW`, OR the row's resolved product is one
    of the eight wallet products below, else `BILL`. Zimbabwe/Zambia → always `BILL`; blank when the
    customer's country can't be resolved.
  - `beneficiary_country` uses the same fixed `IswCountry` lookup as `customer_country_code`.
  - `nickname` ← `additionalParams.customerName` (unlike `beneficiary_name`, this doesn't fall
    back to `destinationAccountName`/other JSON keys).
  - `wallet_type` / `wallet_code` ← `Products.name` / `Products.gatewayCode`, looked up by
    `product_id`, for Malawi/Botswana rows whose resolved `Products.gatewayCode` is one of a fixed
    set of eight wallet product gateway codes (`TNMB2W`, `AIRB2W`, `BW-ORANGE-B2C`, `COMVIVAB2W`,
    `COMVIVAAGENT`, `BW-MYZAKA-B2W`, `AIRTEL`, `TNMAGENTS` - see `WalletProductMapper`). This same
    resolved product also drives `beneficiary_type` above, so a row with `wallet_type`/
    `wallet_code` populated is always `IWL`, but not every `IWL` row has them populated - Botswana's
    biller-gateway-code rule can put a non-wallet product on `IWL`, leaving `wallet_type`/
    `wallet_code` blank.
  - `biller_name` ← `Billers.name`, looked up by `biller_id`.
  - `biller_input_value` ← `customerReferece` (sic - that's the actual column name on the source
    table).
  - `created_by` / `last_modified_by` ← `SYSTEM`; `last_modified_at` ← the migration run
    timestamp (same value as `TransferBeneficiaries.created_at`).
  - `admin_remarks` (see `PaymentBeneficiaryMapper.adminRemarks`): each rule is gated to a specific
    country. Malawi + `Billers.gatewayCode` is `"TNMBUN"` (TNM Bundles) → `additionalParams.productGatewayCode`;
    Malawi + `Billers.gatewayCode` is `"AIRTEL"` → `additionalParams.productGatewayCode`; Malawi +
    `Billers.gatewayCode` is `"MWB"` (Water Board Postpaid Bills) or `"MWBPREP"` (Water Board
    PrePaid Bills) → the row's own `product_id`. Zimbabwe + `Billers.gatewayCode` is
    `"CITYOFBULAWAYO"` → `additionalParams.utilityType`; Zimbabwe + `Billers.gatewayCode` is
    `"NYARADZO"` → static `"1"`; Zimbabwe + `Billers.gatewayCode` is `"ZIMRA"` → static `"Domestic"`;
    Zimbabwe + `Billers.gatewayCode` is `"CIMAS"` → `additionalParams.referenceType`. Blank for
    everything else.
  - `bank_gateway_code` ← `Billers.gatewayCode` by `biller_id`, only when `beneficiary_type` resolves
    to `BILL`; blank for `IWL` rows (which carry their gateway code in `wallet_code` instead, via
    `Products.gatewayCode`).

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
  - `LocalTransfer` and `SchoolTransaction` → `WIB`, unconditionally.
  - `BillPayment` → Zimbabwe/Zambia: always `BILL`. Malawi/Botswana: `IWL` when
    `Billers.gatewayCode` (looked up by `biller_id`) is a wallet biller — Malawi `TNMAGENTS`/
    `COMVIVAAGENT`/`COMVIVAB2W`, Botswana `BW-ORANGE-B2C`/`BW-MYZAKA-B2W` — or when the row's
    resolved product is one of the wallet products (`WalletProductMapper`); everything else
    (including an unresolved biller) is `BILL`.
  - `AirtimePurchase` → Zimbabwe/Zambia: always `BILL`. Botswana: the same wallet biller/product
    check as `BillPayment` — Orange Money Bank to Wallet is booked as an `AirtimePurchase` (biller
    `BW-ORANGE-B2C`, `product_id` 10144) and goes to `IWL`; Orange/Mascom airtime stays `BILL`.
    Malawi: looks up `product_id` in
    `Products` (**not** `Billers`/`biller_id` - Airtime keys off the product, unlike BillPayment
    which keys off the biller) and decides via the same wallet-product gatewayCode check used for
    `wallet_type`/`wallet_code` elsewhere (see `WalletProductMapper`) — a wallet product → `IWL`,
    any other resolvable product → `BILL`; an unresolvable `product_id` is left blank (warned).
  - `InterBankTransfer` → Botswana: always `OBE`. Zimbabwe: `destinationBank_id` is `NULL` →
    `OBZ` (ZIPIT), otherwise `OBR` (RTGS) — no bank lookup needed. Malawi:
    `Banks.allowInstantTransfer` by `destinationBank_id` (`true`→`OBI`, `false`→`OBR`). Zambia:
    a wallet-institution bank always wins → `IWL`; otherwise the same `allowInstantTransfer` flag
    (`true`→`OBI`, `false`→`OBR`) — the same wallet-clubbing behavior as
    `beneficiary_type`/`BeneficiaryTypeMapper` for Zambia.
  - Anything else (unrecognized `DTYPE`, unresolved country, or a lookup that can't be resolved)
    is logged as a warning and left blank rather than guessed.
- `biller_gateway_code` ← `Billers.gatewayCode` by `biller_id`, only when `dtype` resolves to `BILL`;
  blank for every other `dtype` (an unresolvable `biller_id` on a `BILL` row is logged as a warning,
  same pattern as the other lookup-driven fields).
- `transferprocessor` — for every country, when `dtype` resolves to `BILL`, this is overwritten with
  the same `Billers.gatewayCode` lookup as `biller_gateway_code` above (instead of the raw
  `Transactions.transferProcessor` value); left as the raw copied value for every other `dtype`.
- `wallet_type` / `wallet_code`, per `MigrationCorrection_Wallets.docx`: Malawi/Botswana rows join
  `Products` by `product_id`, then check the resolved `Products.gatewayCode` against the same fixed
  set of eight wallet product gateway codes as `beneficiary_details`'s `PaymentBeneficiaries`
  mapping (see `WalletProductMapper`) → `Products.name` / `Products.gatewayCode`. Zambia rows
  instead join `Banks` by `destinationBank_id`, only when that bank has `allowInstantTransfer=1`
  and a `WALLET` institution → `Banks.name` / `Banks.code` (same criteria as the Zambia wallet
  exception used elsewhere, but applied independently of `dtype` here rather than gating on it).
  For Malawi/Botswana rows that end up `IWL` because their *biller* is a wallet biller (above) but
  whose product isn't a wallet product, the biller's `Billers.name` / `Billers.gatewayCode` are used
  instead. Blank everywhere else. These two columns didn't exist in the `transaction` target schema before
  this correction.
- Every `Transactions` row-level validation warning that involves a resolved country now logs it
  (e.g. `country=MALAWI`) alongside the id, so a support engineer can tell at a glance which
  country's rules produced a given warning without cross-referencing the row.
- `--status-filter` (see Run below) restricts this migration to `Transactions` rows matching a
  given `status` (`SUCCESSFUL`/`SUCCESS`, `FAILED` or `PENDING`); omitted, every status is migrated. For
  Botswana and Zambia, a `status` of "Payment pending acknowledgement" (case-insensitive, spaces or
  underscores) counts as successful: those rows are included by `SUCCESSFUL` (and not by `FAILED` or `PENDING`),
  and their `status` is written out as `SUCCESSFUL`. Other countries' pending rows are untouched.
- `--transaction-months` (see Run below) limits this migration to the last 3 months of
  `Transactions` by default; see that flag for how the cutoff works and how to turn it off.
- `destinationaccountname` ← `destinationAccountName`, copied unconditionally for every transaction
  type including bill payments (it's a direct copy like the rest of the fields in the first bullet
  above - there's no dtype-based gating on it).
- `Banks.allowInstantTransfer` (config: `banks.column.allowInstantTransfer`, default
  `allowsInstantTransfer`) and the `Billers`/`Products` tables (schema undocumented, resolved
  dynamically like `Banks`/`CountryPartner` — see `billers.column.*`/`products.column.*`) are the
  new lookups this migration needed beyond what beneficiary_details/customer_info already used.
