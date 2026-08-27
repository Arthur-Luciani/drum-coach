# ADR-0009: Treino pertence direto à Meta — remoção do conceito de Plano

## Status
Aceito. Corrige o modelo de domínio implementado na Fase 1 (schema `V2__training_and_more.sql`,
entidade `TrainingPlan`) e ajusta a jornada descrita no [ADR-0008](0008-identidade-e-jornadas-woodshed.md),
que ainda referenciava "planos" no Dashboard e no seletor de treino.

## Contexto
Na modelagem original (entrevista de produto, início do projeto) e na Fase 1, criei uma
hierarquia de 3 níveis: `Goal` (Meta) → `TrainingPlan` (Plano, com objetivo e período
próprios) → `Training` (Treino, com meta de repetição) → `Exercise`. Ao desenhar a
jornada do Dashboard (ADR-0008), ficou claro em conversa que essa camada de "Plano" não
corresponde a nada no modelo mental real do usuário.

O exemplo que resolveu a confusão: "Meta: evoluir singles e doubles → Treino A: 1h30,
Treino B: 45min, Treino C: 15min" — os Treinos pertencem **direto** à Meta. Não existe um
"Plano" com objetivo e prazo próprios agrupando-os. A escolha de qual Treino fazer num
dia específico é dirigida por **tempo disponível no momento** ("hoje tenho pouco tempo,
faço o C"), não por uma prescrição de um plano com cronograma.

## Decisão

- **Remove a entidade `TrainingPlan`/`Plano` inteiramente.**
- `Training` (Treino) passa a referenciar `Goal` diretamente: `training.goal_id`
  (nullable FK → `goal`) no lugar de `training.plan_id`.
- `Training` mantém `target_repetitions` como uma meta de repetição **soft** — sem data
  de início/fim, sem status próprio. É só um contador de referência ("pelo menos 3x"),
  não pauta a escolha do dia, só ajuda a ver progresso.
- **Treino avulso continua existindo**: `goal_id` nulo é válido — treinos tipo
  "aquecimento livre" ou "testar um groove novo" não precisam servir nenhuma meta.
- Disciplina de uso pretendida: idealmente **uma única Meta ativa** (`status =
  IN_PROGRESS`) por vez. Quando excepcionalmente houver mais de uma, o usuário escolhe
  manualmente qual fica "em foco" no Dashboard (`goal.in_focus`, já decidido no
  ADR-0008) — não é o sistema que decide.

## Consequências

### Schema (`back/`)
- Nova migration (`V3`, não editar `V2` já aplicada) que: dropa `training_plan`; remove
  `training.plan_id`; adiciona `training.goal_id` (nullable FK → `goal`). Como o banco
  real do usuário ainda não tem dado de treino/plano cadastrado, o impacto de migração de
  dados é mínimo.
- Campos que só existiam em `training_plan` (`objective`, `start_date`, `end_date`,
  `status` do plano) deixam de existir no sistema — não têm equivalente em `Goal` nem em
  `Training`. Se precisar de "objetivo", já existe `goal.description`.

### Backend (código)
- Remove `TrainingPlanController`, `TrainingPlanEntity`, `TrainingPlanRepository`, use
  cases relacionados (`CreateTrainingPlanUseCase` etc.) e os endpoints `/api/plans/*`.
- `TrainingController`/`CreateTrainingUseCase` passam a aceitar `goalId` no lugar de
  `planId`.
- `GetTrainingPlanDetailUseCase`/`TrainingWithProgress` (a visão de progresso por treino)
  precisa ser reencaixada — provavelmente vira parte de um "detalhe da Meta" em vez de
  "detalhe do Plano".

### MCP (`mcp/`)
- Tools `create_plan`, `add_training_to_plan`, `list_active_plans`, `get_plan_details`
  deixam de existir nesse formato. `create_plan` vira algo como `add_training_to_goal`
  (ou `create_goal` já aceita treinos junto). `generate_plan_from_lesson` vira "criar
  treino(s) a partir da aula", sem precisar de plano.

### Frontend/mockup
- `PlansList.dc.html`, `PlanDetail.dc.html`, `PlanCreate.dc.html` deixam de fazer sentido
  como telas próprias — a gestão de Treinos passa a morar dentro da tela de **Metas**
  (visualizar/criar Treinos de uma Meta), não numa seção "Planos" separada.
- A faixa de navegação (hoje 6 seções: Dashboard/Planos/Execuções/Aulas/Repertório/Metas)
  perde "Planos" — os Treinos vivem dentro de Metas.
- `Main.dc.html` (hero do Dashboard) e `TrainingPicker.dc.html` citam "plano" em texto —
  precisam de revisão textual/estrutural (não é só cosmético: o `TrainingPicker` buscava
  treinos "do plano da meta", agora busca treinos "da meta" direto).
- `ExecutionsHistory.dc.html` tinha um filtro por `planId` — vira filtro por Meta/Treino.

Este ADR registra a decisão de modelagem. A atualização do mockup e do backend para
refletir isso é trabalho de implementação separado, ainda não feito.
