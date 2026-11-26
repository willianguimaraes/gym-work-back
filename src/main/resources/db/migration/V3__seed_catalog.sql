-- Grupos musculares e catálogo global de exercícios (owner_id nulo), conforme A9.

INSERT INTO muscle_groups (code, name, position) VALUES
    ('CHEST',      'Peito',               1),
    ('BACK',       'Costas',              2),
    ('SHOULDERS',  'Ombros',              3),
    ('TRAPEZIUS',  'Trapézio',            4),
    ('BICEPS',     'Bíceps',              5),
    ('TRICEPS',    'Tríceps',             6),
    ('FOREARMS',   'Antebraços',          7),
    ('ABS',        'Abdômen',             8),
    ('LOWER_BACK', 'Lombar',              9),
    ('GLUTES',     'Glúteos',            10),
    ('QUADRICEPS', 'Quadríceps',         11),
    ('HAMSTRINGS', 'Posteriores de coxa', 12),
    ('ADDUCTORS',  'Adutores',           13),
    ('ABDUCTORS',  'Abdutores',          14),
    ('CALVES',     'Panturrilhas',       15);

CREATE TEMPORARY TABLE seed_exercises (
    name        VARCHAR(120),
    muscle      VARCHAR(30),
    type        VARCHAR(20),
    equipment   VARCHAR(20),
    secondaries TEXT[]
) ON COMMIT DROP;

INSERT INTO seed_exercises (name, muscle, type, equipment, secondaries) VALUES
    -- Peito
    ('Supino reto com barra',              'CHEST', 'COMPOUND',  'BARBELL',    '{TRICEPS,SHOULDERS}'),
    ('Supino reto com halteres',           'CHEST', 'COMPOUND',  'DUMBBELL',   '{TRICEPS,SHOULDERS}'),
    ('Supino reto máquina',                'CHEST', 'COMPOUND',  'MACHINE',    '{TRICEPS,SHOULDERS}'),
    ('Supino inclinado com barra',         'CHEST', 'COMPOUND',  'BARBELL',    '{SHOULDERS,TRICEPS}'),
    ('Supino inclinado com halteres',      'CHEST', 'COMPOUND',  'DUMBBELL',   '{SHOULDERS,TRICEPS}'),
    ('Supino inclinado máquina',           'CHEST', 'COMPOUND',  'MACHINE',    '{SHOULDERS,TRICEPS}'),
    ('Supino inclinado no Smith',          'CHEST', 'COMPOUND',  'SMITH',      '{SHOULDERS,TRICEPS}'),
    ('Supino declinado com barra',         'CHEST', 'COMPOUND',  'BARBELL',    '{TRICEPS}'),
    ('Crucifixo com halteres',             'CHEST', 'ISOLATION', 'DUMBBELL',   '{SHOULDERS}'),
    ('Crucifixo máquina (peck deck)',      'CHEST', 'ISOLATION', 'MACHINE',    '{SHOULDERS}'),
    ('Crossover na polia',                 'CHEST', 'ISOLATION', 'CABLE',      '{SHOULDERS}'),
    ('Flexão de braço',                    'CHEST', 'COMPOUND',  'BODYWEIGHT', '{TRICEPS,SHOULDERS}'),
    -- Costas
    ('Puxada frontal aberta',              'BACK', 'COMPOUND',  'CABLE',      '{BICEPS}'),
    ('Puxada neutra',                      'BACK', 'COMPOUND',  'CABLE',      '{BICEPS}'),
    ('Puxada supinada',                    'BACK', 'COMPOUND',  'CABLE',      '{BICEPS}'),
    ('Barra fixa',                         'BACK', 'COMPOUND',  'BODYWEIGHT', '{BICEPS}'),
    ('Remada curvada com barra',           'BACK', 'COMPOUND',  'BARBELL',    '{BICEPS,LOWER_BACK}'),
    ('Remada unilateral com halter',       'BACK', 'COMPOUND',  'DUMBBELL',   '{BICEPS}'),
    ('Remada baixa na polia',              'BACK', 'COMPOUND',  'CABLE',      '{BICEPS}'),
    ('Remada articulada',                  'BACK', 'COMPOUND',  'MACHINE',    '{BICEPS}'),
    ('Remada cavalinho',                   'BACK', 'COMPOUND',  'BARBELL',    '{BICEPS,LOWER_BACK}'),
    ('Pulldown com braços estendidos',     'BACK', 'ISOLATION', 'CABLE',      '{}'),
    ('Levantamento terra',                 'BACK', 'COMPOUND',  'BARBELL',    '{GLUTES,HAMSTRINGS,LOWER_BACK}'),
    -- Ombros e trapézio
    ('Desenvolvimento com halteres',       'SHOULDERS', 'COMPOUND',  'DUMBBELL', '{TRICEPS}'),
    ('Desenvolvimento com barra',          'SHOULDERS', 'COMPOUND',  'BARBELL',  '{TRICEPS}'),
    ('Desenvolvimento máquina',            'SHOULDERS', 'COMPOUND',  'MACHINE',  '{TRICEPS}'),
    ('Elevação lateral com halteres',      'SHOULDERS', 'ISOLATION', 'DUMBBELL', '{}'),
    ('Elevação lateral na polia',          'SHOULDERS', 'ISOLATION', 'CABLE',    '{}'),
    ('Elevação lateral máquina',           'SHOULDERS', 'ISOLATION', 'MACHINE',  '{}'),
    ('Elevação frontal com halteres',      'SHOULDERS', 'ISOLATION', 'DUMBBELL', '{}'),
    ('Crucifixo inverso com halteres',     'SHOULDERS', 'ISOLATION', 'DUMBBELL', '{BACK}'),
    ('Crucifixo inverso máquina',          'SHOULDERS', 'ISOLATION', 'MACHINE',  '{BACK}'),
    ('Face pull',                          'SHOULDERS', 'ISOLATION', 'CABLE',    '{TRAPEZIUS}'),
    ('Encolhimento com halteres',          'TRAPEZIUS', 'ISOLATION', 'DUMBBELL', '{}'),
    -- Bíceps, tríceps e antebraços
    ('Rosca direta com barra',             'BICEPS', 'ISOLATION', 'BARBELL',  '{FOREARMS}'),
    ('Rosca alternada com halteres',       'BICEPS', 'ISOLATION', 'DUMBBELL', '{FOREARMS}'),
    ('Rosca martelo',                      'BICEPS', 'ISOLATION', 'DUMBBELL', '{FOREARMS}'),
    ('Rosca inclinada com halteres',       'BICEPS', 'ISOLATION', 'DUMBBELL', '{}'),
    ('Rosca Scott máquina',                'BICEPS', 'ISOLATION', 'MACHINE',  '{}'),
    ('Rosca na polia',                     'BICEPS', 'ISOLATION', 'CABLE',    '{}'),
    ('Tríceps corda',                      'TRICEPS', 'ISOLATION', 'CABLE',      '{}'),
    ('Tríceps pulley',                     'TRICEPS', 'ISOLATION', 'CABLE',      '{}'),
    ('Tríceps francês com halter',         'TRICEPS', 'ISOLATION', 'DUMBBELL',   '{}'),
    ('Tríceps testa com barra',            'TRICEPS', 'ISOLATION', 'BARBELL',    '{}'),
    ('Mergulho nas paralelas',             'TRICEPS', 'COMPOUND',  'BODYWEIGHT', '{CHEST,SHOULDERS}'),
    ('Supino fechado',                     'TRICEPS', 'COMPOUND',  'BARBELL',    '{CHEST}'),
    ('Rosca de punho',                     'FOREARMS', 'ISOLATION', 'DUMBBELL',  '{}'),
    -- Pernas
    ('Agachamento livre',                  'QUADRICEPS', 'COMPOUND',  'BARBELL',  '{GLUTES,HAMSTRINGS,LOWER_BACK}'),
    ('Agachamento no Smith',               'QUADRICEPS', 'COMPOUND',  'SMITH',    '{GLUTES}'),
    ('Agachamento búlgaro',                'QUADRICEPS', 'COMPOUND',  'DUMBBELL', '{GLUTES}'),
    ('Leg press 45°',                      'QUADRICEPS', 'COMPOUND',  'MACHINE',  '{GLUTES}'),
    ('Hack machine',                       'QUADRICEPS', 'COMPOUND',  'MACHINE',  '{GLUTES}'),
    ('Afundo com halteres',                'QUADRICEPS', 'COMPOUND',  'DUMBBELL', '{GLUTES}'),
    ('Cadeira extensora',                  'QUADRICEPS', 'ISOLATION', 'MACHINE',  '{}'),
    ('Mesa flexora',                       'HAMSTRINGS', 'ISOLATION', 'MACHINE',  '{}'),
    ('Cadeira flexora',                    'HAMSTRINGS', 'ISOLATION', 'MACHINE',  '{}'),
    ('Stiff com barra',                    'HAMSTRINGS', 'COMPOUND',  'BARBELL',  '{GLUTES,LOWER_BACK}'),
    ('Levantamento terra romeno com halteres', 'HAMSTRINGS', 'COMPOUND', 'DUMBBELL', '{GLUTES,LOWER_BACK}'),
    ('Elevação pélvica com barra',         'GLUTES', 'COMPOUND',  'BARBELL', '{HAMSTRINGS}'),
    ('Glúteo na polia (coice)',            'GLUTES', 'ISOLATION', 'CABLE',   '{}'),
    ('Cadeira abdutora',                   'ABDUCTORS', 'ISOLATION', 'MACHINE', '{GLUTES}'),
    ('Cadeira adutora',                    'ADDUCTORS', 'ISOLATION', 'MACHINE', '{}'),
    ('Panturrilha em pé máquina',          'CALVES', 'ISOLATION', 'MACHINE', '{}'),
    ('Panturrilha sentado',                'CALVES', 'ISOLATION', 'MACHINE', '{}'),
    ('Panturrilha no leg press',           'CALVES', 'ISOLATION', 'MACHINE', '{}'),
    -- Abdômen e lombar
    ('Abdominal crunch',                   'ABS', 'ISOLATION', 'BODYWEIGHT', '{}'),
    ('Abdominal na polia',                 'ABS', 'ISOLATION', 'CABLE',      '{}'),
    ('Elevação de pernas',                 'ABS', 'ISOLATION', 'BODYWEIGHT', '{}'),
    ('Prancha',                            'ABS', 'ISOLATION', 'BODYWEIGHT', '{}'),
    ('Extensão lombar (banco romano)',     'LOWER_BACK', 'ISOLATION', 'BODYWEIGHT', '{GLUTES,HAMSTRINGS}');

INSERT INTO exercises (id, owner_id, name, search_name, primary_muscle_group, type, equipment, active, created_at, updated_at)
SELECT gen_random_uuid(), NULL, name,
       translate(lower(name), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc'),
       muscle, type, equipment, TRUE, now(), now()
FROM seed_exercises;

INSERT INTO exercise_secondary_muscles (exercise_id, muscle_group)
SELECT e.id, s.muscle_group
FROM seed_exercises se
JOIN exercises e ON e.owner_id IS NULL AND e.name = se.name AND e.equipment = se.equipment
CROSS JOIN LATERAL unnest(se.secondaries) AS s (muscle_group);
