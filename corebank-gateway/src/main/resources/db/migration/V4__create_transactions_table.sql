CREATE TABLE transactions (
    id               uuid PRIMARY KEY,
    transfer_id      uuid NOT NULL,
    account_id       uuid NOT NULL REFERENCES accounts(id),
    type             varchar(255) NOT NULL,
    channel          varchar(255) NOT NULL,
    status           varchar(255) NOT NULL,
    amount           numeric(15, 2) NOT NULL,
    balance_after    numeric(15, 2) NOT NULL,
    description      varchar(255),
    idempotency_key  varchar(255),
    created_at       timestamptz NOT NULL,
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT uq_transactions_account_idempotency_key UNIQUE (account_id, idempotency_key)
);

CREATE INDEX idx_transactions_account_created_at ON transactions (account_id, created_at DESC);

CREATE INDEX idx_transactions_transfer_id ON transactions (transfer_id);
