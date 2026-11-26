-- Avaliação física (independente do planejamento): peso, medidas e fotos de evolução.
-- Todos os valores são opcionais; medida não informada simplesmente não tem linha.
-- As fotos ficam no storage (arquivos privados); o banco guarda só a referência.

CREATE TABLE body_assessments (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    assessed_on  DATE          NOT NULL,
    weight_kg    NUMERIC(5,2),
    height_cm    NUMERIC(4,1),
    body_fat_pct NUMERIC(4,1),
    notes        VARCHAR(2000),
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_body_assessments_id_user UNIQUE (id, user_id),
    CONSTRAINT ck_body_assessments_weight CHECK (weight_kg IS NULL OR weight_kg BETWEEN 20 AND 400),
    CONSTRAINT ck_body_assessments_height CHECK (height_cm IS NULL OR height_cm BETWEEN 50 AND 260),
    CONSTRAINT ck_body_assessments_fat CHECK (body_fat_pct IS NULL OR body_fat_pct BETWEEN 1 AND 75)
);

CREATE INDEX ix_body_assessments_user ON body_assessments (user_id, assessed_on DESC, created_at DESC);

CREATE TABLE body_measurements (
    id            UUID          PRIMARY KEY,
    assessment_id UUID          NOT NULL,
    user_id       UUID          NOT NULL,
    type          VARCHAR(20)   NOT NULL,
    value_cm      NUMERIC(5,1)  NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_body_measurements_type UNIQUE (assessment_id, type),
    CONSTRAINT fk_body_measurements_assessment FOREIGN KEY (assessment_id, user_id)
        REFERENCES body_assessments (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_body_measurements_type CHECK (type IN (
        'SHOULDERS', 'CHEST', 'WAIST', 'ABDOMEN', 'ARM_RIGHT', 'ARM_LEFT', 'FOREARM_RIGHT', 'FOREARM_LEFT',
        'THIGH_RIGHT', 'THIGH_LEFT', 'CALF_RIGHT', 'CALF_LEFT')),
    CONSTRAINT ck_body_measurements_value CHECK (value_cm BETWEEN 5 AND 300)
);

CREATE TABLE progress_photos (
    id            UUID          PRIMARY KEY,
    assessment_id UUID          NOT NULL,
    user_id       UUID          NOT NULL,
    position      VARCHAR(10)   NOT NULL,
    storage_key   VARCHAR(200)  NOT NULL,
    content_type  VARCHAR(30)   NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    width         INTEGER,
    height        INTEGER,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_progress_photos_key UNIQUE (storage_key),
    CONSTRAINT fk_progress_photos_assessment FOREIGN KEY (assessment_id, user_id)
        REFERENCES body_assessments (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_progress_photos_position CHECK (position IN ('FRONT', 'RIGHT', 'LEFT', 'BACK', 'OTHER')),
    CONSTRAINT ck_progress_photos_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_progress_photos_size CHECK (size_bytes > 0 AND size_bytes <= 10485760)
);

CREATE INDEX ix_progress_photos_assessment ON progress_photos (assessment_id);
CREATE INDEX ix_progress_photos_user ON progress_photos (user_id);
