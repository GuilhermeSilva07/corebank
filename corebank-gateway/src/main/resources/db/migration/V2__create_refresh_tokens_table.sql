
CREATE TABLE refresh_tokens (
    id              uuid PRIMARY KEY,
    token_hash      varchar(255) NOT NULL UNIQUE,
    user_id         uuid NOT NULL REFERENCES users(id),
    expires_at      timestamptz NOT NULL,
    created_at      timestamptz NOT NULL,
    revoked_at      timestamptz
);