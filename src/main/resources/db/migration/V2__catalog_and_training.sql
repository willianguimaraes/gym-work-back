-- Catálogo de exercícios e planejamento de treino.
--
-- Isolamento no banco: toda tabela do usuário tem user_id, e as filhas referenciam o pai por
-- FK composta (parent_id, user_id). Assim o PostgreSQL rejeita, por exemplo, um dia de treino
-- de um usuário pendurado na fase de outro, mesmo que haja bug no código.
-- Cascata só existe dentro do agregado do plano (plano -> fases -> semanas/dias -> exercícios).

-- ---------------------------------------------------------------------------------------------
-- Catálogo
-- ---------------------------------------------------------------------------------------------

-- Tabela de referência (seed). O código é estável e pode ser usado em templates de importação.
CREATE TABLE muscle_groups (
    code     VARCHAR(30)  PRIMARY KEY,
    name     VARCHAR(60)  NOT NULL,
    position SMALLINT     NOT NULL
);

-- owner_id nulo = exercício global (seed). Com dono = exercício personalizado do usuário.
CREATE TABLE exercises (
    id                   UUID          PRIMARY KEY,
    owner_id             UUID          REFERENCES users (id) ON DELETE CASCADE,
    name                 VARCHAR(120)  NOT NULL,
    -- nome em minúsculas e sem acentos, para a busca ("elevacao" encontra "Elevação")
    search_name          VARCHAR(120)  NOT NULL,
    primary_muscle_group VARCHAR(30)   NOT NULL REFERENCES muscle_groups (code),
    type                 VARCHAR(20)   NOT NULL,
    equipment            VARCHAR(20)   NOT NULL,
    description          VARCHAR(1000),
    instructions         VARCHAR(4000),
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_exercises_type CHECK (type IN ('COMPOUND', 'ISOLATION')),
    CONSTRAINT ck_exercises_equipment CHECK (equipment IN (
        'BARBELL', 'DUMBBELL', 'MACHINE', 'CABLE', 'SMITH', 'BODYWEIGHT', 'KETTLEBELL', 'BAND', 'OTHER')),
    CONSTRAINT ck_exercises_name CHECK (length(btrim(name)) > 0)
);

-- Mesmo nome + equipamento não pode se repetir no catálogo global nem entre os exercícios do usuário.
CREATE UNIQUE INDEX uk_exercises_global_name ON exercises (lower(name), equipment) WHERE owner_id IS NULL;
CREATE UNIQUE INDEX uk_exercises_owner_name ON exercises (owner_id, lower(name), equipment) WHERE owner_id IS NOT NULL;
CREATE INDEX ix_exercises_muscle ON exercises (primary_muscle_group);

CREATE TABLE exercise_secondary_muscles (
    exercise_id  UUID        NOT NULL REFERENCES exercises (id) ON DELETE CASCADE,
    muscle_group VARCHAR(30) NOT NULL REFERENCES muscle_groups (code),
    PRIMARY KEY (exercise_id, muscle_group)
);

-- ---------------------------------------------------------------------------------------------
-- Planejamento
-- ---------------------------------------------------------------------------------------------

CREATE TABLE training_plans (
    id                UUID          PRIMARY KEY,
    user_id           UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name              VARCHAR(120)  NOT NULL,
    goal              VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    start_date        DATE,
    expected_end_date DATE,
    notes             VARCHAR(2000),
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_training_plans_id_user UNIQUE (id, user_id),
    CONSTRAINT ck_training_plans_goal CHECK (goal IN ('HYPERTROPHY', 'STRENGTH', 'FAT_LOSS', 'MAINTENANCE', 'OTHER')),
    CONSTRAINT ck_training_plans_status CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT ck_training_plans_dates CHECK (
        start_date IS NULL OR expected_end_date IS NULL OR expected_end_date >= start_date)
);

-- No máximo um planejamento ativo por usuário (A4).
CREATE UNIQUE INDEX uk_training_plans_one_active ON training_plans (user_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_training_plans_user ON training_plans (user_id, created_at DESC);

CREATE TABLE training_phases (
    id          UUID          PRIMARY KEY,
    plan_id     UUID          NOT NULL,
    user_id     UUID          NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    position    INTEGER       NOT NULL,
    start_date  DATE,
    weeks_count INTEGER       NOT NULL,
    notes       VARCHAR(2000),
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_training_phases_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_training_phases_plan FOREIGN KEY (plan_id, user_id)
        REFERENCES training_plans (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_training_phases_weeks CHECK (weeks_count BETWEEN 1 AND 52)
);

CREATE INDEX ix_training_phases_plan ON training_phases (plan_id, position);

-- Semanas 1..N geradas a partir de weeks_count; guardam o contexto da semana (A1).
CREATE TABLE training_weeks (
    id          UUID          PRIMARY KEY,
    phase_id    UUID          NOT NULL,
    user_id     UUID          NOT NULL,
    week_number INTEGER       NOT NULL,
    start_date  DATE,
    is_deload   BOOLEAN       NOT NULL DEFAULT FALSE,
    notes       VARCHAR(2000),
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_training_weeks_id_user UNIQUE (id, user_id),
    CONSTRAINT uk_training_weeks_number UNIQUE (phase_id, week_number),
    CONSTRAINT fk_training_weeks_phase FOREIGN KEY (phase_id, user_id)
        REFERENCES training_phases (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_training_weeks_number CHECK (week_number >= 1)
);

-- Dias de treino pertencem à fase (modelo semanal, A1).
CREATE TABLE workout_days (
    id          UUID          PRIMARY KEY,
    phase_id    UUID          NOT NULL,
    user_id     UUID          NOT NULL,
    name        VARCHAR(60)   NOT NULL,
    day_of_week VARCHAR(10),
    position    INTEGER       NOT NULL,
    notes       VARCHAR(2000),
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_workout_days_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_workout_days_phase FOREIGN KEY (phase_id, user_id)
        REFERENCES training_phases (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_workout_days_dow CHECK (day_of_week IS NULL OR day_of_week IN (
        'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'))
);

CREATE INDEX ix_workout_days_phase ON workout_days (phase_id, position);

-- Configuração por exercício (A3). O exercício pode ser global ou personalizado do mesmo usuário
-- (checado no serviço); RESTRICT impede apagar um exercício que está em algum plano.
CREATE TABLE planned_exercises (
    id                UUID          PRIMARY KEY,
    workout_day_id    UUID          NOT NULL,
    user_id           UUID          NOT NULL,
    exercise_id       UUID          NOT NULL REFERENCES exercises (id) ON DELETE RESTRICT,
    position          INTEGER       NOT NULL,
    sets              INTEGER       NOT NULL,
    reps_min          INTEGER       NOT NULL,
    reps_max          INTEGER       NOT NULL,
    rest_seconds      INTEGER,
    suggested_load_kg NUMERIC(6,2),
    target_rir        INTEGER,
    notes             VARCHAR(2000),
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_planned_exercises_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_planned_exercises_day FOREIGN KEY (workout_day_id, user_id)
        REFERENCES workout_days (id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_planned_exercises_sets CHECK (sets BETWEEN 1 AND 20),
    CONSTRAINT ck_planned_exercises_reps CHECK (reps_min >= 1 AND reps_min <= reps_max AND reps_max <= 100),
    CONSTRAINT ck_planned_exercises_rest CHECK (rest_seconds IS NULL OR rest_seconds BETWEEN 0 AND 900),
    CONSTRAINT ck_planned_exercises_load CHECK (suggested_load_kg IS NULL OR suggested_load_kg >= 0),
    CONSTRAINT ck_planned_exercises_rir CHECK (target_rir IS NULL OR target_rir BETWEEN 0 AND 10)
);

CREATE INDEX ix_planned_exercises_day ON planned_exercises (workout_day_id, position);
CREATE INDEX ix_planned_exercises_exercise ON planned_exercises (exercise_id);
