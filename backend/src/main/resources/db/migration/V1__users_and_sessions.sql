-- Accounts and server-side sessions (Phase 3).

-- Emails are stored lower-cased by the application; the CHECK makes the database enforce it, so the
-- plain UNIQUE constraint is effectively case-insensitive without the citext extension.
CREATE TABLE app_user (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name     text        NOT NULL CHECK (char_length(full_name) BETWEEN 1 AND 100),
    email         text        NOT NULL UNIQUE CHECK (email = lower(email) AND char_length(email) <= 254),
    password_hash text        NOT NULL,
    enabled       boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);

-- Spring Session JDBC schema, copied from spring-session-jdbc 4.1.1 (schema-postgresql.sql).
-- Flyway owns it so the schema is versioned with everything else (spring.session.jdbc.initialize-schema=never).
-- PRINCIPAL_NAME holds the user id, which lets us find and delete a user's sessions.
CREATE TABLE spring_session (
    primary_id            char(36)     NOT NULL,
    session_id            char(36)     NOT NULL,
    creation_time         bigint       NOT NULL,
    last_access_time      bigint       NOT NULL,
    max_inactive_interval int          NOT NULL,
    expiry_time           bigint       NOT NULL,
    principal_name        varchar(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id char(36)     NOT NULL,
    attribute_name     varchar(200) NOT NULL,
    attribute_bytes    bytea        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
