# Account Transfer Specification

## Purpose

Lets the owner of an account move money to another account of the bank in a single atomic operation, with the balance checked and both sides recorded in the transaction ledger.

## Requirements

### Requirement: Authenticated owner initiates a transfer
The system SHALL accept a transfer request carrying an origin account, a destination account and an amount only from an authenticated user who owns the origin account. A request for an origin account that does not exist SHALL be rejected exactly like one for an account owned by another user.

#### Scenario: Request without a valid access token
- **WHEN** a transfer is requested without a valid access token
- **THEN** the request is rejected as unauthenticated (401) and nothing is moved

#### Scenario: Origin account belongs to another user
- **WHEN** an authenticated user requests a transfer from an account they do not own
- **THEN** the request is rejected as forbidden (403, ACCESS_DENIED) and nothing is moved

#### Scenario: Origin account does not exist
- **WHEN** an authenticated user requests a transfer from an account id that does not exist
- **THEN** the response is the same 403 ACCESS_DENIED as for another user's account

### Requirement: Amount is positive with at most two decimal places
The transfer amount SHALL be present, strictly greater than zero, and have at most two decimal places. An invalid amount SHALL be rejected as a validation error before any account is touched; it SHALL NOT be rounded.

#### Scenario: Missing amount
- **WHEN** a transfer is requested without an amount
- **THEN** the request is rejected as a validation error (400) naming the amount field

#### Scenario: Zero or negative amount
- **WHEN** a transfer is requested with amount 0.00 or a negative amount
- **THEN** the request is rejected as a validation error (400) and nothing is moved

#### Scenario: More than two decimal places
- **WHEN** a transfer is requested with amount 10.005
- **THEN** the request is rejected as a validation error (400) instead of being rounded

### Requirement: Origin and destination must differ
A transfer whose origin and destination are the same account SHALL be rejected with a clear error and SHALL NOT change any balance or record any ledger entry.

#### Scenario: Self-transfer
- **WHEN** the owner of account A requests a transfer from A to A
- **THEN** the request is rejected (422, SELF_TRANSFER_NOT_ALLOWED) and the balance of A is unchanged

### Requirement: Destination account must exist
A transfer to an account that does not exist SHALL be rejected with a not-found error and SHALL leave the origin balance and the ledger untouched.

#### Scenario: Unknown destination
- **WHEN** the owner of account A requests a transfer to an account id that does not exist
- **THEN** the request is rejected (404, DESTINATION_ACCOUNT_NOT_FOUND), the balance of A is unchanged and no ledger entry is recorded

### Requirement: Origin balance must cover the amount
The origin balance SHALL be checked against the amount within the same atomic operation that debits it. When the balance is lower than the amount, the transfer SHALL be rejected with an error that states the shortfall, and nothing SHALL be written. A transfer that leaves the origin balance at exactly zero SHALL be accepted.

#### Scenario: Insufficient balance
- **WHEN** account A has balance 30.00 and its owner requests a transfer of 50.00
- **THEN** the request is rejected (422, INSUFFICIENT_BALANCE) with a message stating the 20.00 shortfall, both balances are unchanged and no ledger entry is recorded

#### Scenario: Transfer of the entire balance
- **WHEN** account A has balance 50.00 and its owner requests a transfer of 50.00
- **THEN** the transfer succeeds and the balance of A becomes 0.00

### Requirement: Successful transfer moves money atomically
A successful transfer SHALL, as one atomic operation, decrease the origin balance by the amount, increase the destination balance by the amount, and record exactly two ledger entries: a DEBIT on the origin and a CREDIT on the destination, sharing one operation id, with channel TRANSFER, status COMPLETED and each account's resulting balance.

#### Scenario: Successful transfer
- **WHEN** account A has 100.00, account B has 10.00, and the owner of A transfers 40.00 to B
- **THEN** A has 60.00, B has 50.00, and exactly two new ledger entries exist: a DEBIT of 40.00 on A with balance after 60.00 and a CREDIT of 40.00 on B with balance after 50.00, both with the same operation id

#### Scenario: Response describes the transfer
- **WHEN** a transfer succeeds
- **THEN** the response is 201 and carries the operation id, the origin and destination accounts, the amount, the origin balance after the transfer and the creation timestamp

#### Scenario: Failure after part of the work leaves no partial state
- **WHEN** any step of the transfer fails after the origin was debited but before the operation completes
- **THEN** both balances keep their values from before the request and no ledger entry of that operation is recorded

### Requirement: Concurrent transfers never overdraw an account
Transfers that touch the same account at the same time SHALL be applied one after another, so that every balance check sees the balance left by the previous transfer, and two transfers between the same pair of accounts in opposite directions SHALL NOT block each other forever.

#### Scenario: Two simultaneous transfers exceed the balance together
- **WHEN** account A has 100.00 and two transfers of 70.00 from A are requested at the same time
- **THEN** exactly one succeeds, the other is rejected with INSUFFICIENT_BALANCE, and the balance of A ends at 30.00

#### Scenario: Opposite transfers at the same time
- **WHEN** a transfer from A to B and a transfer from B to A are requested at the same time, both covered by their balances
- **THEN** both complete and the final balances reflect both transfers
