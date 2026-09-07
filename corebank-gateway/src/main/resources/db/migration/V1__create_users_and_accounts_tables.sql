CREATE TABLE users (
    id              uuid PRIMARY KEY,
    full_name       varchar(255) NOT NULL,
    cpf             varchar(255) NOT NULL UNIQUE,
    email           varchar(255) NOT NULL UNIQUE,
    phone           varchar(255),
    password_hash   varchar(255) NOT NULL,
    birth_date      date,
    street          varchar(255) NOT NULL,
    number          varchar(255) NOT NULL,
    complement      varchar(255),
    neighborhood    varchar(255) NOT NULL,
    city            varchar(255) NOT NULL,
    state           varchar(255) NOT NULL,
    zip_code        varchar(255) NOT NULL,
    created_at      timestamptz NOT NULL
);


CREATE TABLE accounts (
    id              uuid PRIMARY KEY,
    account_number  varchar(255) NOT NULL UNIQUE,
    agency_number   varchar(255) NOT NULL,
    balance         numeric(15, 2) NOT NULL DEFAULT 0.00,
    user_id         uuid NOT NULL UNIQUE REFERENCES users(id),
    created_at      timestamptz NOT NULL
);