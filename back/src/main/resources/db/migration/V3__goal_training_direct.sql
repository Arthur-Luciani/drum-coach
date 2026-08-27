-- ADR-0009: Treino pertence direto a Meta - remove o conceito de Plano (training_plan).
-- Tambem cobre as mudancas de schema listadas nas "Consequencias" do ADR-0008:
-- exercise.target_duration_seconds, execution_exercise_log.actual_duration_seconds e
-- goal.in_focus.
--
-- REGRA: nunca editar uma migration ja aplicada (V1__init.sql, V2__training_and_more.sql)
-- - toda mudanca de schema entra como um novo arquivo V<N>__descricao.sql, como este.
--
-- Estrategia para `training` e `lesson` (troca/remocao de coluna com FK): o driver SQLite
-- usado neste projeto tem suporte limitado a ALTER TABLE (sem DROP COLUMN nem troca de FK
-- de forma direta/portavel). Por isso usamos o padrao "recriar tabela": criar a tabela
-- `_new` com o schema final, copiar os dados possiveis da tabela antiga, dropar a antiga
-- e renomear a nova para o nome definitivo. Como o banco real do usuario ainda nao tem
-- nenhum dado de treino/plano/aula cadastrado, nao ha migracao de dados de fato aqui
-- (plan_id nao tem correspondente em goal_id, e generated_plan_id apontava para
-- training_plan, nao para training - ambos os campos entram sempre NULL na tabela nova) -
-- so garantimos que o schema final fique correto.

-- 1. Remove o conceito de Plano por completo (ver ADR-0009).
DROP TABLE training_plan;

-- 2. training: plan_id -> goal_id (nullable FK -> goal, treino direto da meta).
CREATE TABLE training_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  goal_id INTEGER REFERENCES goal(id),
  name TEXT NOT NULL,
  description TEXT,
  target_duration_minutes INTEGER NOT NULL,
  target_repetitions INTEGER,
  order_index INTEGER NOT NULL,
  created_by TEXT NOT NULL CHECK (created_by IN ('USER','CLAUDE')),
  last_modified_by TEXT NOT NULL CHECK (last_modified_by IN ('USER','CLAUDE')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

INSERT INTO training_new (id, goal_id, name, description, target_duration_minutes,
    target_repetitions, order_index, created_by, last_modified_by, created_at, updated_at)
SELECT id, NULL, name, description, target_duration_minutes, target_repetitions,
    order_index, created_by, last_modified_by, created_at, updated_at
FROM training;

DROP TABLE training;
ALTER TABLE training_new RENAME TO training;

-- 3. exercise.target_duration_seconds (novo, nullable) - duracao alvo do exercicio, usada
--    pelo timer automatico do Modo Sessao (ver ADR-0008). ADD COLUMN simples cobre este
--    caso (coluna nova nullable, sem trocar/remover nada existente).
ALTER TABLE exercise ADD COLUMN target_duration_seconds INTEGER;

-- 4. execution_exercise_log.actual_duration_seconds (novo, nullable) - tempo realmente
--    gasto no exercicio dentro da execucao, capturado automaticamente pelo Modo Sessao.
--    Tabela filha sem auditoria propria - inalterado (ver ADR-0005).
ALTER TABLE execution_exercise_log ADD COLUMN actual_duration_seconds INTEGER;

-- 5. goal.in_focus (novo) - meta em foco no Dashboard (ver ADR-0008/ADR-0009). SQLite nao
--    tem tipo BOOLEAN nativo: usamos INTEGER 0/1, mapeado direto para boolean Java sem
--    conversor customizado (o driver JDBC ja lida com essa conversao nativamente, ao
--    contrario de Instant/LocalDate/enums - ver JdbcConfig). A aplicacao garante no
--    maximo um in_focus=1 por vez (UpdateGoalUseCase desfoca as demais na mesma
--    transacao antes de focar uma nova).
ALTER TABLE goal ADD COLUMN in_focus INTEGER NOT NULL DEFAULT 0;

-- 6. lesson.generated_plan_id -> lesson.generated_training_id (FK -> training).
--    Consequencia direta de remover training_plan: uma aula gerada passa a apontar para o
--    Treino criado a partir dela, nao mais para um Plano (que deixou de existir). Recria a
--    tabela pelo mesmo motivo/estrategia do item 2.
CREATE TABLE lesson_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  lesson_date TEXT NOT NULL,
  teacher_notes TEXT NOT NULL,
  feedback TEXT,
  focus_until_next TEXT,
  suggested_material TEXT,
  generated_training_id INTEGER REFERENCES training(id),
  created_by TEXT NOT NULL CHECK (created_by IN ('USER','CLAUDE')),
  last_modified_by TEXT NOT NULL CHECK (last_modified_by IN ('USER','CLAUDE')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

INSERT INTO lesson_new (id, lesson_date, teacher_notes, feedback, focus_until_next,
    suggested_material, generated_training_id, created_by, last_modified_by, created_at,
    updated_at)
SELECT id, lesson_date, teacher_notes, feedback, focus_until_next, suggested_material,
    NULL, created_by, last_modified_by, created_at, updated_at
FROM lesson;

DROP TABLE lesson;
ALTER TABLE lesson_new RENAME TO lesson;
