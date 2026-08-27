-- Fase 1: planos de treino, treinos, exercicios, aulas, execucoes e repertorio.
--
-- REGRA: nunca editar uma migration ja aplicada (V1__init.sql) - toda mudanca de schema
-- entra como um novo arquivo V<N>__descricao.sql.

CREATE TABLE training_plan (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL,
  objective TEXT,
  start_date TEXT,
  end_date TEXT,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE','COMPLETED','ARCHIVED')),
  goal_id INTEGER REFERENCES goal(id),
  created_by TEXT NOT NULL CHECK (created_by IN ('USER','CLAUDE')),
  last_modified_by TEXT NOT NULL CHECK (last_modified_by IN ('USER','CLAUDE')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE training (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  plan_id INTEGER REFERENCES training_plan(id),
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

CREATE TABLE exercise (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  training_id INTEGER NOT NULL REFERENCES training(id),
  name TEXT NOT NULL,
  exercise_type TEXT NOT NULL,
  how_to_execute TEXT NOT NULL,
  target_bpm INTEGER,
  video_source_type TEXT CHECK (video_source_type IN ('LINK','FILE')),
  video_url TEXT,
  video_file_path TEXT,
  order_index INTEGER NOT NULL,
  created_by TEXT NOT NULL CHECK (created_by IN ('USER','CLAUDE')),
  last_modified_by TEXT NOT NULL CHECK (last_modified_by IN ('USER','CLAUDE')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE lesson (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  lesson_date TEXT NOT NULL,
  teacher_notes TEXT NOT NULL,
  feedback TEXT,
  focus_until_next TEXT,
  suggested_material TEXT,
  generated_plan_id INTEGER REFERENCES training_plan(id),
  created_by TEXT NOT NULL CHECK (created_by IN ('USER','CLAUDE')),
  last_modified_by TEXT NOT NULL CHECK (last_modified_by IN ('USER','CLAUDE')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE execution (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  training_id INTEGER NOT NULL REFERENCES training(id),
  execution_date TEXT NOT NULL,
  actual_duration_minutes INTEGER,
  feeling TEXT,
  general_notes TEXT,
  goal_id INTEGER REFERENCES goal(id),
  created_by TEXT NOT NULL CHECK (created_by IN ('USER','CLAUDE')),
  last_modified_by TEXT NOT NULL CHECK (last_modified_by IN ('USER','CLAUDE')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

-- Tabela filha: sem colunas de auditoria propria - a origem da escrita e a da execution
-- pai (ver ADR-0005).
CREATE TABLE execution_exercise_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  execution_id INTEGER NOT NULL REFERENCES execution(id),
  exercise_id INTEGER NOT NULL REFERENCES exercise(id),
  achieved_bpm INTEGER,
  notes TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE repertoire_item (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  song_title TEXT NOT NULL,
  artist TEXT,
  status TEXT NOT NULL CHECK (status IN ('NOT_STARTED','LEARNING','MASTERED')),
  target_bpm INTEGER,
  current_bpm INTEGER,
  notes TEXT,
  created_by TEXT NOT NULL CHECK (created_by IN ('USER','CLAUDE')),
  last_modified_by TEXT NOT NULL CHECK (last_modified_by IN ('USER','CLAUDE')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

-- Tabela filha: sem colunas de auditoria propria - a origem da escrita e a do
-- repertoire_item pai (ver ADR-0005).
CREATE TABLE repertoire_link (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  repertoire_item_id INTEGER NOT NULL REFERENCES repertoire_item(id),
  url TEXT NOT NULL,
  label TEXT,
  created_at TEXT NOT NULL
);
