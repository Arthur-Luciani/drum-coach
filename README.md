# drum-coach

Caderno pessoal de treino de bateria: planos de treino, execuções, metas, aulas e
repertório, com o Claude atuando como coach via MCP.

A arquitetura completa está documentada em `docs/adr/` — leia os ADRs antes de mexer na
estrutura do projeto, eles são a fonte da verdade. Em especial: ADR-0006 (camadas
DDD/Clean Architecture do backend) e ADR-0007 (por que o MCP é um proxy HTTP separado, em
`mcp/`, e não um segundo entry point do backend).

## Estrutura (monorepo)

```
drum-coach/
├── back/   # Spring Boot (Java 25) — domain/application/infra/presentation.web
├── front/  # Angular SPA
├── mcp/    # proxy MCP — projeto Java independente, sem Spring, fala stdio com o Claude
└── docs/adr/
```

## Estado atual (Fase 0 — scaffolding)

Esta fase entrega o esqueleto técnico + a entidade `Goal` ponta a ponta (domain →
application → infra → REST), para validar que a stack toda funciona:

- `back/pom.xml`, Java 25, Spring Boot 4.1.1.
- Pacotes `dev.drumcoach.{domain,application,infra,presentation.web}`.
- Persistência SQLite via Spring Data JDBC + Flyway (`V1__init.sql`, só a tabela `goal`).
- `WebApplication`: sobe a API REST (`GET/POST /api/goals`, `GET /api/health`) e serve o
  build do Angular (SPA mínima que chama a API).
- Auditoria de origem por requisição: toda escrita HTTP marca `created_by`/
  `last_modified_by` como `USER`, exceto quando a requisição chega com o header
  `X-Drum-Coach-Actor: CLAUDE` (usado pelo proxy MCP) — validado empiricamente, ver ADR-0007.

- `mcp/`: proxy MCP (stdio) independente, sem Spring — ver seção abaixo e
  `mcp/README.md`.

## Estado atual (Fase 1 — backend completo)

Entrega o restante do schema e da API REST do backend (ainda sem UI Angular para essas
entidades — isso fica para uma etapa futura), seguindo exatamente o mesmo padrão em
camadas da Fase 0 (domain → application → infra → presentation.web), arquivo por
arquivo, para cada entidade:

- **Plano de treino** (`training_plan`): `POST/GET /api/plans`, `GET /api/plans/{id}`
  (retorna o plano com os treinos aninhados e o progresso de cada um — quantas
  `execution` existem vs. `targetRepetitions`).
- **Treino/template** (`training`): `POST/GET /api/trainings` (`planId` opcional no
  corpo/query — nulo é treino avulso, fora de qualquer plano).
- **Exercício** (`exercise`): `POST/GET /api/trainings/{trainingId}/exercises`.
- **Aula** (`lesson`): `POST/GET /api/lessons` (filtro opcional `?from=&to=`),
  `GET /api/lessons/{id}`.
- **Execução real** (`execution` + `execution_exercise_log`): `POST/GET /api/executions`
  (filtros opcionais `?trainingId=` e/ou `?planId=`) — a execução e seus logs de BPM por
  exercício são persistidos numa única operação transacional.
- **Repertório** (`repertoire_item` + `repertoire_link`): `POST/GET /api/repertoire-items`,
  `PATCH /api/repertoire-items/{id}` (atualização parcial de status/BPM atual/notas e
  adição de novos links — links já existentes nunca são reescritos/removidos).

Schema em `back/src/main/resources/db/migration/V2__training_and_more.sql` (a
`V1__init.sql` da Fase 0, só com `goal`, nunca foi editada). Auditoria de origem
(`created_by`/`last_modified_by`) funciona exatamente como na Fase 0 em todas as
entidades novas; as tabelas filhas sem ciclo de vida próprio
(`execution_exercise_log`, `repertoire_link`) não têm colunas de auditoria — a origem é
a da linha pai (ver ADR-0005).

Decisões de modelagem tomadas nesta fase (fora do que já estava especificado):

- `TrainingPlan` recém-criado começa em `status=ACTIVE` (não há um equivalente a
  "não iniciado" no enum `ACTIVE|COMPLETED|ARCHIVED`).
- `Execution`+`ExecutionExerciseLog` e `RepertoireItem`+`RepertoireLink` são modelados
  como agregados no domínio (a raiz carrega a lista de filhos), mas a persistência
  **não** usa o mapeamento nativo de agregado do Spring Data JDBC
  (`@MappedCollection`) — cada tabela filha tem seu próprio repositório Spring Data JDBC
  "flat", e a classe `*RepositoryImpl` orquestra raiz + filhos numa única transação
  (`@Transactional`). Isso evita a semântica de "apaga tudo e reinsere" do
  `@MappedCollection` em updates (que reescreveria o `created_at` dos filhos já
  persistidos) e mantém o mapeamento simples e previsível, ao custo de uma pequena
  duplicação de orquestração entre os dois repositórios afetados.
- No PATCH de `RepertoireItem`, links novos (sem `id`) são sempre inseridos e links já
  persistidos nunca são alterados/removidos — não há endpoint para editar/remover um
  link individual no MVP.

## Pré-requisitos

- **JDK 25.** ⚠️ Nesta máquina o `java` do PATH aponta para uma versão mais antiga
  (JDK 23), que **não roda** classes compiladas com Java 25
  (`UnsupportedClassVersionError`). Ou ajuste o `PATH`/`JAVA_HOME` para apontar para o
  JDK 25 antes de rodar `java -cp ...` manualmente, ou use o caminho completo, ex.:
  `C:\Users\Dell\.jdks\temurin-25.0.2\bin\java.exe`. O `mvn` já usa o JDK certo porque
  `JAVA_HOME` está configurado para ele globalmente — o problema é só ao rodar `java`
  diretamente depois do build.
- Maven.
- Node 22 + npm (usados automaticamente pelo build do `back` via `frontend-maven-plugin`
  — não precisa instalar Angular CLI global).

## Como rodar o backend (`back/`)

```powershell
cd back
mvn clean install
```

Isso builda o frontend Angular (saída direto em `back/target/classes/static`), compila o
backend, roda os testes (inclui um teste de integração que sobe a API contra um SQLite
temporário) e empacota.

**Importante:** o `spring-boot-maven-plugin` só permite UM `Start-Class` por jar
repackaged, e um jar "gordo" repackaged (`BOOT-INF/classes` + `BOOT-INF/lib/*.jar`) não
funciona com `java -cp app.jar <classe>`. O build gera em vez disso:

- `back/target/drum-coach.jar` — jar fino, só com as classes deste projeto.
- `back/target/lib/*.jar` — todas as dependências de runtime (via
  `maven-dependency-plugin:copy-dependencies`).

Rode com (veja a nota de JDK 25 acima se `java` do PATH for mais antigo):

```powershell
java -cp "target\drum-coach.jar;target\lib\*" dev.drumcoach.presentation.web.WebApplication
```

(no Linux/Mac o separador de classpath é `:` em vez de `;`). Acesse
<http://localhost:8080> (SPA Angular) ou `http://localhost:8080/api/goals` (REST).

Também existe um jar executável clássico (`back/target/drum-coach-boot.jar`, classifier
`boot`), rodável com `java -jar target/drum-coach-boot.jar` — só um atalho de
conveniência para subir o `WebApplication` sozinho.

**O backend precisa estar rodando para o proxy MCP funcionar** (ver ADR-0007) — deixe-o
de pé em background no uso normal do dia a dia.

### Desenvolvimento do frontend (`front/`)

Para iterar rápido no Angular sem rebuildar o jar inteiro:

```powershell
cd front
npm start
```

Isso sobe `ng serve` com proxy (`proxy.conf.json`) para `http://localhost:8080` — suba o
`WebApplication` primeiro.

## Proxy MCP (`mcp/`)

Ver [`mcp/README.md`](mcp/README.md) para instruções de build/execução, a lista completa
de tools e o JSON exato de configuração do `claude_desktop_config.json`. Resumo do
desenho (ADR-0007): processo Java independente (sem Spring Boot), fala MCP via stdio com
o Claude Desktop, e traduz cada tool call numa chamada HTTP para a API REST do `back`
(`http://localhost:8080` por padrão), enviando o header `X-Drum-Coach-Actor: CLAUDE` para
que as escritas fiquem auditadas corretamente.

## Estado atual (Fase 2 — MCP completo)

Expande o `mcp/` para conhecer todas as 7 entidades (Fase 0/1 só cobriam `Goal`), com 22
tools ao todo (12 de leitura + 10 de escrita — lista completa em `mcp/README.md`). Esse
número de 22 tools baseadas em `Plano` foi revisado na Fase 3 (abaixo) — a lista atual,
com 20 tools, está em `mcp/README.md`.

- A maioria das tools de leitura/escrita é um espelho fino de um endpoint REST existente.
  Algumas filtragens (`get_goal`, `list_active_plans`, `list_repertoire`/
  `get_repertoire_item` por não terem endpoint dedicado/filtro no back) acontecem no
  próprio proxy, sobre o resultado de `GET` do back — dataset pequeno, app pessoal, não
  precisou de endpoint novo.
- Duas tools são **compostas**: `create_plan` (plano + treinos + exercícios, via
  `POST /api/plans` seguido de `POST /api/trainings`/`POST .../exercises` em sequência,
  best-effort — falhas parciais viram avisos na resposta em vez de abortar tudo) e
  `generate_plan_from_lesson` (mesma composição de `create_plan` + vincula
  `lesson.generatedPlanId`).
- Uma tool é **agregadora**, sem endpoint correspondente: `get_coach_briefing` junta
  planos ativos com progresso, metas em andamento, aulas e execuções recentes num resumo
  legível, para o Claude se situar rápido no início de uma conversa de coaching.
- Dois endpoints mínimos foram adicionados ao `back` nesta fase — mesmo padrão em camadas
  de sempre (`application` use case + `presentation.web` controller), nada além disso:
  `PATCH /api/goals/{id}` (`UpdateGoalUseCase` — status/descrição parciais) e
  `PATCH /api/lessons/{id}` (`UpdateLessonUseCase` — vincula `generatedPlanId`).
- Testes: `McpProxyStdioTest` (22 tools, validação de parâmetros obrigatórios incl.
  aninhados, sem depender do `back`) roda em `mvn test`/`mvn clean package`; `BackendIT`
  (fluxos reais contra um `back` de teste — `create_plan` composto, progresso após
  `record_execution`, `get_coach_briefing`, `update_goal_progress`,
  `generate_plan_from_lesson`) continua fora do padrão default do Surefire — ver
  `mcp/README.md` para como rodar manualmente contra uma porta/banco de teste.

## Estado atual (Fase 3 — correção de modelagem: Treino pertence direto à Meta, sem Plano)

Corrige o modelo de domínio implementado na Fase 1 — ver
[ADR-0009](docs/adr/0009-treino-direto-na-meta-sem-plano.md) para o racional completo — e
adiciona os campos de schema listados nas "Consequências" do
[ADR-0008](docs/adr/0008-identidade-e-jornadas-woodshed.md). `back/` e `mcp/` já foram
atualizados para o novo contrato; `front/` ainda referencia o modelo antigo (Plano) e será
atualizado numa etapa separada.

- **Remove a entidade `TrainingPlan`/`Plano` inteiramente**: `TrainingPlanController`,
  os use cases (`CreateTrainingPlanUseCase` etc.), o repositório, o mapper e a tabela
  `training_plan` não existem mais. Os endpoints `/api/plans/*` deixaram de existir.
- **`Treino` (`training`) passa a pertencer direto à `Meta` (`goal`)**: a coluna
  `training.plan_id` virou `training.goal_id` (nullable FK → `goal` — nula continua
  significando treino avulso, sem meta associada). `POST/GET /api/trainings` e
  `CreateTrainingCommand`/`Training` usam `goalId` no lugar de `planId` em todas as
  camadas (domain → application → infra → presentation.web).
- **Visão de progresso movida para a Meta**: o antigo `GET /api/plans/{id}`
  (`GetTrainingPlanDetailUseCase`/`TrainingPlanDetail`) virou
  **`GET /api/goals/{id}`** (`GetGoalDetailUseCase`/`GoalDetail`) — retorna a Meta +
  a lista dos Treinos que pertencem direto a ela, cada um com o progresso
  (`completedCount` = quantas `Execution` existem vs. `targetRepetitions`). Formato de
  resposta: `{"goal": {...GoalResponse}, "trainings": [{"trainingId", "name",
  "targetRepetitions", "completedCount"}, ...]}`.
- **`GET /api/executions?goalId=`** filtra direto por `execution.goal_id` (coluna que já
  existia desde a Fase 1, independente do treino) — substitui o antigo `?planId=`, que
  fazia join via `training.plan_id` (join que deixou de fazer sentido, já que
  `execution.goal_id` é a forma mais direta de expressar "execuções vinculadas a esta
  meta").
- **`lesson.generated_plan_id` virou `lesson.generated_training_id`** (FK → `training`):
  consequência direta de remover `training_plan` — uma aula gerada passa a apontar para o
  Treino criado a partir dela, não mais para um Plano. `PATCH /api/lessons/{id}` aceita
  `generatedTrainingId` no lugar de `generatedPlanId`.
- **`exercise.target_duration_seconds`** (novo, nullable) — duração alvo do exercício em
  segundos, usada pelo timer automático do Modo Sessão (ver ADR-0008).
- **`execution_exercise_log.actual_duration_seconds`** (novo, nullable) — tempo realmente
  gasto no exercício dentro da execução. Tabela filha sem auditoria própria, inalterado
  (ver ADR-0005).
- **`goal.in_focus`** (novo, `boolean`, default `false`) — meta em foco no Dashboard. A
  aplicação garante no máximo um `true` por vez: `PATCH /api/goals/{id}` com
  `{"inFocus": true}` desfoca atomicamente (mesma transação, `@Transactional` em
  `UpdateGoalUseCase`) qualquer outra meta em foco antes de focar a meta alvo. Não há
  suporte a `inFocus: false` isolado — o produto não tem o conceito de "desfocar todas",
  você troca de foco escolhendo outra meta.
- Schema em `back/src/main/resources/db/migration/V3__goal_training_direct.sql` (`V1` e
  `V2` nunca foram editadas). SQLite tem suporte limitado a `ALTER TABLE` (sem `DROP
  COLUMN`/troca de FK diretos) — `training` e `lesson` foram recriadas pelo padrão
  "criar tabela `_new`, copiar dados, dropar a antiga, renomear" (documentado no
  comentário da migration); `exercise`, `execution_exercise_log` e `goal` só ganharam
  colunas novas via `ALTER TABLE ... ADD COLUMN`, sem precisar recriar.
- `goal.in_focus` é mapeado direto para `boolean` Java (coluna `INTEGER 0/1`) — precisou
  de um par de conversores explícitos em `JdbcConfig` (`Integer <-> Boolean`), porque o
  driver `org.xerial:sqlite-jdbc` devolve colunas `INTEGER` como `java.lang.Integer` e o
  Spring Data JDBC não converte `Integer -> boolean` automaticamente (mesmo padrão já
  usado para `Instant`/`LocalDate`/enums, ver risco técnico #4 da Fase 0).

### `mcp/` atualizado para o novo contrato

O proxy MCP (`mcp/`) foi atualizado nesta fase para refletir o modelo sem Plano — lista
completa de tools em `mcp/README.md`; resumo das mudanças:

- **Removidas** as 4 tools baseadas em Plano: `list_active_plans`, `get_plan_details`,
  `create_plan`, `add_training_to_plan`.
- **`get_goal(id)`** passou a usar `GET /api/goals/{id}` de verdade (antes filtrava
  `list_goals` no proxy, já que o endpoint não existia) — a resposta já inclui os treinos
  da meta com progresso, substituindo o antigo `get_plan_details`.
- **`add_training_to_goal`** (ex-`add_training_to_plan`) aceita `goalId` opcional (nulo =
  treino avulso).
- **`generate_training_from_lesson`** (ex-`generate_plan_from_lesson`) cria um Treino
  (com exercícios, se informados) sob uma Meta (`goalId` opcional) ou avulso, e vincula
  `lesson.generatedTrainingId` via `PATCH /api/lessons/{id}`.
- **Nova tool `focus_goal(goalId)`**: `PATCH /api/goals/{id}` com `inFocus:true`,
  separada de `update_goal_progress` (ação de produto distinta, ver ADR-0009).
- **`add_exercise_to_training`** aceita `targetDurationSeconds` opcional;
  `record_execution` aceita `actualDurationSeconds` opcional em cada log de exercício;
  `list_trainings`/`get_execution_history` filtram por `goalId` no lugar de `planId`.
- **`get_coach_briefing`** passou a agregar metas em foco/andamento com seus treinos e
  progresso (`GET /api/goals` + `GET /api/goals/{id}`), sem nenhuma referência a plano.
- Total: 22 → 20 tools (12→10 de leitura, 10 de escrita — `create_plan` saiu,
  `focus_goal` entrou). Testes: `McpProxyStdioTest` confere que nenhuma tool baseada em
  Plano aparece em `tools/list`; `BackendIT` cobre `get_goal` com progresso,
  `add_training_to_goal` com/sem `goalId`, `focus_goal` desfocando outra meta e
  `actualDurationSeconds` em `record_execution`, rodando contra um `back` de teste
  isolado (porta/banco separados de produção).

## Onde fica o banco de dados

Arquivo SQLite único em `%USERPROFILE%\.drum-coach\drum-coach.db` (fora da pasta de
instalação do jar, de propósito — não some em upgrades/reinstalação). Configurável via
`drumcoach.data.db-path` em `back/src/main/resources/application.yml` ou por variável de
ambiente/propriedade JVM (`-Ddrumcoach.data.db-path=...`).

O diretório é criado automaticamente na primeira subida. `journal_mode=WAL` e
`busy_timeout=5000ms` são configurados explicitamente na conexão (ver
`infra.config.DataSourceConfig`) para tolerar o `back` recebendo escritas concorrentes
(front + proxy MCP, ambos via HTTP no mesmo processo `back` — não é mais SQLite sendo
escrito por dois processos diferentes, já que o MCP não toca no banco diretamente).

## Migrations (Flyway)

Ficam em `back/src/main/resources/db/migration/`, nomeadas `V<N>__descricao.sql`.

**Regra: nunca edite uma migration já aplicada.** Se o schema já rodou em algum banco
(mesmo que só o seu, local), qualquer mudança de schema entra como um arquivo `V<N+1>`
novo — editar um `V<N>` existente quebra o checksum que o Flyway valida na próxima
subida.

## Riscos técnicos validados na Fase 0

Ver ADRs para o desenho; abaixo, o resultado prático de cada risco levantado no plano de
implementação:

1. **Dois entry points precisando rodar separados**: resolvido de duas formas
   complementares — (a) o jar repackaged do Spring Boot não funciona com `java -cp`,
   então o backend gera um jar fino + `target/lib/*.jar`; (b) o MCP deixou de ser um
   segundo entry point do backend e virou um processo totalmente separado em `mcp/`
   (ADR-0007), o que elimina o risco de colisão de beans entre os dois.
2. **Dialect Spring Data JDBC para SQLite**: não existe dialect de primeira classe.
   `AnsiDialect` (já embutido no Spring Data Relational) funciona para as operações
   usadas por este app (insert com PK autoincrement, select por id, select all) — não
   foi necessário usar a lib comunitária `io.github.jamoamo:spring-data-jdbc-sqlite`.
   Só precisou de um adapter mínimo (`infra.config.SqliteJdbcDialect`) porque o Spring
   Data JDBC 4.1.x espera um bean do tipo `JdbcDialect` (que estende `Dialect`), não
   `Dialect` puro.
3. **`org.xerial:sqlite-jdbc` + `getGeneratedKeys()`**: funciona — habilitado
   explicitamente via `SQLiteConfig#setGetGeneratedKeys(true)`. Validado com inserts
   reais via `POST /api/goals` (id autoincrementado retornado corretamente).
4. **Timestamps/enums em SQLite**: `JdbcCustomConversions` explícitas em
   `infra.config.JdbcConfig` para `Instant`, `LocalDate`, `GoalStatus` e `Origin` ↔
   `TEXT` (ISO-8601). Validado — os quatro tipos vão e voltam corretamente.
5. **Concorrência**: `PRAGMA journal_mode=WAL` + `busy_timeout=5000` configurados via
   `SQLiteConfig` no `DataSourceConfig`. Validado lendo `PRAGMA journal_mode`/
   `PRAGMA busy_timeout` de volta numa conexão real. Ficou menos crítico depois do
   ADR-0007 (só o `back` escreve no arquivo agora), mas mantido por segurança.
6. **Empacotamento do Angular no jar**: `frontend-maven-plugin` roda na fase
   `generate-resources`, com `outputPath` do Angular apontando direto para
   `target/classes/static` (builder novo do Angular 21 aceita `outputPath.base` +
   `outputPath.browser: ""` para não aninhar em `/browser`). Validado — o Angular serve
   como welcome page do Spring Boot e chama a API real.
7. **Auditoria de origem por header HTTP** (`X-Drum-Coach-Actor`, ADR-0007): validado
   empiricamente — `POST /api/goals` sem o header grava `createdBy=USER`; com o header
   `X-Drum-Coach-Actor: CLAUDE`, grava `createdBy=CLAUDE`. Implementado como um
   `OriginProvider` (`RequestOriginProvider`) de escopo de requisição (`@RequestScope`),
   substituindo o bean fixo por processo do desenho anterior.

### Nota sobre versões

- Angular: a versão mais recente do `@angular/cli` (22.x) exige Node ≥ 22.22.3; a
  máquina tinha Node 22.14.0 instalado, então o frontend foi criado com Angular 21.2.x
  (a série estável mais recente compatível). Se a máquina for atualizada para Node
  22.22+/24.15+/26, dá para migrar para o Angular 22 com `ng update`.
- `spring-boot-starter-flyway`: no Spring Boot 4, a autoconfiguração do Flyway foi
  separada da `spring-boot-autoconfigure` para este módulo dedicado — só declarar
  `flyway-core` como dependência (sem essa starter) NÃO ativa a auto-configuração, e as
  migrations simplesmente não rodam (sem nenhum erro óbvio).
