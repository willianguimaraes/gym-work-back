-- Execução do treino: sessões, exercícios executados, séries e observações.
--
-- Planejamento não é execução: a sessão copia (snapshot) os nomes e a configuração planejada no
-- início. As referências ao plano são opcionais e viram NULL se o plano for alterado ou apagado
-- (ON DELETE SET NULL só da coluna do plano, preservando user_id). O histórico nunca é apagado
-- por cascata a partir do plano; cascata só existe dentro da própria sessão.

CREATE TABLE workout_sessions (
    id             UUID          PRIMARY KEY,
    user_id        UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan_id        UUID,
    phase_id       UUID,
    week_id        UUID,
    workout_day_id UUID,
    status         VARCHAR(20)   NOT NULL,
    started_at     TIMESTAMPTZ   NOT NULL,
    finished_at    TIMESTAMPTZ,
    notes          VARCHAR(2000),
    -- snapshot do planejamento no momento do início
    plan_name      VARCHAR(120),
    phase_name     VARCHAR(120),
    week_number    INTEGER,
    deload_week    BOOLEAN       NOT NULL DEFAULT FALSE,
    day_name       VARCHAR(60),
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_workout_sessions_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_workout_sessions_plan FOREIGN KEY (plan_id, user_id)
        REFERENCES training_plans (id, user_id) ON DELETE SET NULL (plan_id),
    CONSTRAINT fk_workout_sessions_phase FOREIGN KEY (phase_id, user_id)
        REFERENCES training_phases (id, user_id) ON DELETE SET NULL (phase_id),
    CONSTRAINT fk_workout_sessions_week FOREIGN KEY (week_id, user_id)
        REFERENCES training_weeks (id, user_id) ON DELETE SET NULL (week_id),
    CONSTRAINT fk_workout_sessions_day FOREIGN KEY (workout_day_id, user_id)
        REFERENCES workout_days (id, user_id) ON DELETE SET NULL (workout_day_id),
    CONSTRAINT ck_workout_sessions_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_workout_sessions_finish CHECK (
        (status = 'IN_PROGRESS' AND finished_at IS NULL)
        OR (status = 'COMPLETED' AND finished_at IS NOT NULL AND finished_at >= started_at))
);

-- No máximo uma sessão em andamento por usuário.
CREATE UNIQUE INDEX uk_workout_sessions_in_progress ON workout_sessions (user_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_workout_sessions_user_started ON workout_sessions (user_id, started_at DESC);
CREATE INDEX ix_workout_sessions_day ON workout_sessions (workout_day_id);

CREATE TABLE session_exercises (
    id                  UUID          PRIMARY KEY,
    session_id          UUID          NOT NULL,
    user_id             UUID          NOT NULL,
    exercise_id         UUID          NOT NULL REFERENCES exercises (id) ON DELETE RESTRICT,
    planned_exercise_id UUID,
    position            INTEGER       NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    substituted_by_id   UUID,
    notes               VARCHAR(2000),
    -- snapshot do exercício e da configuração planejada
    exercise_name       VARCHAR(120)  NOT NULL,
    equipment           VARCHAR(20)   NOT NULL,
    muscle_group        VARCHAR(30)   NOT NULL,
    planned_sets        INTEGER,
    reps_min            INTEGER,
    reps_max            INTEGER,
    rest_seconds        INTEGER,
    target_rir          INTEGER,
    reference_load_kg   NUMERIC(6,2),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_session_exercises_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_session_exercises_session FOREIGN KEY (session_id, user_id)
        REFERENCES workout_sessions (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_session_exercises_planned FOREIGN KEY (planned_exercise_id, user_id)
        REFERENCES planned_exercises (id, user_id) ON DELETE SET NULL (planned_exercise_id),
    CONSTRAINT fk_session_exercises_substituted FOREIGN KEY (substituted_by_id, user_id)
        REFERENCES session_exercises (id, user_id) ON DELETE SET NULL (substituted_by_id),
    CONSTRAINT ck_session_exercises_status CHECK (status IN ('PENDING', 'DONE', 'PARTIAL', 'SKIPPED', 'SUBSTITUTED'))
);

CREATE INDEX ix_session_exercises_session ON session_exercises (session_id, position);
CREATE INDEX ix_session_exercises_user_exercise ON session_exercises (user_id, exercise_id);

CREATE TABLE exercise_sets (
    id                  UUID          PRIMARY KEY,
    session_exercise_id UUID          NOT NULL,
    user_id             UUID          NOT NULL,
    -- chave gerada no aparelho: reenviar a mesma série (fila offline) não duplica o registro
    client_id           UUID,
    set_number          INTEGER       NOT NULL,
    set_type            VARCHAR(20)   NOT NULL,
    parent_set_id       UUID,
    status              VARCHAR(20)   NOT NULL,
    load_kg             NUMERIC(6,2),
    reps                INTEGER,
    rir                 INTEGER,
    rest_seconds        INTEGER,
    notes               VARCHAR(1000),
    performed_at        TIMESTAMPTZ   NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_exercise_sets_id_user UNIQUE (id, user_id),
    CONSTRAINT uk_exercise_sets_client UNIQUE (user_id, client_id),
    CONSTRAINT fk_exercise_sets_session_exercise FOREIGN KEY (session_exercise_id, user_id)
        REFERENCES session_exercises (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_exercise_sets_parent FOREIGN KEY (parent_set_id, user_id)
        REFERENCES exercise_sets (id, user_id) ON DELETE SET NULL (parent_set_id),
    CONSTRAINT ck_exercise_sets_type CHECK (set_type IN ('WARMUP', 'ACTIVATION', 'WORKING', 'DROP_SET', 'REST_PAUSE', 'EXTRA')),
    CONSTRAINT ck_exercise_sets_status CHECK (status IN ('COMPLETED', 'NOT_PERFORMED', 'INTERRUPTED', 'SUBSTITUTED')),
    CONSTRAINT ck_exercise_sets_completed CHECK (status <> 'COMPLETED' OR reps IS NOT NULL),
    CONSTRAINT ck_exercise_sets_load CHECK (load_kg IS NULL OR load_kg >= 0),
    CONSTRAINT ck_exercise_sets_reps CHECK (reps IS NULL OR reps BETWEEN 0 AND 1000),
    CONSTRAINT ck_exercise_sets_rir CHECK (rir IS NULL OR rir BETWEEN 0 AND 10),
    CONSTRAINT ck_exercise_sets_rest CHECK (rest_seconds IS NULL OR rest_seconds BETWEEN 0 AND 3600)
);

CREATE INDEX ix_exercise_sets_session_exercise ON exercise_sets (session_exercise_id, set_number);

CREATE TABLE workout_observations (
    id                  UUID          PRIMARY KEY,
    user_id             UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_id          UUID,
    session_exercise_id UUID,
    exercise_id         UUID          REFERENCES exercises (id) ON DELETE RESTRICT,
    category            VARCHAR(30)   NOT NULL,
    body_region         VARCHAR(20),
    body_side           VARCHAR(10),
    intensity           INTEGER,
    moment              VARCHAR(10),
    interrupted         BOOLEAN       NOT NULL DEFAULT FALSE,
    text                VARCHAR(2000),
    observed_at         TIMESTAMPTZ   NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    -- observação da sessão some com a sessão descartada; a que é só do usuário permanece
    CONSTRAINT fk_workout_observations_session FOREIGN KEY (session_id, user_id)
        REFERENCES workout_sessions (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_workout_observations_session_exercise FOREIGN KEY (session_exercise_id, user_id)
        REFERENCES session_exercises (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_workout_observations_category CHECK (category IN (
        'PAIN', 'DISCOMFORT', 'RECOVERY', 'SLEEP', 'ENERGY', 'EXECUTION_DIFFICULTY', 'EQUIPMENT_UNAVAILABLE', 'GENERAL')),
    CONSTRAINT ck_workout_observations_region CHECK (body_region IS NULL OR body_region IN (
        'NECK', 'SHOULDER', 'UPPER_BACK', 'LOWER_BACK', 'CHEST', 'ELBOW', 'WRIST', 'HAND', 'ABDOMEN',
        'HIP', 'GLUTE', 'THIGH', 'KNEE', 'CALF', 'ANKLE', 'FOOT', 'OTHER')),
    CONSTRAINT ck_workout_observations_side CHECK (body_side IS NULL OR body_side IN ('LEFT', 'RIGHT', 'BOTH')),
    CONSTRAINT ck_workout_observations_moment CHECK (moment IS NULL OR moment IN ('BEFORE', 'DURING', 'AFTER')),
    CONSTRAINT ck_workout_observations_intensity CHECK (intensity IS NULL OR intensity BETWEEN 0 AND 10),
    CONSTRAINT ck_workout_observations_body CHECK (
        category IN ('PAIN', 'DISCOMFORT') OR (body_region IS NULL AND body_side IS NULL))
);

CREATE INDEX ix_workout_observations_user ON workout_observations (user_id, observed_at DESC);
CREATE INDEX ix_workout_observations_session ON workout_observations (session_id);
CREATE INDEX ix_workout_observations_exercise ON workout_observations (user_id, exercise_id, observed_at);
