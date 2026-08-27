-- Fase 0 (spike): apenas a tabela `goal`, para validar Spring Data JDBC + SQLite +
-- Flyway ponta a ponta. O schema completo (training_plan, training, exercise, lesson,
-- execution, repertoire_item, etc.) fica para a Fase 1.
--
-- REGRA: nunca editar uma migration ja aplicada - toda mudanca de schema entra como um
-- novo arquivo V<N>__descricao.sql.
CREATE TABLE goal (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL,
  description TEXT,
  target_date TEXT,
  status TEXT NOT NULL CHECK (status IN ('NOT_STARTED','IN_PROGRESS','ACHIEVED','ABANDONED')),
  target_metric TEXT,
  created_by TEXT NOT NULL CHECK (created_by IN ('USER','CLAUDE')),
  last_modified_by TEXT NOT NULL CHECK (last_modified_by IN ('USER','CLAUDE')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
