# ADR-0008: Identidade e jornadas do produto (Woodshed)

## Status
Aceito

## Contexto
Depois da Fase 1 (CRUD completo) e Fase 2 (MCP completo), o usuário testou o app e
avaliou: "é um CRUD simples, sem identidade, sem jornadas, só databags na tela". A
proposta original nunca foi só um caderno digital — é a interface entre o usuário e o
instrumento durante a prática. Duas restrições reais do uso concreto moldaram esta
decisão: o app é usado perto da bateria, muitas vezes **com as baquetas na mão**, o que
torna teclado (não mouse) o meio de interação primário; e o usuário já tem o proxy MCP
funcionando (Fase 2), então grande parte da estrutura de dados (planos, treinos,
exercícios) tende a ser criada via conversa com o Claude, não digitada na UI.

O processo (pesquisa de mercado + definição colaborativa de identidade + mockups
iterados como Artifact) está registrado na conversa; este ADR consolida as decisões daí
como referência técnica, do mesmo jeito que os ADRs anteriores registram arquitetura.

## Decisão

### Identidade
- Nome do produto: **Woodshed** (gíria de músico para praticar sozinho, intensamente).
- Tema escuro fixo (sem modo claro) — uso em ambiente de ensaio, glanceable a distância.
- Tipografia: Space Grotesk (UI/headings) + JetBrains Mono (números — BPM, timers,
  contagens). Paleta: quase-preto quente de base, acento âmbar (evoca latão/hardware de
  bateria).
- Personalidade: "coach discreto" — comenta ocasionalmente (ex: metas paradas), sem
  gamificação agressiva (sem streaks/ligas/pressão social).

### Navegação por teclado (requisito central, não incidental)
- Atalhos globais fixos, não um command palette digitado: números `1`–`6` pulam entre
  seções, `?` abre um cheat-sheet de atalhos, `M` abre o metrônomo avulso.
- A captura de teclado global **deve ignorar** teclas quando o foco estiver num campo de
  texto (ex: notas da revisão pós-treino) — senão `Espaço` vira "play/pause" em vez de
  digitar um espaço.

### Jornada central (substitui a ideia de "sugestão automática de treino")
1. **Dashboard**: destaca a **meta em foco** (ver "Meta em foco" abaixo) ou, se o plano
   ativo não tiver meta vinculada, o próprio plano — nunca a hero fica vazia se existir
   algo ativo.
2. `Enter` abre um **seletor de treino** (overlay): lista os treinos possíveis (do plano
   da meta/plano em destaque, mais a opção de sessão livre), numerados (`1`–`9`,
   atalho direto) e navegáveis por seta, cada um mostrando sessões previstas x
   executadas como barra de progresso. **A escolha é sempre do usuário** — o sistema não
   decide sozinho qual treino fazer hoje.
3. **Modo Sessão**: 
   - **Count-in** antes de cada exercício começar a contar: toca o metrônomo já no BPM
     alvo do exercício, por um número inteiro de compassos, garantindo **pelo menos 5
     segundos** de preparação (arredonda pra cima em compassos completos — ex: a 120bpm
     em 4/4 um compasso dura 2s, então usa 3 compassos = 6s; a 60bpm um compasso dura 4s,
     usa 2 compassos = 8s). O exercício sempre começa a contar exatamente no primeiro
     tempo de um compasso.
   - Durante o exercício, um timer conta automaticamente contra o `target_duration` do
     exercício (não precisa de controle manual). Ao esgotar o tempo previsto: aviso
     **visual e sonoro** (som curto, diferente do clique do metrônomo — o usuário pode
     estar de costas pra tela) sugerindo avançar, sem forçar a transição.
   - Suporta **sessão livre** (sem treino/exercícios pré-cadastrados): só cronômetro +
     metrônomo + notas rápidas, vira uma Execução avulsa ao final.
4. **Revisão pós-treino**: pré-preenchida com o que a sessão capturou (duração, BPM por
   exercício); só pede "como se sentiu" e notas.

### Metrônomo (cidadão de primeira classe, não um link externo)
Embutido no Modo Sessão E disponível como utilitário avulso (`M`, não registra nada).
Precisa de: compasso configurável (ex: 4/4), acento visual/sonoro no primeiro tempo,
subdivisões (semínima, colcheia, tercina), tap tempo.

### Meta em foco
Quando há mais de uma meta `IN_PROGRESS`, o usuário **escolhe manualmente** qual aparece
em destaque no Dashboard — não é escolhida automaticamente por prazo ou recência.

## Consequências

### Mudanças de schema necessárias em `back/` (ainda não implementadas)
- `exercise.target_duration_seconds` (novo, nullable) — duração alvo por exercício.
- `execution_exercise_log.actual_duration_seconds` (novo, nullable) — tempo realmente
  gasto no exercício, capturado automaticamente pelo Modo Sessão.
- `goal.in_focus BOOLEAN NOT NULL DEFAULT FALSE` (novo) — a aplicação garante no máximo
  um `true` por vez (ao focar uma meta, desfoca as demais na mesma operação).
- Endpoint para marcar foco (ex: `PATCH /api/goals/{id}` aceitando um campo `inFocus`,
  reaproveitando o endpoint já existente do ADR anterior em vez de criar um novo).

### Frontend
- Metrônomo com áudio real precisa de **Web Audio API com scheduling por lookahead**
  (`setInterval` sozinho tem drift perceptível num metrônomo) — tratar como um spike
  técnico à parte antes de integrar ao Modo Sessão, não é trivial.
- O count-in reusa o mesmo motor de áudio/clique do metrônomo do Modo Sessão.

### Pendência de design
- A tela de Metas (mockup atual) ainda não tem a ação de "marcar como foco" — falta
  desenhar essa interação antes ou durante a implementação.

### Mockup de referência
Artifact "Woodshed" (canvas de design, 17 artboards, 2 páginas: Jornada de treino /
Planejamento) — usar como referência visual ao implementar, não como especificação
pixel-perfect (é mockup estático, não protótipo).
