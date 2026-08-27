# ADR-0011: Tipos de exercício e drum sheet engine

## Status
Aceito. Introduz um eixo novo no `Exercise` (consolidado desde a Fase 1, `V2`) e um
formato de padrão de partitura tocável. Nasce da constatação de que um exercício hoje é
só texto (`how_to_execute`) — não dá pra *executar* nada. Mockup de validação iterado
como Artifact (pauta tradicional de bateria montada numa grade estilo piano-roll,
playhead em loop): https://claude.ai/code/artifact/84a36bb4-491e-4f09-a7bf-c412ae54e514

## Contexto
Nos treinos, alguns exercícios são "toque este groove / esta virada / este rudimento em
loop" — precisam de uma partitura que rode, com metrônomo e count-in, pra tocar junto.
Outros são de natureza diferente: "escutar a música, anotar o que percebe, se acostumar
com ela" — sem partitura, o trabalho é o ouvido. O usuário quer poder escolher o tipo do
exercício no treino. Foram projetados **só esses dois tipos** por enquanto.

Decisões de modelagem confirmadas com o usuário antes de formalizar (ver
[[feedback-domain-modeling]]):

- O tipo novo é um **eixo separado** do `exercise_type` que já existe. `exercise_type`
  continua texto livre descrevendo *o que o exercício treina* ("singles", "leitura de
  tercina", "independência"). O tipo novo decide *como o exercício é executado/renderizado*.
- **Um padrão por exercício** (1:1). Treinar groove e virada = dois exercícios no treino.
  Bate com o exemplo do usuário: "um ritmo, uma virada tocando em loop, ou até rudimentos".
- **Autoria completa do padrão via MCP**: o Claude precisa conseguir escrever uma
  partitura do zero numa conversa, não só descrever em texto.
- **Transcrição fica simples por enquanto**: notas livres + trechos marcados
  (timestamp + rótulo). Áudio dentro do exercício e marcações ricas sobre ele
  (com integrações a outros apps) são evolução futura conhecida — o schema não deve
  fechar essa porta, mas não implementa nada disso agora.
- Banco atual do usuário é só de teste, sem exercícios reais — **sem preocupação de
  backfill / migração de dados** (ver [[project-drum-coach]]).

## Decisão

### 1. `Exercise.kind` — discriminador
- Campo novo `exercise.kind`, enum **`TOCA_JUNTO | TRANSCRICAO`**, obrigatório.
- Define tela, campos disponíveis e validação. **Imutável após a criação** — trocar o
  tipo troca o que o registro significa; se precisar, cria-se outro exercício.
- `exercise_type` (texto livre) **permanece** e continua obrigatório.
- `how_to_execute` passa a **nullable** — vira nota livre opcional nos dois tipos
  ("foco na mão esquerda, chimbal fechado").

### 2. `TOCA_JUNTO` — o padrão (`exercise.pattern`)
- Serializado como **JSON num campo `TEXT` do `exercise`** (`pattern`, nullable — só
  preenchido em `TOCA_JUNTO`). Não é tabela relacional: é single-user, SQLite, e o
  padrão é um documento fechado que ninguém consulta por dentro — junção não traz ganho.
- `target_bpm` (já existe) = BPM alvo do padrão.
- Forma do documento (`version` pra evoluir sem quebrar):

```json
{
  "version": 1,
  "timeSignature": [4, 4],
  "stepsPerBeat": 4,
  "tuplet": false,
  "bars": 2,
  "voices": ["hihat", "snare", "kick"],
  "hits":    { "hihat": [0,2,4,6,8,10,12,14], "snare": [4,12], "kick": [0,6,8,14] },
  "accents": { "snare": [4] },
  "sticking": ["R","L","R","R", "..."]
}
```

- `voices`: vocabulário fixo — `crash, ride, hihat, hiTom, midTom, floorTom, snare, kick`.
- `hits`: índices de step 0-based sobre o loop inteiro. `total = bars · beatsPorCompasso · stepsPerBeat`.
- `subdivisão` = `stepsPerBeat` (int) + `tuplet` (bool), não enum semântico — é o que a
  engine de layout usa e não tem ambiguidade.
- `accents` e `sticking` opcionais. `sticking` só faz sentido em padrão de voz única
  (rudimento).
- **O back só valida estrutura** (steps no range, voices no vocabulário, aritmética do
  `total`, arrays consistentes). Valor rítmico / ligadura (beam) / pausa / posição na
  pauta é **derivado em runtime no front** — não se salva.

### 3. `TRANSCRICAO` — trechos marcados (`exercise_passage`)
- Notas livres reusam `how_to_execute` (agora nullable).
- Trechos marcados = **tabela filha `exercise_passage`** (não array JSON): `from_seconds`
  (obrigatório), `to_seconds` (nullable — trecho pontual vs intervalo), `label` (texto
  curto), `created_at`. Sem colunas de auditoria próprias — a origem é a do `exercise`
  pai (padrão de `repertoire_link` / `execution_exercise_log`, ver
  [ADR-0005](0005-auditoria-de-origem.md)).
- Tabela filha (e não JSON) porque os trechos são adicionados incrementalmente ao longo
  da prática (comportamento de log, como os filhos de `Execution`), e porque a evolução
  conhecida (áudio + marcações ricas) cresce melhor como linhas/colunas do que como um
  blob.

## Consequências

### Schema (`back/`) — migration `V5` (não editar `V1`-`V4` já aplicadas)
- Recria a tabela `exercise` (estratégia de recriação de tabela, já usada em `V3`/`V4` —
  o driver SQLite deste projeto não faz `ALTER COLUMN` pra tirar `NOT NULL`):
  - `+ kind TEXT NOT NULL CHECK (kind IN ('TOCA_JUNTO','TRANSCRICAO'))`
  - `+ pattern TEXT` (JSON, nullable)
  - `how_to_execute TEXT` — deixa de ser `NOT NULL`
  - resto inalterado (`exercise_type NOT NULL`, `target_bpm`, `target_duration_seconds`,
    campos de vídeo, `order_index`, auditoria)
- `+ CREATE TABLE exercise_passage (id, exercise_id NOT NULL REFERENCES exercise(id),
  from_seconds INTEGER NOT NULL, to_seconds INTEGER, label TEXT, created_at TEXT NOT NULL)`.

### Backend (código)
- `Exercise` (domínio): campo `kind` (`ExerciseKind` enum), `pattern` (String JSON,
  nullable), `howToExecute` deixa de ter `Objects.requireNonNull`. Regra de imutabilidade
  de `kind` no use case de update.
- Novo value object / validador de `pattern` (`DrumPattern` + parse/valida a partir do
  JSON) em `domain` ou `application`. Só validação estrutural.
- `exercise_passage`: entidade filha + repositório, no mesmo padrão dos filhos de
  `Execution`/`RepertoireItem` (`@Transactional` manual pra preservar `created_at`, ver
  [[project-drum-coach]]).
- Endpoints:
  - `POST /api/trainings/{id}/exercises` — `CreateExerciseRequest` ganha `kind`, `pattern`
  - `PATCH /api/exercises/{id}` — novo: edita `pattern`, `howToExecute` (usado pelo editor
    da grade). `kind` não é editável.
  - `GET /api/exercises/{id}` — inclui `kind`, `pattern`, `passages`
  - `POST /api/exercises/{id}/passages` · `DELETE /api/exercises/{id}/passages/{pid}`
  - `list_exercises` / `GET /api/trainings/{id}` — incluir campos novos na resposta
- `GlobalExceptionHandler` já converte `IllegalArgumentException` → 400; a validação do
  `pattern` joga `IllegalArgumentException` com mensagem clara.

### MCP (`mcp/`)
- `create_exercise` estendida: `kind` (obrigatório), `pattern` (opcional, documento
  inteiro).
- `update_exercise_pattern(exerciseId, pattern)` — recebe o **documento JSON inteiro**
  (o Claude emite o padrão completo; toggle de hit a hit via tool seria frágil e cheio
  de round-trips).
- `list_pattern_presets()` — pontos de partida nomeados ("groove 4/4", "paradiddle",
  "shuffle") pro Claude não montar tudo do zero sempre.
- `add_marked_passage(exerciseId, fromSeconds, toSeconds?, label)`.
- `get_exercise` passa a devolver `pattern`/`passages` (o Claude lê, modifica e reenvia).
- `server.instructions()` + descrições das tools documentam o schema do `pattern`
  (o Claude só sabe a forma se estiver escrito onde ele lê via MCP).
- Contagem de tools do teste do `mcp/` sobe; README do `mcp/` atualizado. Requer
  reiniciar o cliente MCP (Claude Desktop) pra pegar as tools novas.

### Frontend (`front/`)
- **`PatternEngraver`** — módulo puro, sem DOM: valor rítmico a partir do gap, layout de
  beam/flag/pausa por tempo, posição de cada voz na pauta. Portado da lógica do mockup,
  com testes unitários (`ng test`, nunca `npx vitest run` direto — ver [[project-drum-coach]]).
- **`DrumSheetComponent`** — SVG (pauta de 5 linhas, clave de percussão, cabeça ✕ pra
  pratos, hastes ↑ mãos / ↓ bumbo, ligaduras, pausas; grade de pontos clicável em cima).
  Inputs: `pattern`, `playheadPos` (0–1), `highlightStep`, `editable`. Outputs:
  `patternChange`, `stepToggle`. Reaproveitado entre Modo Sessão (lê) e editor (edita).
- Criação/edição de exercício: seletor de `kind` (tela 1 do mockup); mostra editor de
  padrão OU campos de transcrição conforme o `kind`.
- Modo Sessão: pra `TOCA_JUNTO`, `SessionStateService` alimenta `playheadPos` do mesmo
  relógio de lookahead do `MetronomeService` (ver plano da identidade Woodshed,
  [[project-drum-coach]]). Count-in já existe.
- Tela de transcrição: textarea de notas + lista de trechos marcados (add/remove via os
  endpoints de `passage`). Áudio fica pra depois.
- `models.ts`: `Exercise` ganha `kind`, `pattern`, `passages`; `how_to_execute` vira
  opcional.

### Fases de implementação (cada uma isolada; produção religada pelo coordenador ao final)
- **4a — back**: `V5`, `Exercise.kind`/`pattern`, validador de padrão, `exercise_passage`,
  endpoints. Testes.
- **4b — mcp**: tools novas + schema documentado nas descrições/`instructions()`. Testes.
- **4c — front (engine)**: `PatternEngraver` + `DrumSheetComponent` portados, com testes,
  sem wiring nas telas.
- **4d — front (telas)**: seletor de `kind`, editor de padrão, wiring no Modo Sessão e na
  transcrição.
