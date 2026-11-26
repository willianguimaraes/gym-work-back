-- Usuários, perfil/configurações e tokens de autenticação.
-- IDs são UUID (v7, gerados pela aplicação). Tokens são guardados apenas como hash SHA-256.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name          VARCHAR(120) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);

CREATE TABLE user_profiles (
    user_id              UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    height_cm            NUMERIC(4,1),
    birth_date           DATE,
    weight_unit          VARCHAR(2)   NOT NULL DEFAULT 'KG',
    length_unit          VARCHAR(2)   NOT NULL DEFAULT 'CM',
    theme                VARCHAR(10)  NOT NULL DEFAULT 'DARK',
    default_rest_seconds INTEGER      NOT NULL DEFAULT 90,
    rest_sound_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    timezone             VARCHAR(64)  NOT NULL DEFAULT 'America/Sao_Paulo',
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_profiles_height CHECK (height_cm IS NULL OR (height_cm > 50 AND height_cm < 260)),
    CONSTRAINT ck_user_profiles_weight_unit CHECK (weight_unit IN ('KG', 'LB')),
    CONSTRAINT ck_user_profiles_length_unit CHECK (length_unit IN ('CM', 'IN')),
    CONSTRAINT ck_user_profiles_theme CHECK (theme IN ('DARK', 'LIGHT', 'SYSTEM')),
    CONSTRAINT ck_user_profiles_rest CHECK (default_rest_seconds BETWEEN 0 AND 900)
);

-- Refresh tokens com rotação: cada uso gera um novo token na mesma família.
-- Reuso de um token já trocado revoga a família inteira.
CREATE TABLE refresh_tokens (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id      UUID         NOT NULL,
    token_hash     VARCHAR(64)  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    replaced_by_id UUID,
    user_agent     VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);

CREATE TABLE password_reset_tokens (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);
