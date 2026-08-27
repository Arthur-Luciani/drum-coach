# drum-coach-mcp

Proxy MCP (stdio) do drum-coach. Ver [ADR-0007](../docs/adr/0007-mcp-como-proxy-http.md)
para a decisao completa; resumo: este e um processo Java **independente**, sem Spring
Boot, que fala o protocolo MCP via stdio com o Claude Desktop e traduz cada tool call
numa chamada HTTP para a API REST do `back` (`http://localhost:8080` por padrao). Nao
conhece dominio nem persistencia do backend - so o contrato HTTP.

**O `back` precisa estar rodando para as tools funcionarem** (exceto `health_check`, que
existe justamente para diagnosticar se ele esta no ar ou nao). Ver o README da raiz do
projeto para como subir o `back`.

O servidor MCP declara um `instructions` bem mais rico que "e um proxy HTTP": explica o
modelo de dados (Meta → Treino → Exercicio, sem Plano, ver ADR-0009), a convencao de
"so uma meta em foco por vez", recomenda comecar uma conversa de coaching chamando
`get_coach_briefing`, passa o tom esperado ("coach discreto", ver ADR-0008) e documenta o
eixo `kind` do exercicio e a forma do `pattern` tocavel (ver "Modelo: kind / pattern /
trechos marcados" abaixo e ADR-0011). A ideia e que o Claude entenda o sistema pelo que o
servidor MCP conta, nao so pelos nomes das tools - ver `McpProxyApplication.main` para o
texto completo.

## Build

```powershell
cd mcp
mvn clean package
```

Gera `mcp/target/drum-coach-mcp.jar` - um jar unico e autocontido (todas as dependencias
dentro, via `maven-shade-plugin`), com `Main-Class` configurada no manifest. E esse jar
que o Claude Desktop vai rodar.

Este modulo compila para bytecode Java 21 (`maven.compiler.release=21`), nao 25 como o
`back` - de proposito: nao usa nenhuma feature de linguagem nova, e isso permite que o
jar final rode com um JDK mais antigo que ja esteja no `PATH` (nesta maquina, o `java` do
PATH e JDK 23, e o jar roda nele sem problema). Ou seja, ao contrario do `back`, **nao**
deveria ser necessario usar o caminho completo de um JDK 25 especifico so para rodar este
jar - `java -jar drum-coach-mcp.jar` deve funcionar com o `java` que ja estiver no PATH,
desde que seja JDK 17+ (minimo do SDK MCP).

## Rodar standalone, pra debug

O processo fala MCP por stdio - nao da pra simplesmente rodar e digitar texto nele. Pra
inspecionar manualmente, use um cliente MCP (ex. o [MCP Inspector](https://github.com/modelcontextprotocol/inspector))
apontando pro comando abaixo, ou alimente mensagens JSON-RPC via stdin manualmente:

```powershell
java -jar mcp\target\drum-coach-mcp.jar
```

Variavel de ambiente relevante:

- `DRUM_COACH_BACKEND_URL` - base URL do `back`. Default: `http://localhost:8080`.

Mensagens de log (SLF4J, se algum dia um binding for adicionado) vao para stderr - stdout
e reservado exclusivamente para as mensagens JSON-RPC do protocolo MCP. Sem nenhum
binding de SLF4J no classpath (caso atual), o SDK so emite 3 linhas de aviso em stderr no
start ("No SLF4J providers were found...") e mais nada - normal, pode ignorar.

### Testes automatizados

```powershell
cd mcp
mvn test
```

Roda `McpProxyStdioTest`: sobe `McpProxyApplication` como subprocesso real (fala o
protocolo MCP de verdade via stdio, usando o `McpSyncClient` do proprio SDK) e verifica
`initialize`, que `tools/list` retorna as 25 tools esperadas (Fase 3 - Treino direto na
Meta, sem Plano, ver [ADR-0009](../docs/adr/0009-treino-direto-na-meta-sem-plano.md); Fase
4b - eixo `kind` + `pattern` tocavel, ver
[ADR-0011](../docs/adr/0011-tipos-de-exercicio-e-drum-sheet-engine.md)), que nenhuma tool
baseada em Plano (`list_active_plans`, `get_plan_details`, `create_plan`,
`add_training_to_plan`, `generate_plan_from_lesson`) sobreviveu no `tools/list`, que
`health_check` responde de forma amigavel (sem derrubar o processo) quando o `back` esta
fora do ar, que tools com parametro obrigatorio faltando (inclusive campos aninhados,
ex. `training.targetDurationMinutes` em `add_training_to_goal`, `pattern` em
`update_exercise_pattern`) retornam erro amigavel em vez de derrubar o processo, que o
`inputSchema` das tools novas expoe os campos obrigatorios certos (e que o exercicio
inline passou a exigir `kind` e nao mais `howToExecute`), e que `list_pattern_presets`
devolve os 3 presets embutidos sem precisar do `back`. **Nao precisa do `back` rodando.**

Existe tambem `BackendIT` - testes que precisam de um `back` de verdade no ar: cria uma
Goal via `create_goal` e confere `createdBy=CLAUDE`; `update_goal_progress` muda
status/descricao; `get_goal` retorna os treinos de uma meta com o progresso correto
(`GET /api/goals/{id}`); `add_training_to_goal` cria um treino vinculado a uma meta e
tambem, sem `goalId`, um treino avulso (`goalId` nulo); `focus_goal` desfoca
atomicamente qualquer outra meta que estivesse em foco; `record_execution` persiste
`actualDurationSeconds` nos logs de exercicio; `generate_training_from_lesson` vincula
`lesson.generatedTrainingId`; `get_coach_briefing` retorna um resumo coerente (sem
mencionar "planos"); `add_repertoire_item`/`update_repertoire_item` fecham o ciclo do
repertorio. Fase 4b: `add_exercise_to_training` cria um exercicio `TOCA_JUNTO` com um
`pattern` de preset e `get_exercise` devolve esse pattern; `update_exercise_pattern`
substitui o pattern por um editado (e propaga o 400 do back quando o documento e
invalido); `add_marked_passage` grava um trecho num exercicio `TRANSCRICAO` que reaparece
em `get_exercise`; `list_pattern_presets` retorna os 3 presets e cada um passa na
validacao do `DrumPattern` do back. Confere tudo batendo direto na API REST do back (sem
passar pelo proxy), para validar a auditoria e o estado persistido de forma independente.
Ele **nao roda** em
`mvn test`/`mvn clean package` (o nome termina em `IT`, fora do padrao default do
Surefire, de proposito). Para rodar:

```powershell
cd back
mvn clean install
# porta e banco SEPARADOS do uso normal (nunca aponte para 8080/%USERPROFILE%\.drum-coach\drum-coach.db
# se ja houver um back de producao rodando la - ver aviso na raiz do projeto):
& "C:\Users\Dell\.jdks\temurin-25.0.2\bin\java.exe" `
    -Dserver.port=8099 -Ddrumcoach.data.db-path="$env:TEMP\drum-coach-it.db" `
    -cp "target\drum-coach.jar;target\lib\*" dev.drumcoach.presentation.web.WebApplication
```

E, em outro terminal:

```powershell
cd mcp
mvn test "-Dtest=BackendIT" "-Dbackend.url=http://localhost:8099"
```

Lembre de parar o processo do `back` de teste (`Ctrl+C` ou matar o PID) e apagar o arquivo
temporario depois.

## Configurar no Claude Desktop (Windows)

1. Rode `mvn clean package` (ver acima) para gerar `mcp\target\drum-coach-mcp.jar`.
2. Garanta que o `back` esteja rodando (`http://localhost:8080` por padrao) sempre que
   for usar as tools - deixe-o de pe em background no uso do dia a dia.
3. Edite (ou crie) o arquivo de configuracao do Claude Desktop, em
   `%APPDATA%\Claude\claude_desktop_config.json`, adicionando uma entrada em
   `mcpServers`. **Ajuste o caminho do jar abaixo para onde voce clonou o repositorio**
   (o exemplo assume `C:\git\drum-coach`):

```json
{
  "mcpServers": {
    "drum-coach": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\git\\drum-coach\\mcp\\target\\drum-coach-mcp.jar"
      ],
      "env": {
        "DRUM_COACH_BACKEND_URL": "http://localhost:8080"
      }
    }
  }
}
```

   (A chave `env`/`DRUM_COACH_BACKEND_URL` e opcional - so precisa dela se o `back`
   estiver rodando em outra porta/host. Sem ela, o proxy ja usa
   `http://localhost:8080` por padrao.)

4. Reinicie o Claude Desktop. As 25 tools listadas abaixo devem aparecer disponiveis numa
   conversa. **Sempre que a lista de tools mudar (ex.: apos esta fase), reinicie o Claude
   Desktop de novo - ele so le o `tools/list` no start.**

Se `java` nao estiver no `PATH` do sistema (o Claude Desktop roda o `command` fora de um
terminal, entao usa o `PATH` do sistema/usuario, nao necessariamente o mesmo `PATH` de um
terminal aberto), troque `"command": "java"` pelo caminho completo do executavel, ex.
`"C:\\Users\\<usuario>\\.jdks\\temurin-25.0.2\\bin\\java.exe"`.

## Tools expostas

Fase 3 (ver [ADR-0009](../docs/adr/0009-treino-direto-na-meta-sem-plano.md)): o conceito
de "Plano" (TrainingPlan) foi removido do backend - `Training` (Treino) agora referencia
`Goal` (Meta) direto via `goalId` (nullable = treino avulso, sem meta associada). As
tools baseadas em Plano (`list_active_plans`, `get_plan_details`, `create_plan`,
`add_training_to_plan`) deixaram de existir; `get_goal` agora usa `GET /api/goals/{id}`
de verdade (que ja traz os treinos com progresso, substituindo o antigo
`get_plan_details`), `add_training_to_goal` (ex-`add_training_to_plan`) aceita `goalId`
opcional, e uma tool nova, `focus_goal`, marca uma meta como foco do Dashboard.

Fase 4b (ver [ADR-0011](../docs/adr/0011-tipos-de-exercicio-e-drum-sheet-engine.md)): o
exercicio ganhou o eixo `kind` (`TOCA_JUNTO` | `TRANSCRICAO`, obrigatorio e imutavel) e,
em `TOCA_JUNTO`, um `pattern` tocavel (objeto JSON validado pelo back). `howToExecute`
virou opcional. Quatro tools novas: `get_exercise` e `list_pattern_presets` (leitura),
`update_exercise_pattern` e `add_marked_passage` (escrita); e os exercicios inline de
`add_exercise_to_training` / `add_training_to_goal` / `generate_training_from_lesson`
passaram a exigir `kind` e a aceitar `pattern` opcional.

Todas as chamadas de escrita enviam o header `X-Drum-Coach-Actor: CLAUDE`, para que o
`back` marque `createdBy`/`lastModifiedBy` como `CLAUDE` (ver ADR-0007). Duas tools sao
**compostas** (varias chamadas HTTP sequenciais no proprio proxy, sem endpoint composto
novo no back) e uma e **agregadora** (so leitura, sem endpoint correspondente) - marcadas
abaixo.

### Leitura

- **`list_goals`** - sem parametros. Lista todas as metas cadastradas (marca `[FOCO]` a
  que estiver `inFocus`).
- **`get_goal`** - `id` (obrigatorio). Busca uma meta pelo id, com os treinos que
  pertencem direto a ela e o progresso de cada um (execucoes registradas vs.
  repeticoes-alvo) - `GET /api/goals/{id}` (substitui o antigo `get_plan_details`).
- **`list_trainings`** - `goalId` (opcional). Lista treinos, opcionalmente filtrados pela
  meta a qual pertencem (`GET /api/trainings?goalId=`).
- **`list_exercises`** - `trainingId` (obrigatorio). Lista os exercicios de um treino,
  com id/BPM alvo/duracao alvo - usada pra descobrir o `exerciseId` certo antes de
  `record_execution` (`GET /api/trainings/{trainingId}/exercises`).
- **`get_exercise`** - `exerciseId` (obrigatorio). Detalhe de um exercicio
  (`GET /api/exercises/{id}`), formatado: `kind`, `exerciseType`, `howToExecute`, BPM/
  duracao alvo, o `pattern` completo em JSON (quando `TOCA_JUNTO`) e os trechos marcados
  (quando `TRANSCRICAO`). E a leitura do fluxo de edicao de pattern:
  `get_exercise` -> editar o JSON -> `update_exercise_pattern`.
- **`list_pattern_presets`** - sem parametros. Pontos de partida nomeados para o `pattern`
  de um exercicio `TOCA_JUNTO` (`groove-4-4`, `paradiddle`, `shuffle`) - cada um com nome,
  descricao e o objeto JSON ja valido. Sem endpoint no back: os presets sao embutidos no
  proxy (`PatternPresets`).
- **`get_execution_history`** - `trainingId`/`goalId` (opcionais). Lista o historico de
  execucoes reais, com os logs de BPM/duracao por exercicio (`GET /api/executions`).
- **`list_lessons`** - `from`/`to` (opcionais, `yyyy-MM-dd`). Lista aulas
  (`GET /api/lessons`).
- **`get_lesson`** - `id` (obrigatorio). Busca uma aula (`GET /api/lessons/{id}`).
- **`list_repertoire`** - `status` (opcional: `NOT_STARTED`/`LEARNING`/`MASTERED`). Lista
  o repertorio; filtra por status no proprio proxy (o back nao tem esse filtro).
- **`get_repertoire_item`** - `id` (obrigatorio). Busca um item de repertorio; filtra o
  resultado de `GET /api/repertoire-items` no proprio proxy.
- **`get_coach_briefing`** *(agregadora)* - sem parametros. Junta metas em foco/andamento
  (com seus treinos e progresso), aulas recentes e execucoes recentes num resumo legivel -
  varias chamadas HTTP no proxy, sem endpoint correspondente no back.
- **`health_check`** - sem parametros. Confirma se o `back` esta no ar
  (`GET /api/health`); se a chamada falhar (ex. `back` nao esta rodando), retorna uma
  mensagem amigavel em vez de deixar a tool quebrar.

### Escrita

- **`create_goal`** - `title` (obrigatorio), `description`/`targetDate`/`targetMetric`
  (opcionais). `POST /api/goals`.
- **`update_goal_progress`** - `id` (obrigatorio), `status`/`notes` (opcionais - mantem o
  atual se omitido; `notes` grava no campo `description` da meta). `PATCH /api/goals/{id}`.
  Para marcar a meta como foco do Dashboard, use `focus_goal` (parametro separado de
  proposito - acao de produto distinta, ver ADR-0009).
- **`focus_goal`** - `goalId` (obrigatorio). Marca a meta como foco do Dashboard
  (`PATCH /api/goals/{id}` com `inFocus:true`), desfocando atomicamente qualquer outra
  meta em foco - no maximo uma meta em foco por vez. Nao ha suporte a desfocar isolado.
- **`add_training_to_goal`** *(composta)* - `goalId` (opcional - nulo cria um treino
  avulso, sem meta), `training` (obrigatorio, com exercicios opcionais aninhados). Cria o
  treino (`POST /api/trainings`) e, se `training.exercises` foi passado, cada exercicio
  (`POST /api/trainings/{id}/exercises`) em sequencia. Falhas parciais num exercicio nao
  interrompem o resto - viram avisos na resposta.
- **`add_exercise_to_training`** - `trainingId` (obrigatorio), `exercise` (obrigatorio).
  O `exercise` exige `name`/`exerciseType`/`kind` (`TOCA_JUNTO`|`TRANSCRICAO`)/`orderIndex`
  e aceita `howToExecute` (opcional), `pattern` (objeto JSON, opcional, so em `TOCA_JUNTO`),
  `targetBpm`/`targetDurationSeconds` e os campos de video. `POST
  /api/trainings/{trainingId}/exercises`.
- **`update_exercise_pattern`** - `exerciseId`/`pattern` (obrigatorios), `howToExecute`
  (opcional). Substitui o `pattern` de um exercicio `TOCA_JUNTO` pelo documento JSON
  **inteiro** (nao e diff) - `PATCH /api/exercises/{id}`. `kind` e imutavel e nao e
  enviado. O back valida a estrutura e responde 400 (com mensagem, propagada como erro da
  tool) se algo nao fecha. Fluxo: `get_exercise` -> editar o JSON -> esta tool.
- **`add_marked_passage`** - `exerciseId`/`fromSeconds` (obrigatorios), `toSeconds`/`label`
  (opcionais). Adiciona um trecho marcado a um exercicio (tipicamente `TRANSCRICAO`) -
  `POST /api/exercises/{id}/passages`. Trecho pontual = so `fromSeconds`; intervalo =
  `fromSeconds` + `toSeconds`.
- **`record_execution`** - `trainingId`/`executionDate` (obrigatorios),
  `actualDurationMinutes`/`feeling`/`generalNotes`/`goalId`/`logs` (opcionais; cada log
  aceita `achievedBpm`/`actualDurationSeconds`/`notes`). `POST /api/executions`.
- **`record_lesson`** - `lessonDate`/`teacherNotes` (obrigatorios), `feedback`/
  `focusUntilNext`/`suggestedMaterial`/`generatedTrainingId` (opcionais). `POST /api/lessons`.
- **`generate_training_from_lesson`** *(composta)* - `lessonId` (obrigatorio), `goalId`
  (opcional - meta a qual o treino pertence, se fizer sentido no contexto da aula; nulo =
  avulso), `training` (obrigatorio). Confere que a aula existe, cria o treino (igual
  `add_training_to_goal`) e vincula `lesson.generatedTrainingId` a ele via
  `PATCH /api/lessons/{id}`.
- **`add_repertoire_item`** - `songTitle` (obrigatorio), `artist`/`targetBpm`/
  `currentBpm`/`notes`/`links` (opcionais). `POST /api/repertoire-items`.
- **`update_repertoire_item`** - `id` (obrigatorio), `status`/`currentBpm`/`notes`/
  `newLinks` (opcionais - mantem o atual se omitido; `newLinks` e sempre adicionado, nunca
  edita/remove links existentes). `PATCH /api/repertoire-items/{id}`.

## Modelo: kind / pattern / trechos marcados (Fase 4b, ADR-0011)

Todo exercicio tem um eixo **`kind`** obrigatorio e **imutavel apos a criacao**, separado
do `exerciseType` (texto livre que descreve *o que* o exercicio treina):

- **`TOCA_JUNTO`** - tem um **`pattern`** tocavel: uma partitura numa grade que roda em
  loop com metronomo/count-in (grooves, viradas, rudimentos). `target_bpm` do exercicio =
  BPM alvo do pattern.
- **`TRANSCRICAO`** - trabalho de ouvido, sem pattern: notas livres em `howToExecute` +
  **trechos marcados** no audio (`add_marked_passage`: `fromSeconds` obrigatorio,
  `toSeconds` opcional para intervalo, `label` curto).

`howToExecute` e opcional nos dois tipos (nota livre: "foco na mao esquerda", "chimbal
fechado").

Forma do **`pattern`** (objeto JSON, so em `TOCA_JUNTO`):

```json
{
  "version": 1,
  "timeSignature": [4, 4],
  "stepsPerBeat": 4,
  "tuplet": false,
  "bars": 1,
  "voices": ["hihat", "snare", "kick"],
  "hits":    { "hihat": [0,2,4,6,8,10,12,14], "snare": [4,12], "kick": [0,8] },
  "accents": { "snare": [4] },
  "sticking": ["R","L","R","R", "..."]
}
```

- `voices`: vocabulario fixo - `crash, ride, hihat, hiTom, midTom, floorTom, snare, kick`.
- `hits`: indices de step **0-based** sobre o loop inteiro. `total = bars * numerador *
  stepsPerBeat`; todo step deve estar em `[0, total)`.
- `accents` (opcional): por voz, subconjunto dos `hits` daquela voz.
- `sticking` (opcional): so em pattern de **voz unica** (rudimento), comprimento
  exatamente `total`, entradas `"R"`/`"L"`.
- O back so valida **estrutura** (`application.DrumPattern`) e rejeita com 400 + mensagem
  se algo nao fecha. Valor ritmico / ligadura / pausa e derivado no front, nao se envia.

Fluxo de autoria/ajuste: `list_pattern_presets()` (base pronta) -> `add_exercise_to_training`
ou, para editar um existente, `get_exercise` (le o JSON atual) -> editar o **documento
inteiro** -> `update_exercise_pattern`.

### Endpoints relevantes no back (Fase 2/3/4)

Endpoints adicionados/ajustados nas ultimas fases, seguindo exatamente o padrao das
demais entidades (`application` use case + `presentation.web` controller,
`OriginProvider` para auditoria), so para viabilizar as tools acima:

- `PATCH /api/goals/{id}` (`UpdateGoalUseCase`) - atualizacao parcial de `status`/
  `description`/`inFocus` de uma meta (`inFocus:true` desfoca atomicamente qualquer outra
  meta em foco).
- `GET /api/goals/{id}` (`GetGoalDetailUseCase`) - a meta + os treinos que pertencem
  direto a ela, cada um com o progresso (substitui o antigo `GET /api/plans/{id}`).
- `PATCH /api/lessons/{id}` (`UpdateLessonUseCase`) - vincula `generatedTrainingId` a uma
  aula ja existente.
- `POST /api/trainings/{id}/exercises` - `CreateExerciseRequest` ganhou `kind` e `pattern`
  (objeto JSON); `howToExecute` virou opcional.
- `GET /api/exercises/{id}` (`GetExerciseUseCase`) - o exercicio inteiro, com `kind`,
  `pattern` (objeto) e `passages`.
- `PATCH /api/exercises/{id}` (`UpdateExerciseUseCase`) - edita `pattern`/`howToExecute`
  (documento inteiro); `kind` e imutavel (400 se tentar trocar).
- `POST /api/exercises/{id}/passages` (`AddMarkedPassageUseCase`) - adiciona um trecho
  marcado.
