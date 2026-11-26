-- Regras de progressão por exercício (A12). Sem registro = regra padrão (dupla progressão).

CREATE TABLE progression_rules (
    id                UUID          PRIMARY KEY,
    user_id           UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    exercise_id       UUID          NOT NULL REFERENCES exercises (id) ON DELETE CASCADE,
    -- RIR mínimo em todas as séries para sugerir aumento (nulo = usar o RIR alvo do plano)
    min_rir           INTEGER,
    -- quantas sessões seguidas no teto da faixa antes de sugerir aumento
    sessions_at_top   INTEGER       NOT NULL DEFAULT 1,
    -- incremento de carga sugerido (nulo = padrão de 2,5 kg)
    load_increment_kg NUMERIC(5,2),
    enabled           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_progression_rules_exercise UNIQUE (user_id, exercise_id),
    CONSTRAINT ck_progression_rules_rir CHECK (min_rir IS NULL OR min_rir BETWEEN 0 AND 10),
    CONSTRAINT ck_progression_rules_sessions CHECK (sessions_at_top BETWEEN 1 AND 5),
    CONSTRAINT ck_progression_rules_increment CHECK (load_increment_kg IS NULL OR load_increment_kg > 0)
);

-- Consultas de evolução: execuções de um exercício do usuário, por data.
CREATE INDEX ix_workout_sessions_user_status_started ON workout_sessions (user_id, status, started_at DESC);
