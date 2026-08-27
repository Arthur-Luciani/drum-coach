-- Fase 3e-3 (Modo Sessao - sessao livre): permite registrar uma execucao sem treino
-- vinculado ("so cronometrar", sem estrutura de exercicios). goal_id ja era nullable
-- desde V2 (treino avulso); training_id ainda era NOT NULL - esta migration relaxa isso.
--
-- REGRA: nunca editar uma migration ja aplicada (V1/V2/V3) - toda mudanca de schema entra
-- como um novo arquivo V<N>__descricao.sql, como este.
--
-- Estrategia (mesma de V3 - ver comentario la): o driver SQLite deste projeto nao suporta
-- ALTER COLUMN pra remover NOT NULL de forma direta/portavel, entao recriamos a tabela
-- `execution` com o schema final e copiamos os dados existentes (todos com training_id
-- preenchido, ja que a coluna era obrigatoria ate agora - nenhuma migracao de dados alem
-- da copia direta e necessaria).

CREATE TABLE execution_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  training_id INTEGER REFERENCES training(id),
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

INSERT INTO execution_new (id, training_id, execution_date, actual_duration_minutes,
    feeling, general_notes, goal_id, created_by, last_modified_by, created_at, updated_at)
SELECT id, training_id, execution_date, actual_duration_minutes, feeling, general_notes,
    goal_id, created_by, last_modified_by, created_at, updated_at
FROM execution;

DROP TABLE execution;
ALTER TABLE execution_new RENAME TO execution;
