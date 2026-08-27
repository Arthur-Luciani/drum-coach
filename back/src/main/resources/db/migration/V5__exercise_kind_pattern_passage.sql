-- ADR-0011 (Fase 4a): introduz o eixo `kind` no exercicio (TOCA_JUNTO | TRANSCRICAO) e o
-- documento de padrao tocavel (`pattern`, JSON num campo TEXT). `how_to_execute` deixa de
-- ser obrigatorio - vira nota livre opcional nos dois tipos. Adiciona a tabela filha
-- `exercise_passage` (trechos marcados de uma transcricao: timestamp + rotulo), sem
-- colunas de auditoria propria - a origem da escrita e a do exercise pai (mesmo padrao de
-- repertoire_link / execution_exercise_log, ver ADR-0005).
--
-- REGRA: nunca editar uma migration ja aplicada (V1..V4) - toda mudanca de schema entra
-- como um novo arquivo V<N>__descricao.sql, como este.
--
-- Estrategia para `exercise` (mesma de V3/V4 - ver comentario la): o driver SQLite deste
-- projeto nao suporta ALTER COLUMN pra remover o NOT NULL de `how_to_execute` de forma
-- direta/portavel, entao recriamos a tabela `exercise` com o schema final (`exercise_new`),
-- copiamos os dados existentes e renomeamos. Ao copiar, todo exercicio antigo (que so tinha
-- texto) recebe kind = 'TRANSCRICAO' (o comportamento antigo = so `how_to_execute`) e
-- pattern = NULL. O banco real do usuario ainda nao tem exercicios cadastrados, entao nao
-- ha migracao de dados de fato aqui - so garantimos que o schema final fique correto.

CREATE TABLE exercise_new (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  training_id INTEGER NOT NULL REFERENCES training(id),
  name TEXT NOT NULL,
  exercise_type TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('TOCA_JUNTO','TRANSCRICAO')),
  how_to_execute TEXT,
  pattern TEXT,
  target_bpm INTEGER,
  target_duration_seconds INTEGER,
  video_source_type TEXT CHECK (video_source_type IN ('LINK','FILE')),
  video_url TEXT,
  video_file_path TEXT,
  order_index INTEGER NOT NULL,
  created_by TEXT NOT NULL CHECK (created_by IN ('USER','CLAUDE')),
  last_modified_by TEXT NOT NULL CHECK (last_modified_by IN ('USER','CLAUDE')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

INSERT INTO exercise_new (id, training_id, name, exercise_type, kind, how_to_execute, pattern,
    target_bpm, target_duration_seconds, video_source_type, video_url, video_file_path,
    order_index, created_by, last_modified_by, created_at, updated_at)
SELECT id, training_id, name, exercise_type, 'TRANSCRICAO', how_to_execute, NULL, target_bpm,
    target_duration_seconds, video_source_type, video_url, video_file_path, order_index,
    created_by, last_modified_by, created_at, updated_at
FROM exercise;

DROP TABLE exercise;
ALTER TABLE exercise_new RENAME TO exercise;

-- Tabela filha: sem colunas de auditoria propria - a origem da escrita e a do exercise
-- pai (ver ADR-0005). `to_seconds` nullable = trecho pontual (so `from_seconds`) vs
-- intervalo [from, to].
CREATE TABLE exercise_passage (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  exercise_id INTEGER NOT NULL REFERENCES exercise(id),
  from_seconds INTEGER NOT NULL,
  to_seconds INTEGER,
  label TEXT,
  created_at TEXT NOT NULL
);
