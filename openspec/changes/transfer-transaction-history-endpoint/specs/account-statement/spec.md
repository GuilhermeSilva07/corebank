## Purpose

Lets the owner of an account read the movements recorded in the transaction ledger for that account, page by page and optionally filtered by period and direction, so the balance always has a visible trail.

## ADDED Requirements

### Requirement: Only the authenticated owner reads an account's history
The system SHALL return the history of an account only to an authenticated user who owns it. A request for an account that does not exist SHALL be rejected exactly like one for an account owned by another user, so the response does not reveal which account ids exist.

#### Scenario: Request without a valid access token
- **WHEN** the history of an account is requested without a valid access token
- **THEN** the request is rejected as unauthenticated (401)

#### Scenario: Account belongs to another user
- **WHEN** an authenticated user requests the history of an account they do not own
- **THEN** the request is rejected as forbidden (403, ACCESS_DENIED) and no entry is returned

#### Scenario: Account does not exist
- **WHEN** an authenticated user requests the history of an account id that does not exist
- **THEN** the response is the same 403 ACCESS_DENIED as for another user's account

### Requirement: History lists the account's completed ledger entries
The history SHALL list the ledger entries of the requested account with status COMPLETED, and only those. Each item SHALL carry the entry id, the direction (DEBIT or CREDIT), the amount, the creation timestamp and the account's balance after that movement.

#### Scenario: Account with movements
- **WHEN** account A received a CREDIT of 100.00 and later had a DEBIT of 40.00, and its owner requests the history
- **THEN** the response lists two items: a DEBIT of 40.00 with running balance 60.00 and a CREDIT of 100.00 with running balance 100.00

#### Scenario: Entries of other accounts are never listed
- **WHEN** the owner of account A requests its history after a transfer from A to account B
- **THEN** the response contains the DEBIT on A and does not contain the CREDIT on B

#### Scenario: Account without movements
- **WHEN** the owner of an account that never moved money requests its history
- **THEN** the response is 200 with an empty item list and totalElements 0

#### Scenario: Entries not yet completed are hidden
- **WHEN** an account has one COMPLETED entry and one PENDING or FAILED entry
- **THEN** only the COMPLETED entry is listed and it is the only one counted in totalElements

### Requirement: Newest entries come first with a stable order
Items SHALL be ordered by creation timestamp, newest first. Entries with the same timestamp SHALL always be returned in the same relative order, so that walking the pages never repeats or skips an entry.

#### Scenario: Default ordering
- **WHEN** an account has entries created on November 1, 3 and 2, and its owner requests the history
- **THEN** the items come in the order November 3, November 2, November 1

#### Scenario: Paging through entries with equal timestamps
- **WHEN** several entries of the account share the same creation timestamp and the owner reads every page in sequence
- **THEN** each entry appears exactly once across all pages

### Requirement: History is paginated
The history SHALL be paginated with a zero-based page number (default 0) and a page size (default 20, allowed from 1 to 100). The response SHALL report the page, the size, the total number of matching entries and the total number of pages. Invalid pagination SHALL be rejected (400, INVALID_PAGINATION).

#### Scenario: Second page
- **WHEN** an account has 25 completed entries and its owner requests page 1 with size 20
- **THEN** the response lists the 5 oldest entries, with page 1, size 20, totalElements 25 and totalPages 2

#### Scenario: Page beyond the last
- **WHEN** an account has 5 entries and its owner requests page 3 with size 20
- **THEN** the response is 200 with an empty item list and totalElements 5

#### Scenario: Invalid page size
- **WHEN** the history is requested with size 0 or size 101, or with a negative page
- **THEN** the request is rejected (400, INVALID_PAGINATION)

### Requirement: Filter by period
The history SHALL accept optional `from` and `to` dates (YYYY-MM-DD), both inclusive, interpreted as calendar days in the America/Sao_Paulo time zone. Either may be given alone. When both are given, `from` SHALL NOT be after `to`.

#### Scenario: Both bounds inclusive
- **WHEN** an account has entries on November 1, 2 and 3 (São Paulo time) and its owner requests from 2026-11-01 to 2026-11-02
- **THEN** only the entries of November 1 and 2 are listed, including one created at 23:59 on November 2

#### Scenario: Day boundary follows São Paulo time
- **WHEN** an entry is created at 01:00 UTC on November 3, which is 22:00 on November 2 in São Paulo, and the owner requests from 2026-11-02 to 2026-11-02
- **THEN** that entry is listed

#### Scenario: Only the start date
- **WHEN** the owner requests the history with from 2026-11-02 and no `to`
- **THEN** every completed entry from November 2 onward is listed

#### Scenario: Only the end date
- **WHEN** the owner requests the history with to 2026-11-02 and no `from`
- **THEN** every completed entry up to and including November 2 is listed

#### Scenario: Start after end
- **WHEN** the owner requests the history with from 2026-11-05 and to 2026-11-01
- **THEN** the request is rejected (400, INVALID_FILTER)

### Requirement: Filter by direction
The history SHALL accept an optional `type` of DEBIT or CREDIT that keeps only entries with that direction. Without it, both directions are listed.

#### Scenario: Only debits
- **WHEN** an account has two DEBIT and one CREDIT entries and its owner requests type DEBIT
- **THEN** only the two DEBIT entries are listed and totalElements is 2

#### Scenario: Unknown type
- **WHEN** the history is requested with type FOO
- **THEN** the request is rejected (400, INVALID_FILTER) in the standard error format

### Requirement: Filters compose
When several filters are given, the history SHALL list only the entries that satisfy all of them, keeping the newest-first order and the pagination rules, and totalElements SHALL count only the matching entries.

#### Scenario: Period and direction together
- **WHEN** an account has a DEBIT and a CREDIT on November 1 and a DEBIT on November 3, and its owner requests from 2026-11-01 to 2026-11-02 with type DEBIT
- **THEN** only the DEBIT of November 1 is listed and totalElements is 1

### Requirement: Malformed filter values are rejected in the standard format
A filter value that cannot be read, such as a date that is not in YYYY-MM-DD format, SHALL be rejected with status 400 and code INVALID_FILTER in the standard error body `{message, code, fieldErrors}`, naming the offending parameter.

#### Scenario: Malformed date
- **WHEN** the history is requested with from 01/11/2026
- **THEN** the request is rejected (400, INVALID_FILTER) and the error names the `from` parameter

### Requirement: Statement and history share one contract
The account statement and the account transaction history SHALL return the same response format. The statement SHALL return exactly what the history returns for the same account and pagination when no filter is given.

#### Scenario: Statement shows real entries
- **WHEN** account A has completed ledger entries and its owner requests the statement
- **THEN** the statement lists those entries instead of an empty list

#### Scenario: Statement equals unfiltered history
- **WHEN** the owner of account A requests the statement and the unfiltered history with the same page and size
- **THEN** both responses have the same items in the same order and the same page, size, totalElements and totalPages
