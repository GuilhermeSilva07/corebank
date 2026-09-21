ALTER TABLE refresh_tokens
    ADD COLUMN version bigint NOT NULL DEFAULT 0;
