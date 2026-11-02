# Spec Delta

## Purpose

Records every movement of money on an account as a durable, auditable ledger entry, so balances always have a trail and every money-moving feature (transfers, PIX, investments, loans) shares one source of truth.

## ADDED Requirements

### Requirement: Ledger entry per affected account
Every money movement SHALL be recorded as one ledger entry for each account it affects, carrying the account, a direction (DEBIT or CREDIT), the channel that originated it (TRANSFER, PIX, INVESTMENT or LOAN), a status, the amount, the account's balance after the movement, an optional description, and a creation timestamp.

#### Scenario: Entry carries all mandatory fields
- **WHEN** a ledger entry is recorded for an account
- **THEN** it has an account, direction, channel, status, amount, balance after and creation timestamp, and none of them is empty

#### Scenario: Entry must belong to an existing account
- **WHEN** a ledger entry references an account that does not exist
- **THEN** the entry is rejected and nothing is recorded

#### Scenario: Description is optional
- **WHEN** a ledger entry is recorded without a description
- **THEN** it is accepted

### Requirement: Amount is always positive
The amount of a ledger entry SHALL be strictly greater than zero; the direction alone expresses whether money left or entered the account. Amounts and balances SHALL use the same precision as the account balance (two decimal places).

#### Scenario: Zero amount rejected
- **WHEN** a ledger entry is recorded with amount 0.00
- **THEN** the entry is rejected

#### Scenario: Negative amount rejected
- **WHEN** a ledger entry is recorded with a negative amount
- **THEN** the entry is rejected

### Requirement: Legs of one operation share a grouping id
Every ledger entry SHALL carry a non-empty operation id. Entries produced by the same operation SHALL share it, and entries from different operations SHALL NOT.

#### Scenario: Transfer between two accounts
- **WHEN** an operation moves money from account A to account B
- **THEN** exactly two entries exist for it, a DEBIT on A and a CREDIT on B, with the same amount and the same operation id

#### Scenario: Single-account operation
- **WHEN** an operation affects only one account (for example, moving money into an investment)
- **THEN** exactly one entry exists for it, with its own operation id

#### Scenario: Counterpart lookup
- **WHEN** a consumer has one leg of a transfer
- **THEN** it can find the other leg through the shared operation id

### Requirement: Idempotency key unique per account
The idempotency key is optional and SHALL only be set on the leg that initiates the operation. Within one account, an idempotency key SHALL appear on at most one entry; the same key on different accounts SHALL be allowed, and entries without a key are unrestricted.

#### Scenario: Duplicate key on the same account rejected
- **WHEN** an entry is recorded on account A with idempotency key K, and K is already present on another entry of account A
- **THEN** the second entry is rejected and nothing is recorded

#### Scenario: Same key on different accounts allowed
- **WHEN** account A and account B each record an entry with idempotency key K
- **THEN** both entries are accepted

#### Scenario: Entries without a key are unrestricted
- **WHEN** several entries of the same account are recorded without an idempotency key
- **THEN** all of them are accepted

### Requirement: Entries are immutable except for status
After an entry is recorded, only its status SHALL change, among PENDING, COMPLETED and FAILED. Entries SHALL never be deleted by application code.

#### Scenario: Status transition allowed
- **WHEN** an entry recorded as PENDING is later marked COMPLETED or FAILED
- **THEN** the new status is persisted and every other field keeps its original value

#### Scenario: Synchronous movements are recorded as completed
- **WHEN** a movement is applied synchronously and succeeds
- **THEN** its entries are recorded with status COMPLETED

#### Scenario: Failed synchronous movement leaves no entries
- **WHEN** a synchronous movement fails before committing
- **THEN** none of its entries are recorded
