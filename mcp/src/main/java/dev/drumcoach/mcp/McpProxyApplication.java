package dev.drumcoach.mcp;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import dev.drumcoach.mcp.BackendClient.BackendException;
import dev.drumcoach.mcp.BackendClient.ExecutionDto;
import dev.drumcoach.mcp.BackendClient.ExecutionExerciseLogDto;
import dev.drumcoach.mcp.BackendClient.ExecutionLogInput;
import dev.drumcoach.mcp.BackendClient.ExerciseDto;
import dev.drumcoach.mcp.BackendClient.ExerciseInput;
import dev.drumcoach.mcp.BackendClient.GoalDetailDto;
import dev.drumcoach.mcp.BackendClient.GoalDto;
import dev.drumcoach.mcp.BackendClient.LessonDto;
import dev.drumcoach.mcp.BackendClient.MarkedPassageDto;
import dev.drumcoach.mcp.BackendClient.RepertoireItemDto;
import dev.drumcoach.mcp.BackendClient.RepertoireLinkDto;
import dev.drumcoach.mcp.BackendClient.RepertoireLinkInput;
import dev.drumcoach.mcp.BackendClient.TrainingDto;
import dev.drumcoach.mcp.BackendClient.TrainingProgressDto;

/**
 * Entry point do proxy MCP (ver ADR-0007): processo Java independente, sem Spring, que
 * fala MCP via stdio com o Claude Desktop e traduz cada tool call numa chamada HTTP para
 * a API REST do {@code back}.
 *
 * <p>
 * Deliberadamente simples: monta o {@link McpSyncServer} com transporte stdio, registra
 * as tools de leitura/escrita para todas as entidades (Goal, Training, Exercise, Lesson,
 * Execution, RepertoireItem) e sobe. O {@link StdioServerTransportProvider} inicia
 * threads nao-daemon de leitura/escrita de stdin/stdout assim que o servidor e
 * construido - {@code main} pode simplesmente retornar depois disso que o processo
 * continua vivo.
 *
 * <p>
 * Fase 3 (ver ADR-0009): o conceito de "Plano" (TrainingPlan) foi removido do backend -
 * Treino pertence direto a Meta ({@code goalId} nullable = treino avulso). As tools
 * baseadas em Plano ({@code list_active_plans}, {@code get_plan_details},
 * {@code create_plan}, {@code add_training_to_plan}) deixaram de existir; {@code get_goal}
 * agora usa {@code GET /api/goals/{id}} de verdade (que ja traz os treinos com progresso,
 * substituindo o antigo {@code get_plan_details}), {@code add_training_to_goal} (ex-
 * {@code add_training_to_plan}) aceita {@code goalId} opcional, e uma tool nova,
 * {@code focus_goal}, marca uma meta como foco do Dashboard.
 *
 * <p>
 * Duas tools sao "compostas" (orquestram varias chamadas HTTP sequenciais no proprio
 * proxy, sem endpoint composto novo no back): {@code add_training_to_goal} (treino +
 * exercicios) e {@code generate_training_from_lesson} (treino + exercicios + vincular
 * {@code generatedTrainingId} na aula). Uma tool e puramente agregadora, sem escrita:
 * {@code get_coach_briefing}, que junta metas em foco/andamento (com seus treinos e
 * progresso), aulas e execucoes recentes num resumo legivel.
 */
public final class McpProxyApplication {

	static final String DEFAULT_BACKEND_URL = "http://localhost:8080";

	private McpProxyApplication() {
	}

	public static void main(String[] args) {
		String backendUrl = System.getenv().getOrDefault("DRUM_COACH_BACKEND_URL", DEFAULT_BACKEND_URL);
		McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();
		BackendClient backendClient = new BackendClient(backendUrl, jsonMapper);

		StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);

		McpSyncServer server = McpServer.sync(transportProvider)
			.serverInfo("drum-coach-mcp", "0.4.0")
			.instructions("O drum-coach e o caderno de treino de bateria do usuario, com voce (Claude) atuando "
					+ "como coach.\n\n"
					+ "MODELO DE DADOS: Meta (Goal) e o nivel mais alto - idealmente so UMA meta fica 'em foco' "
					+ "por vez (use focus_goal para trocar o foco; a tela inicial do app sempre destaca a meta em "
					+ "foco - nao ha suporte a 'nenhuma meta em foco' por escolha do usuario, so trocar de uma "
					+ "para outra). Treino (Training) pertence direto a uma Meta (goalId) ou e avulso (goalId "
					+ "nulo, ex.: aquecimento livre, testar um groove novo) - NAO existe uma camada de 'Plano' "
					+ "entre Meta e Treino (foi removida, ver ADR-0009 no repositorio). Cada Treino tem "
					+ "Exercicios. Execucao (Execution) e o registro real de uma sessao de pratica de um Treino, "
					+ "com BPM/duracao alcancados por exercicio - trainingId tambem pode ser omitido (sessao "
					+ "livre, so cronometro/metronomo sem treino especifico), caso em que nao ha logs por "
					+ "exercicio. Aula (Lesson) registra o que o professor humano "
					+ "passou, e pode gerar um Treino novo (generate_training_from_lesson). Repertorio e uma "
					+ "lista a parte de musicas em aprendizado, nao conectada a Meta/Treino.\n\n"
					+ "COMO COMECAR: no inicio de uma conversa de coaching, prefira chamar get_coach_briefing() "
					+ "primeiro para se situar (meta em foco com progresso dos treinos, aulas e execucoes "
					+ "recentes) em vez de perguntar tudo ao usuario. Quando o usuario descrever em conversa algo "
					+ "que ja fez (ex.: 'treinei hoje, fiz o treino X'), registre com record_execution em vez de "
					+ "so anotar mentalmente.\n\n"
					+ "TOM: aja como um coach discreto - comente quando fizer sentido (ex.: uma meta parada ha "
					+ "tempo, progresso notavel), mas sem forcar engajamento ou gamificar - o usuario valoriza "
					+ "baixa friccao acima de tudo, o app existe justamente para ele nao precisar preencher tudo "
					+ "manualmente. Toda escrita feita por voce fica marcada como origem CLAUDE e visivel para o "
					+ "usuario na interface (cor/badge propria) - e um sistema de auditoria intencional, entao "
					+ "seja transparente sobre o que esta registrando, nao registre nada que o usuario nao tenha "
					+ "pedido ou descrito.\n\n"
					+ "TIPO DO EXERCICIO (kind, ver ADR-0011): todo exercicio tem um eixo 'kind' obrigatorio e "
					+ "IMUTAVEL apos a criacao, separado do 'exerciseType' (texto livre). Dois valores: TOCA_JUNTO "
					+ "= tem um 'pattern' tocavel (uma partitura em grade que roda em loop com metronomo/count-in - "
					+ "grooves, viradas, rudimentos); TRANSCRICAO = trabalho de ouvido, sem pattern, so notas livres "
					+ "em howToExecute + trechos marcados no audio (add_marked_passage: fromSeconds obrigatorio, "
					+ "toSeconds opcional para intervalo, label curto). howToExecute e opcional nos dois tipos "
					+ "(nota livre: 'foco na mao esquerda', 'chimbal fechado').\n\n"
					+ "FORMA DO PATTERN (objeto JSON, so em TOCA_JUNTO): { \"version\": 1, \"timeSignature\": [num, "
					+ "den], \"stepsPerBeat\": int, \"tuplet\": bool, \"bars\": int, \"voices\": [...], \"hits\": "
					+ "{ voz: [steps...] }, \"accents\": { voz: [steps...] } (opcional), \"sticking\": [\"R\",\"L\",...] "
					+ "(opcional) }. Vocabulario fixo de voices: crash, ride, hihat, hiTom, midTom, floorTom, snare, "
					+ "kick. Os indices em 'hits' sao steps 0-based sobre o loop inteiro; total = bars * num * "
					+ "stepsPerBeat, e todo step deve estar em [0, total). 'accents' por voz precisa ser subconjunto "
					+ "dos 'hits' daquela voz. 'sticking' so vale para pattern de voz unica (rudimento) e tem "
					+ "comprimento exatamente igual a total. target_bpm do exercicio = BPM alvo do pattern. O back so "
					+ "valida a estrutura (rejeita com 400 + mensagem se algo nao fecha) - valor ritmico, ligadura e "
					+ "pausa sao derivados no front, nao se enviam.\n\n"
					+ "FLUXO DO PATTERN: comece por list_pattern_presets() (groove-4-4, paradiddle, shuffle) e ajuste "
					+ "a partir de um preset em vez de montar do zero. Para editar um pattern existente: get_exercise "
					+ "(mostra kind, o pattern atual em JSON e os trechos) -> edite o documento inteiro -> "
					+ "update_exercise_pattern (envia o pattern COMPLETO, nao um diff). add_exercise_to_training e os "
					+ "exercicios inline de add_training_to_goal / generate_training_from_lesson agora pedem 'kind' e "
					+ "aceitam 'pattern' opcional.\n\n"
					+ "O back (" + backendUrl + ") precisa estar rodando para as tools funcionarem (exceto o "
					+ "diagnostico de health_check, que reporta a falha de forma amigavel).")
			.capabilities(ServerCapabilities.builder().tools(true).build())
			.tools(allTools(backendClient))
			.build();

		Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully, "mcp-shutdown"));
	}

	private static List<McpServerFeatures.SyncToolSpecification> allTools(BackendClient backendClient) {
		return List.of(
				// Leitura
				listGoalsTool(backendClient), getGoalTool(backendClient), listTrainingsTool(backendClient),
				listExercisesTool(backendClient), getExerciseTool(backendClient),
				getExecutionHistoryTool(backendClient), listLessonsTool(backendClient), getLessonTool(backendClient),
				listRepertoireTool(backendClient), getRepertoireItemTool(backendClient),
				listPatternPresetsTool(backendClient), getCoachBriefingTool(backendClient),
				healthCheckTool(backendClient),
				// Escrita
				createGoalTool(backendClient), updateGoalProgressTool(backendClient), focusGoalTool(backendClient),
				addTrainingToGoalTool(backendClient), addExerciseToTrainingTool(backendClient),
				updateExercisePatternTool(backendClient), addMarkedPassageTool(backendClient),
				recordExecutionTool(backendClient), recordLessonTool(backendClient),
				generateTrainingFromLessonTool(backendClient), addRepertoireItemTool(backendClient),
				updateRepertoireItemTool(backendClient));
	}

	// ===================== Schemas comuns =====================

	private static final JsonSchema NO_ARGS_SCHEMA = JsonSchema.builder()
		.type("object")
		.properties(Map.of())
		.required(List.of())
		.additionalProperties(false)
		.build();

	private static Map<String, Object> stringProp(String description) {
		return Map.of("type", "string", "description", description);
	}

	private static Map<String, Object> intProp(String description) {
		return Map.of("type", "integer", "description", description);
	}

	private static Map<String, Object> enumProp(String description, String... values) {
		return Map.of("type", "string", "description", description, "enum", List.of(values));
	}

	private static Map<String, Object> arrayProp(String description, Map<String, Object> items) {
		return Map.of("type", "array", "description", description, "items", items);
	}

	/**
	 * Schema do {@code pattern} tocavel (ver ADR-0011). Deliberadamente frouxo (objeto
	 * livre) - a validacao estrutural real e do back ({@code DrumPattern}); a forma exata
	 * das chaves esta documentada na descricao e no {@code instructions()} do servidor.
	 */
	private static Map<String, Object> patternProp(String description) {
		return Map.of("type", "object", "description", description, "additionalProperties", true);
	}

	private static Map<String, Object> exerciseItemSchema() {
		return Map.of("type", "object", "properties", Map.ofEntries(
				Map.entry("name", stringProp("Nome do exercicio (obrigatorio).")),
				Map.entry("exerciseType", stringProp("Tipo do exercicio, texto livre (obrigatorio).")),
				Map.entry("kind", enumProp("Como o exercicio e executado (obrigatorio, imutavel): TOCA_JUNTO tem "
						+ "pattern tocavel; TRANSCRICAO e trabalho de ouvido com trechos marcados.", "TOCA_JUNTO",
						"TRANSCRICAO")),
				Map.entry("howToExecute", stringProp("Nota livre de execucao (opcional nos dois kinds).")),
				Map.entry("pattern", patternProp("Documento do padrao tocavel como objeto JSON (opcional, so em "
						+ "TOCA_JUNTO). Chaves: version, timeSignature [num,den], stepsPerBeat, tuplet, bars, voices "
						+ "(crash/ride/hihat/hiTom/midTom/floorTom/snare/kick), hits {voz:[steps]}, accents/sticking "
						+ "opcionais. total = bars*num*stepsPerBeat; steps 0-based em [0,total).")),
				Map.entry("targetBpm", intProp("BPM alvo (opcional; em TOCA_JUNTO e o BPM alvo do pattern).")),
				Map.entry("targetDurationSeconds",
						intProp("Duracao alvo do exercicio em segundos, usada pelo timer do Modo Sessao (opcional).")),
				Map.entry("videoSourceType", enumProp("Origem do video de referencia (opcional).", "LINK", "FILE")),
				Map.entry("videoUrl", stringProp("URL do video de referencia (opcional).")),
				Map.entry("videoFilePath", stringProp("Caminho do arquivo de video (opcional).")),
				Map.entry("orderIndex", intProp("Posicao do exercicio dentro do treino (obrigatorio)."))),
				"required", List.of("name", "exerciseType", "kind", "orderIndex"), "additionalProperties", false);
	}

	private static Map<String, Object> trainingItemSchema() {
		return Map.of("type", "object", "properties",
				Map.of("name", stringProp("Nome do treino (obrigatorio)."), "description",
						stringProp("Descricao do treino (opcional)."), "targetDurationMinutes",
						intProp("Duracao alvo em minutos (obrigatorio)."), "targetRepetitions",
						intProp("Repeticoes alvo - meta soft de referencia, sem prazo proprio (opcional)."),
						"orderIndex", intProp("Posicao do treino, ex.: entre os treinos da mesma meta (obrigatorio)."),
						"exercises", arrayProp("Exercicios deste treino (opcional).", exerciseItemSchema())),
				"required", List.of("name", "targetDurationMinutes", "orderIndex"), "additionalProperties", false);
	}

	private static Map<String, Object> logItemSchema() {
		return Map.of("type", "object", "properties",
				Map.of("exerciseId", intProp("Id do exercicio (obrigatorio)."), "achievedBpm",
						intProp("BPM alcancado (opcional)."), "actualDurationSeconds",
						intProp("Tempo realmente gasto no exercicio, em segundos (opcional)."), "notes",
						stringProp("Notas sobre o exercicio nesta execucao (opcional).")),
				"required", List.of("exerciseId"), "additionalProperties", false);
	}

	private static Map<String, Object> linkItemSchema() {
		return Map.of("type", "object", "properties",
				Map.of("url", stringProp("URL do link de referencia (obrigatorio)."), "label",
						stringProp("Rotulo do link (opcional).")),
				"required", List.of("url"), "additionalProperties", false);
	}

	// ===================== Tools de leitura =====================

	private static McpServerFeatures.SyncToolSpecification listGoalsTool(BackendClient backendClient) {
		Tool tool = Tool.builder("list_goals")
			.description("Lista todas as metas (Goals) de treino de bateria cadastradas no drum-coach.")
			.inputSchema(NO_ARGS_SCHEMA)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				GoalDto[] goals = backendClient.listGoals();
				return okResult(formatGoals(goals));
			}
			catch (Exception e) {
				return errorResult("Falha ao listar metas: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification getGoalTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("id", intProp("Id da meta (obrigatorio).")))
			.required(List.of("id"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("get_goal")
			.description(
					"Busca uma meta (Goal) especifica pelo id, com os treinos que pertencem direto a ela e o "
							+ "progresso de cada um (execucoes registradas vs. repeticoes-alvo) - GET "
							+ "/api/goals/{id} (ver ADR-0009).")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Long id = requiredLongArg(request.arguments(), "id");
				GoalDetailDto detail = backendClient.getGoalDetail(id);
				if (detail == null) {
					return okResult("Nenhuma meta encontrada com id " + id + ".");
				}
				return okResult(formatGoalDetail(detail));
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao buscar meta: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification listTrainingsTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("goalId", intProp("Filtra so os treinos desta meta (opcional).")))
			.required(List.of())
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("list_trainings")
			.description(
					"Lista treinos, opcionalmente filtrados pela meta a qual pertencem (goalId) - GET "
							+ "/api/trainings?goalId=.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Long goalId = longArg(request.arguments(), "goalId");
				TrainingDto[] trainings = backendClient.listTrainings(goalId);
				return okResult(formatTrainings(trainings));
			}
			catch (Exception e) {
				return errorResult("Falha ao listar treinos: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification listExercisesTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("trainingId", intProp("Id do treino cujos exercicios serao listados (obrigatorio).")))
			.required(List.of("trainingId"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("list_exercises")
			.description(
					"Lista os exercicios de um treino, com id, BPM alvo e duracao alvo de cada um - use antes de "
							+ "record_execution para descobrir os exerciseId corretos ao montar os logs de BPM/duracao "
							+ "por exercicio de uma execucao.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Long trainingId = requiredLongArg(request.arguments(), "trainingId");
				ExerciseDto[] exercises = backendClient.listExercises(trainingId);
				return okResult(formatExercises(exercises));
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao listar exercicios: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification getExerciseTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("exerciseId", intProp("Id do exercicio (obrigatorio).")))
			.required(List.of("exerciseId"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("get_exercise")
			.description(
					"Busca um exercicio pelo id (GET /api/exercises/{id}) e mostra, de forma legivel, kind, "
							+ "exerciseType, howToExecute, o BPM/duracao alvo, o 'pattern' completo em JSON (quando "
							+ "TOCA_JUNTO) e os trechos marcados (quando TRANSCRICAO). E a leitura usada para editar um "
							+ "pattern: get_exercise -> editar o JSON -> update_exercise_pattern.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Long id = requiredLongArg(request.arguments(), "exerciseId");
				ExerciseDto exercise = backendClient.getExercise(id);
				if (exercise == null) {
					return okResult("Nenhum exercicio encontrado com id " + id + ".");
				}
				return okResult(formatExerciseDetail(exercise));
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao buscar exercicio: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification getExecutionHistoryTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("trainingId", intProp("Filtra so execucoes deste treino (opcional)."), "goalId",
					intProp("Filtra so execucoes desta meta (opcional).")))
			.required(List.of())
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("get_execution_history")
			.description(
					"Lista o historico de execucoes reais de treino, opcionalmente filtrado por treino "
							+ "(trainingId) e/ou meta (goalId).")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Long trainingId = longArg(request.arguments(), "trainingId");
				Long goalId = longArg(request.arguments(), "goalId");
				ExecutionDto[] executions = backendClient.listExecutions(trainingId, goalId);
				return okResult(formatExecutions(executions));
			}
			catch (Exception e) {
				return errorResult("Falha ao listar historico de execucoes: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification listLessonsTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("from", stringProp("Data inicial yyyy-MM-dd (opcional)."), "to",
					stringProp("Data final yyyy-MM-dd (opcional).")))
			.required(List.of())
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("list_lessons")
			.description("Lista aulas com o professor, opcionalmente filtradas por periodo (from/to, yyyy-MM-dd).")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				String from = stringArg(request.arguments(), "from");
				String to = stringArg(request.arguments(), "to");
				LessonDto[] lessons = backendClient.listLessons(from, to);
				return okResult(formatLessons(lessons));
			}
			catch (Exception e) {
				return errorResult("Falha ao listar aulas: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification getLessonTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("id", intProp("Id da aula (obrigatorio).")))
			.required(List.of("id"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("get_lesson")
			.description(
					"Busca o detalhe completo de uma aula pelo id: o que o professor passou, feedback recebido, "
							+ "foco ate a proxima aula, material sugerido, e o treino gerado a partir dela, se "
							+ "houver (generatedTrainingId).")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Long id = requiredLongArg(request.arguments(), "id");
				LessonDto lesson = backendClient.getLesson(id);
				if (lesson == null) {
					return okResult("Nenhuma aula encontrada com id " + id + ".");
				}
				return okResult(formatLessonDetail(lesson));
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao buscar aula: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification listRepertoireTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("status",
					enumProp("Filtra por status (opcional).", "NOT_STARTED", "LEARNING", "MASTERED")))
			.required(List.of())
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("list_repertoire")
			.description(
					"Lista o repertorio de musicas em aprendizado, opcionalmente filtrado por status. O back nao "
							+ "tem filtro de status em GET /api/repertoire-items - esta tool filtra no proxy.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				String status = stringArg(request.arguments(), "status");
				RepertoireItemDto[] items = backendClient.listRepertoire();
				RepertoireItemDto[] filtered = status == null ? items
						: Arrays.stream(items).filter(i -> status.equals(i.status())).toArray(RepertoireItemDto[]::new);
				return okResult(formatRepertoire(filtered));
			}
			catch (Exception e) {
				return errorResult("Falha ao listar repertorio: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification getRepertoireItemTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("id", intProp("Id do item de repertorio (obrigatorio).")))
			.required(List.of("id"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("get_repertoire_item")
			.description(
					"Busca um item de repertorio especifico pelo id. O back nao tem GET /api/repertoire-items/{id} "
							+ "- esta tool filtra o resultado de GET /api/repertoire-items no proprio proxy.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Long id = requiredLongArg(request.arguments(), "id");
				RepertoireItemDto found = Arrays.stream(backendClient.listRepertoire())
					.filter(i -> id.equals(i.id()))
					.findFirst()
					.orElse(null);
				if (found == null) {
					return okResult("Nenhum item de repertorio encontrado com id " + id + ".");
				}
				return okResult(formatRepertoireItemDetail(found));
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao buscar item de repertorio: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification listPatternPresetsTool(BackendClient backendClient) {
		Tool tool = Tool.builder("list_pattern_presets")
			.description(
					"Sem parametros. Lista pontos de partida nomeados para o 'pattern' de um exercicio TOCA_JUNTO "
							+ "(groove-4-4, paradiddle, shuffle) - cada um com nome, descricao e o objeto JSON do "
							+ "pattern ja valido. Nao ha endpoint no back: os presets sao embutidos no proxy. Use um "
							+ "deles como base em add_exercise_to_training / update_exercise_pattern em vez de montar "
							+ "o documento do zero.")
			.inputSchema(NO_ARGS_SCHEMA)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			StringBuilder sb = new StringBuilder("Presets de pattern (").append(PatternPresets.all().size())
				.append("):\n");
			for (PatternPresets.Preset p : PatternPresets.all()) {
				sb.append("\n## ").append(p.name()).append('\n').append(p.description()).append('\n')
					.append(prettyJson(p.pattern())).append('\n');
			}
			return okResult(sb.toString().stripTrailing());
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification getCoachBriefingTool(BackendClient backendClient) {
		Tool tool = Tool.builder("get_coach_briefing")
			.description(
					"Tool agregadora (sem endpoint correspondente no back): junta metas em foco/andamento com seus "
							+ "treinos e progresso, aulas recentes e execucoes recentes num resumo legivel, para o "
							+ "Claude se situar rapido no inicio de uma conversa de coaching.")
			.inputSchema(NO_ARGS_SCHEMA)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				return okResult(buildCoachBriefing(backendClient));
			}
			catch (Exception e) {
				return errorResult("Falha ao montar o briefing: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification healthCheckTool(BackendClient backendClient) {
		Tool tool = Tool.builder("health_check")
			.description("Verifica se o backend do drum-coach (back/) esta no ar em " + backendClient.baseUrl() + ".")
			.inputSchema(NO_ARGS_SCHEMA)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				backendClient.healthCheck();
				return okResult("back esta no ar em " + backendClient.baseUrl() + ".");
			}
			catch (Exception e) {
				// Falha de diagnostico nao e um erro de execucao da tool - e a resposta
				// esperada quando o back esta fora do ar, entao isError permanece false.
				return okResult("back nao respondeu em " + backendClient.baseUrl() + ": "
						+ friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	// ===================== Tools de escrita =====================

	private static McpServerFeatures.SyncToolSpecification createGoalTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("title",
					Map.of("type", "string", "description", "Titulo da meta (obrigatorio)."), "description",
					Map.of("type", "string", "description", "Descricao livre da meta."), "targetDate",
					Map.of("type", "string", "description", "Data alvo no formato yyyy-MM-dd (opcional)."),
					"targetMetric", Map.of("type", "string", "description",
							"Metrica alvo, ex.: '120 bpm', '3x por semana' (opcional).")))
			.required(List.of("title"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("create_goal")
			.description(
					"Cria uma nova meta (Goal) de treino de bateria. O ideal do produto e ter so uma meta em foco "
							+ "por vez (ver focus_goal) - uma meta nova nao fica em foco automaticamente; se o "
							+ "usuario estiver claramente substituindo o foco atual por esta meta nova, considere "
							+ "chamar focus_goal em seguida.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			Map<String, Object> args = request.arguments();
			String title = stringArg(args, "title");
			if (title == null || title.isBlank()) {
				return errorResult("O parametro 'title' e obrigatorio.");
			}
			try {
				GoalDto created = backendClient.createGoal(title, stringArg(args, "description"),
						stringArg(args, "targetDate"), stringArg(args, "targetMetric"));
				return okResult("Meta criada com sucesso (id " + created.id() + "): \"" + created.title()
						+ "\" - status " + created.status() + ", createdBy=" + created.createdBy() + ".");
			}
			catch (Exception e) {
				return errorResult("Falha ao criar meta: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification updateGoalProgressTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("id", intProp("Id da meta a atualizar (obrigatorio)."), "status",
					enumProp("Novo status da meta (opcional - mantem o atual se omitido).", "NOT_STARTED",
							"IN_PROGRESS", "ACHIEVED", "ABANDONED"),
					"notes", stringProp(
							"Notas de progresso (opcional - grava no campo description da meta; mantem o atual se omitido).")))
			.required(List.of("id"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("update_goal_progress")
			.description(
					"Atualiza parcialmente o progresso de uma meta (status e/ou notas). PATCH /api/goals/{id}. "
							+ "Para marcar a meta como foco do Dashboard, use a tool separada focus_goal.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Long id = requiredLongArg(request.arguments(), "id");
				String status = stringArg(request.arguments(), "status");
				String notes = stringArg(request.arguments(), "notes");
				GoalDto updated = backendClient.updateGoal(id, status, notes, null);
				return okResult("Meta " + updated.id() + " atualizada: \"" + updated.title() + "\" - status "
						+ updated.status() + ", lastModifiedBy=" + updated.lastModifiedBy() + ".");
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao atualizar meta: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification focusGoalTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("goalId", intProp("Id da meta a marcar como foco do Dashboard (obrigatorio).")))
			.required(List.of("goalId"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("focus_goal")
			.description(
					"Marca uma meta como 'em foco' no Dashboard (PATCH /api/goals/{id} com inFocus:true), "
							+ "desfocando atomicamente qualquer outra meta que estivesse em foco - o back garante no "
							+ "maximo uma meta em foco por vez (ver ADR-0009). E o usuario/Claude que decide qual "
							+ "meta fica em foco, o sistema nao escolhe sozinho. Nao ha suporte a desfocar isolado "
							+ "(inFocus:false) - para desfocar, foque outra meta.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Long goalId = requiredLongArg(request.arguments(), "goalId");
				GoalDto updated = backendClient.updateGoal(goalId, null, null, true);
				return okResult("Meta " + updated.id() + " marcada em foco: \"" + updated.title() + "\".");
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao focar meta: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification addTrainingToGoalTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("goalId",
					intProp("Id da meta a qual o treino pertence (opcional - nulo cria um treino avulso, sem meta "
							+ "associada, ver ADR-0009)."),
					"training", trainingItemSchema()))
			.required(List.of("training"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("add_training_to_goal")
			.description(
					"Cria um treino (com seus exercicios, opcionais), sob uma meta (goalId) ou avulso (goalId "
							+ "omitido) - POST /api/trainings + POST .../exercises para cada exercicio, em sequencia. "
							+ "Cada exercicio inline exige 'kind' (TOCA_JUNTO|TRANSCRICAO) e aceita 'pattern' "
							+ "opcional (objeto JSON, so em TOCA_JUNTO); 'howToExecute' e opcional.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Map<String, Object> args = request.arguments();
				Long goalId = longArg(args, "goalId");
				Object trainingRaw = args == null ? null : args.get("training");
				if (!(trainingRaw instanceof Map<?, ?>)) {
					return errorResult("O parametro 'training' e obrigatorio e deve ser um objeto.");
				}
				Map<String, Object> t = asStringObjectMap(trainingRaw);
				TrainingCreationResult result = createTrainingWithExercises(backendClient, goalId, t);

				StringBuilder sb = new StringBuilder("Treino criado (id ").append(result.training().id())
					.append(')').append(goalId != null ? " na meta " + goalId : " avulso (sem meta)").append(": \"")
					.append(result.training().name()).append('"');
				if (result.exercisesCreated() > 0) {
					sb.append(", ").append(result.exercisesCreated()).append(" exercicio(s) criado(s)");
				}
				sb.append('.');
				for (String w : result.warnings()) {
					sb.append('\n').append("- AVISO: ").append(w);
				}
				return okResult(sb.toString());
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao adicionar treino: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification addExerciseToTrainingTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("trainingId",
					intProp("Id do treino ao qual o exercicio sera adicionado (obrigatorio)."), "exercise",
					exerciseItemSchema()))
			.required(List.of("trainingId", "exercise"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("add_exercise_to_training")
			.description(
					"Adiciona um exercicio a um treino ja existente (use list_trainings/get_goal para achar o "
							+ "trainingId certo). 'kind' e obrigatorio (TOCA_JUNTO|TRANSCRICAO, imutavel depois); "
							+ "'pattern' (objeto JSON) e opcional e so vale em TOCA_JUNTO - veja list_pattern_presets "
							+ "e o instructions do servidor para a forma. 'howToExecute' e opcional. Se estiver "
							+ "criando o treino do zero, prefira passar os exercicios direto em add_training_to_goal.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Map<String, Object> args = request.arguments();
				Long trainingId = requiredLongArg(args, "trainingId");
				Object exerciseRaw = args == null ? null : args.get("exercise");
				if (!(exerciseRaw instanceof Map<?, ?>)) {
					return errorResult("O parametro 'exercise' e obrigatorio e deve ser um objeto.");
				}
				Map<String, Object> e = asStringObjectMap(exerciseRaw);
				String name = requiredStringArg(e, "name");
				String exerciseType = requiredStringArg(e, "exerciseType");
				String kind = requiredStringArg(e, "kind");
				int orderIndex = requiredIntArg(e, "orderIndex");
				ExerciseDto created = backendClient.createExercise(trainingId, name, exerciseType, kind,
						stringArg(e, "howToExecute"), e.get("pattern"), intArg(e, "targetBpm"),
						intArg(e, "targetDurationSeconds"), stringArg(e, "videoSourceType"), stringArg(e, "videoUrl"),
						stringArg(e, "videoFilePath"), orderIndex);
				return okResult("Exercicio criado (id " + created.id() + ") no treino " + trainingId + ": \""
						+ created.name() + "\" (kind " + created.kind() + ").");
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao adicionar exercicio: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification updateExercisePatternTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("exerciseId", intProp("Id do exercicio TOCA_JUNTO a atualizar (obrigatorio)."),
					"pattern", patternProp("Documento COMPLETO do padrao tocavel como objeto JSON (obrigatorio) - "
							+ "nao e um diff/patch incremental, e o padrao inteiro que substitui o atual. Chaves: "
							+ "version, timeSignature [num,den], stepsPerBeat, tuplet, bars, voices "
							+ "(crash/ride/hihat/hiTom/midTom/floorTom/snare/kick), hits {voz:[steps]}, accents/"
							+ "sticking opcionais. total = bars*num*stepsPerBeat; steps 0-based em [0,total)."),
					"howToExecute", stringProp("Nota livre de execucao (opcional; se omitido, mantem a atual).")))
			.required(List.of("exerciseId", "pattern"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("update_exercise_pattern")
			.description(
					"Substitui o 'pattern' de um exercicio TOCA_JUNTO pelo documento JSON inteiro informado (PATCH "
							+ "/api/exercises/{id}). Fluxo tipico: get_exercise para ler o pattern atual, editar o JSON "
							+ "completo e reenviar aqui. 'kind' e imutavel e nao e enviado. O back valida a estrutura "
							+ "e rejeita com erro (400 + mensagem) se algo nao fecha (step fora de [0,total), voz fora "
							+ "do vocabulario, sticking com tamanho errado, acento sem hit, etc).")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Map<String, Object> args = request.arguments();
				Long id = requiredLongArg(args, "exerciseId");
				Object pattern = args == null ? null : args.get("pattern");
				if (!(pattern instanceof Map<?, ?>)) {
					return errorResult("O parametro 'pattern' e obrigatorio e deve ser o objeto JSON do padrao "
							+ "completo.");
				}
				ExerciseDto updated = backendClient.updateExercisePattern(id, pattern, stringArg(args, "howToExecute"));
				return okResult("Pattern do exercicio " + updated.id() + " (\"" + updated.name()
						+ "\") atualizado. lastModifiedBy=" + updated.lastModifiedBy() + ".");
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao atualizar pattern: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification addMarkedPassageTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("exerciseId", intProp("Id do exercicio (obrigatorio; tipicamente TRANSCRICAO)."),
					"fromSeconds", intProp("Segundo de inicio do trecho (obrigatorio, >= 0)."), "toSeconds",
					intProp("Segundo de fim do trecho (opcional - omita para uma marcacao pontual; se informado, "
							+ ">= fromSeconds)."),
					"label", stringProp("Rotulo curto do trecho (opcional).")))
			.required(List.of("exerciseId", "fromSeconds"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("add_marked_passage")
			.description(
					"Adiciona um trecho marcado a um exercicio (POST /api/exercises/{id}/passages) - o trabalho "
							+ "incremental de um exercicio TRANSCRICAO: pontos/intervalos do audio com um rotulo curto "
							+ "('virada dificil aos 1:12'). Trecho pontual = so fromSeconds; intervalo = fromSeconds + "
							+ "toSeconds.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Map<String, Object> args = request.arguments();
				Long exerciseId = requiredLongArg(args, "exerciseId");
				int fromSeconds = requiredIntArg(args, "fromSeconds");
				MarkedPassageDto passage = backendClient.addMarkedPassage(exerciseId, fromSeconds,
						intArg(args, "toSeconds"), stringArg(args, "label"));
				StringBuilder sb = new StringBuilder("Trecho marcado criado (id ").append(passage.id())
					.append(") no exercicio ").append(exerciseId).append(": de ").append(passage.fromSeconds())
					.append('s');
				if (passage.toSeconds() != null) {
					sb.append(" ate ").append(passage.toSeconds()).append('s');
				}
				if (passage.label() != null && !passage.label().isBlank()) {
					sb.append(" - \"").append(passage.label()).append('"');
				}
				sb.append('.');
				return okResult(sb.toString());
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao adicionar trecho marcado: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification recordExecutionTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("trainingId",
					intProp("Id do treino executado (opcional - omita para uma sessao livre, sem treino "
							+ "especifico: so cronometro/metronomo, sem logs de exercicio)."),
					"executionDate", stringProp("Data da execucao yyyy-MM-dd (obrigatorio)."),
					"actualDurationMinutes", intProp("Duracao real em minutos (opcional)."), "feeling",
					stringProp("Como foi a sensacao do treino, texto livre (opcional)."), "generalNotes",
					stringProp("Notas gerais sobre a execucao (opcional)."), "goalId",
					intProp("Id da meta relacionada a esta execucao (opcional)."), "logs",
					arrayProp("Logs de BPM/duracao/notas por exercicio (opcional, so faz sentido com trainingId).",
							logItemSchema())))
			.required(List.of("executionDate"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("record_execution")
			.description(
					"Registra uma sessao de pratica real - use esta tool sempre que o usuario contar em "
							+ "conversa que ja praticou algo ('treinei hoje', 'fiz o treino X ontem', 'fiquei "
							+ "batucando sem seguir nada'). Aceita logs opcionais de BPM alcancado/duracao "
							+ "real/notas por exercicio (use list_trainings ou get_goal para descobrir o "
							+ "trainingId e os exercicios certos antes de chamar, se nao souber de cor). Omita "
							+ "trainingId pra uma sessao livre (sem treino especifico). 'feeling' e texto livre, "
							+ "sem enum fixo.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Map<String, Object> args = request.arguments();
				Long trainingId = longArg(args, "trainingId");
				String executionDate = requiredStringArg(args, "executionDate");
				List<ExecutionLogInput> logs = parseLogs(args.get("logs"));
				ExecutionDto created = backendClient.createExecution(trainingId, executionDate,
						intArg(args, "actualDurationMinutes"), stringArg(args, "feeling"),
						stringArg(args, "generalNotes"), longArg(args, "goalId"), logs);
				String treinoDescricao = trainingId != null ? "do treino " + trainingId : "(sessao livre)";
				return okResult("Execucao registrada (id " + created.id() + ") " + treinoDescricao + " em "
						+ created.executionDate() + ", " + created.logs().size() + " log(s) de exercicio.");
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao registrar execucao: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification recordLessonTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("lessonDate", stringProp("Data da aula yyyy-MM-dd (obrigatorio)."), "teacherNotes",
					stringProp("O que foi passado na aula (obrigatorio)."), "feedback",
					stringProp("Feedback recebido do professor (opcional)."), "focusUntilNext",
					stringProp("Foco ate a proxima aula (opcional)."), "suggestedMaterial",
					stringProp("Material sugerido pelo professor (opcional)."), "generatedTrainingId",
					intProp("Id de um treino ja existente gerado por esta aula (opcional).")))
			.required(List.of("lessonDate", "teacherNotes"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("record_lesson")
			.description(
					"Registra uma aula com o professor humano: o que foi passado (teacherNotes, obrigatorio), "
							+ "feedback recebido, e o foco sugerido ate a proxima aula. Depois de registrar, se a "
							+ "aula pedir pra treinar algo novo, considere usar generate_training_from_lesson para "
							+ "ja criar o treino correspondente e vincula-lo a esta aula.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Map<String, Object> args = request.arguments();
				String lessonDate = requiredStringArg(args, "lessonDate");
				String teacherNotes = requiredStringArg(args, "teacherNotes");
				LessonDto created = backendClient.createLesson(lessonDate, teacherNotes, stringArg(args, "feedback"),
						stringArg(args, "focusUntilNext"), stringArg(args, "suggestedMaterial"),
						longArg(args, "generatedTrainingId"));
				return okResult("Aula registrada (id " + created.id() + ") em " + created.lessonDate() + ".");
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao registrar aula: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification generateTrainingFromLessonTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("lessonId", intProp("Id da aula que gerou o treino (obrigatorio)."), "goalId",
					intProp("Id da meta a qual o treino pertence, se fizer sentido no contexto da aula (opcional - "
							+ "nulo cria um treino avulso, ver ADR-0009)."),
					"training", trainingItemSchema()))
			.required(List.of("lessonId", "training"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("generate_training_from_lesson")
			.description(
					"Cria um treino (com exercicios, se informados) a partir de uma aula - sob uma meta (goalId) ou "
							+ "avulso - e vincula lesson.generatedTrainingId a ele via PATCH /api/lessons/{id}. Cada "
							+ "exercicio inline exige 'kind' (TOCA_JUNTO|TRANSCRICAO) e aceita 'pattern' opcional.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Map<String, Object> args = request.arguments();
				Long lessonId = requiredLongArg(args, "lessonId");
				Long goalId = longArg(args, "goalId");
				Object trainingRaw = args == null ? null : args.get("training");
				if (!(trainingRaw instanceof Map<?, ?>)) {
					return errorResult("O parametro 'training' e obrigatorio e deve ser um objeto.");
				}
				Map<String, Object> t = asStringObjectMap(trainingRaw);

				LessonDto lesson = backendClient.getLesson(lessonId);
				if (lesson == null) {
					return errorResult("Nenhuma aula encontrada com id " + lessonId + " - treino nao foi criado.");
				}

				TrainingCreationResult result = createTrainingWithExercises(backendClient, goalId, t);

				StringBuilder sb = new StringBuilder("Treino criado (id ").append(result.training().id())
					.append(')').append(goalId != null ? " na meta " + goalId : " avulso (sem meta)").append(": \"")
					.append(result.training().name()).append('"');
				if (result.exercisesCreated() > 0) {
					sb.append(", ").append(result.exercisesCreated()).append(" exercicio(s) criado(s)");
				}
				sb.append('.');
				for (String w : result.warnings()) {
					sb.append('\n').append("- AVISO: ").append(w);
				}

				try {
					backendClient.linkGeneratedTraining(lessonId, result.training().id());
				}
				catch (Exception e) {
					sb.append("\n\nAVISO: treino criado, mas falhou ao vincular a aula ").append(lessonId)
						.append(" (lesson.generatedTrainingId): ").append(friendlyMessage(e, backendClient.baseUrl()));
					return okResult(sb.toString());
				}

				return okResult("Aula " + lessonId + " vinculada ao treino gerado.\n" + sb);
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao gerar treino a partir da aula: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification addRepertoireItemTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("songTitle", stringProp("Titulo da musica (obrigatorio)."), "artist",
					stringProp("Artista/banda (opcional)."), "targetBpm", intProp("BPM alvo (opcional)."),
					"currentBpm", intProp("BPM atual (opcional)."), "notes", stringProp("Notas (opcional)."),
					"links", arrayProp("Links de referencia (opcional).", linkItemSchema())))
			.required(List.of("songTitle"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("add_repertoire_item")
			.description(
					"Adiciona uma musica ao repertorio - uma lista independente de Metas/Treinos, so para "
							+ "acompanhar musicas que o usuario esta aprendendo (status, bpm atual/alvo, links de "
							+ "referencia). Aceita varios links de uma vez.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Map<String, Object> args = request.arguments();
				String songTitle = requiredStringArg(args, "songTitle");
				List<RepertoireLinkInput> links = parseLinks(args.get("links"));
				RepertoireItemDto created = backendClient.createRepertoireItem(songTitle, stringArg(args, "artist"),
						intArg(args, "targetBpm"), intArg(args, "currentBpm"), stringArg(args, "notes"), links);
				return okResult("Item de repertorio criado (id " + created.id() + "): \"" + created.songTitle()
						+ "\" - status " + created.status() + ".");
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao adicionar item de repertorio: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	private static McpServerFeatures.SyncToolSpecification updateRepertoireItemTool(BackendClient backendClient) {
		JsonSchema schema = JsonSchema.builder()
			.type("object")
			.properties(Map.of("id", intProp("Id do item de repertorio a atualizar (obrigatorio)."), "status",
					enumProp("Novo status (opcional - mantem o atual se omitido).", "NOT_STARTED", "LEARNING",
							"MASTERED"),
					"currentBpm", intProp("Novo BPM atual (opcional - mantem o atual se omitido)."), "notes",
					stringProp("Novas notas (opcional - mantem o atual se omitido)."), "newLinks",
					arrayProp("Novos links a adicionar (opcional - nunca edita/remove links existentes).",
							linkItemSchema())))
			.required(List.of("id"))
			.additionalProperties(false)
			.build();

		Tool tool = Tool.builder("update_repertoire_item")
			.description(
					"Atualiza parcialmente um item de repertorio (status/BPM atual/notas) e/ou adiciona novos "
							+ "links de referencia.")
			.inputSchema(schema)
			.build();

		return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
			try {
				Map<String, Object> args = request.arguments();
				Long id = requiredLongArg(args, "id");
				List<RepertoireLinkInput> newLinks = parseLinks(args.get("newLinks"));
				RepertoireItemDto updated = backendClient.updateRepertoireItem(id, stringArg(args, "status"),
						intArg(args, "currentBpm"), stringArg(args, "notes"), newLinks);
				return okResult("Item de repertorio " + updated.id() + " atualizado: \"" + updated.songTitle()
						+ "\" - status " + updated.status() + ", currentBpm=" + updated.currentBpm() + ".");
			}
			catch (IllegalArgumentException e) {
				return errorResult(e.getMessage());
			}
			catch (Exception e) {
				return errorResult("Falha ao atualizar item de repertorio: " + friendlyMessage(e, backendClient.baseUrl()));
			}
		}).build();
	}

	// ===================== Orquestracao (tools compostas) =====================

	/** Resultado de {@code createTrainingWithExercises}: o treino criado + contadores + avisos de falhas parciais. */
	private record TrainingCreationResult(TrainingDto training, int exercisesCreated, List<String> warnings) {
	}

	/**
	 * Orquestra {@code POST /api/trainings} e, para cada exercicio informado,
	 * {@code POST .../exercises} - tudo sequencial no proprio proxy (sem endpoint composto
	 * no back). Falhas em exercicios individuais nao interrompem o resto - viram avisos no
	 * resultado (best effort, ja que nao ha transacao entre chamadas HTTP separadas).
	 * Compartilhada por {@code add_training_to_goal} e {@code generate_training_from_lesson}.
	 */
	private static TrainingCreationResult createTrainingWithExercises(BackendClient backendClient, Long goalId,
			Map<String, Object> t) throws IOException, InterruptedException {
		String name = requiredStringArg(t, "name");
		int targetDurationMinutes = requiredIntArg(t, "targetDurationMinutes");
		int orderIndex = requiredIntArg(t, "orderIndex");
		TrainingDto created = backendClient.createTraining(goalId, name, stringArg(t, "description"),
				targetDurationMinutes, intArg(t, "targetRepetitions"), orderIndex);
		int exercisesCreated = 0;
		List<String> warnings = new ArrayList<>();
		for (ExerciseInput e : parseExercises(t.get("exercises"))) {
			try {
				backendClient.createExercise(created.id(), e.name(), e.exerciseType(), e.kind(), e.howToExecute(),
						e.pattern(), e.targetBpm(), e.targetDurationSeconds(), e.videoSourceType(), e.videoUrl(),
						e.videoFilePath(), e.orderIndex());
				exercisesCreated++;
			}
			catch (Exception ex) {
				warnings.add(
						"Falha ao criar exercicio '" + e.name() + "': " + friendlyMessage(ex, backendClient.baseUrl()));
			}
		}
		return new TrainingCreationResult(created, exercisesCreated, warnings);
	}

	private static String buildCoachBriefing(BackendClient backendClient) throws IOException, InterruptedException {
		StringBuilder sb = new StringBuilder("BRIEFING DO COACH - drum-coach\n");

		GoalDto[] goals = backendClient.listGoals();
		List<GoalDto> relevantGoals = Arrays.stream(goals)
			.filter(g -> g.inFocus() || "IN_PROGRESS".equals(g.status()))
			.sorted(Comparator.comparing(GoalDto::inFocus).reversed())
			.toList();
		sb.append("\n## Metas em foco/andamento (").append(relevantGoals.size()).append(")\n");
		if (relevantGoals.isEmpty()) {
			sb.append("Nenhuma meta em foco ou em andamento no momento.\n");
		}
		else {
			for (GoalDto g : relevantGoals) {
				sb.append("- ").append(g.inFocus() ? "[FOCO] " : "").append('[').append(g.status()).append("] ")
					.append(g.title()).append(" (id ").append(g.id()).append(')');
				if (g.targetMetric() != null && !g.targetMetric().isBlank()) {
					sb.append(" - meta: ").append(g.targetMetric());
				}
				sb.append('\n');
				GoalDetailDto detail = backendClient.getGoalDetail(g.id());
				if (detail != null && !detail.trainings().isEmpty()) {
					for (TrainingProgressDto tp : detail.trainings()) {
						sb.append("    - ").append(tp.name()).append(": ").append(tp.completedCount());
						if (tp.targetRepetitions() != null) {
							sb.append('/').append(tp.targetRepetitions());
						}
						sb.append(" execucao(oes)\n");
					}
				}
			}
		}

		LessonDto[] lessons = backendClient.listLessons(null, null);
		List<LessonDto> recentLessons = Arrays.stream(lessons)
			.sorted(Comparator.comparing(LessonDto::lessonDate).reversed())
			.limit(3)
			.toList();
		sb.append("\n## Aulas recentes (ultimas ").append(recentLessons.size()).append(")\n");
		if (recentLessons.isEmpty()) {
			sb.append("Nenhuma aula registrada ainda.\n");
		}
		else {
			for (LessonDto l : recentLessons) {
				sb.append("- [id ").append(l.id()).append("] ").append(l.lessonDate());
				if (l.focusUntilNext() != null && !l.focusUntilNext().isBlank()) {
					sb.append(" - foco: ").append(l.focusUntilNext());
				}
				sb.append('\n');
			}
		}

		ExecutionDto[] executions = backendClient.listExecutions(null, null);
		List<ExecutionDto> recentExecutions = Arrays.stream(executions)
			.sorted(Comparator.comparing(ExecutionDto::executionDate).reversed())
			.limit(5)
			.toList();
		sb.append("\n## Execucoes recentes (ultimas ").append(recentExecutions.size()).append(")\n");
		if (recentExecutions.isEmpty()) {
			sb.append("Nenhuma execucao registrada ainda.\n");
		}
		else {
			for (ExecutionDto e : recentExecutions) {
				sb.append("- [id ").append(e.id()).append("] ")
					.append(e.trainingId() != null ? "treino " + e.trainingId() : "sessao livre")
					.append(" em ")
					.append(e.executionDate());
				if (e.feeling() != null && !e.feeling().isBlank()) {
					sb.append(" - sensacao: ").append(e.feeling());
				}
				sb.append('\n');
			}
		}

		return sb.toString().stripTrailing();
	}

	// ===================== Formatacao de resultados =====================

	private static String formatGoals(GoalDto[] goals) {
		if (goals.length == 0) {
			return "Nenhuma meta cadastrada ainda.";
		}
		StringBuilder sb = new StringBuilder();
		for (GoalDto g : goals) {
			sb.append("- ").append(g.inFocus() ? "[FOCO] " : "").append('[').append(g.status()).append("] ")
				.append(g.title()).append(" (id ").append(g.id()).append(')');
			if (g.targetDate() != null) {
				sb.append(", meta: ").append(g.targetDate());
			}
			if (g.targetMetric() != null && !g.targetMetric().isBlank()) {
				sb.append(", metrica: ").append(g.targetMetric());
			}
			sb.append('\n');
		}
		return sb.toString().stripTrailing();
	}

	private static String formatGoalDetail(GoalDetailDto detail) {
		GoalDto g = detail.goal();
		StringBuilder sb = new StringBuilder();
		sb.append("Meta ").append(g.id()).append(": \"").append(g.title()).append("\"\n");
		sb.append("Status: ").append(g.status());
		if (g.inFocus()) {
			sb.append(" - EM FOCO");
		}
		sb.append('\n');
		if (g.description() != null && !g.description().isBlank()) {
			sb.append("Descricao: ").append(g.description()).append('\n');
		}
		if (g.targetDate() != null) {
			sb.append("Data alvo: ").append(g.targetDate()).append('\n');
		}
		if (g.targetMetric() != null && !g.targetMetric().isBlank()) {
			sb.append("Metrica alvo: ").append(g.targetMetric()).append('\n');
		}
		sb.append("Criada por: ").append(g.createdBy()).append(", ultima modificacao por: ")
			.append(g.lastModifiedBy()).append('\n');
		if (detail.trainings().isEmpty()) {
			sb.append("Nenhum treino cadastrado nesta meta ainda.");
			return sb.toString();
		}
		sb.append("Treinos:\n");
		for (TrainingProgressDto tp : detail.trainings()) {
			sb.append("- [id ").append(tp.trainingId()).append("] ").append(tp.name()).append(": ")
				.append(tp.completedCount());
			if (tp.targetRepetitions() != null) {
				sb.append('/').append(tp.targetRepetitions());
			}
			sb.append(" execucao(oes)\n");
		}
		return sb.toString().stripTrailing();
	}

	private static String formatTrainings(TrainingDto[] trainings) {
		if (trainings.length == 0) {
			return "Nenhum treino cadastrado.";
		}
		StringBuilder sb = new StringBuilder();
		for (TrainingDto t : trainings) {
			sb.append("- [id ").append(t.id()).append(']').append(' ').append(t.name());
			if (t.goalId() != null) {
				sb.append(" (meta ").append(t.goalId()).append(')');
			}
			else {
				sb.append(" (avulso)");
			}
			sb.append(", ").append(t.targetDurationMinutes()).append(" min");
			if (t.targetRepetitions() != null) {
				sb.append(", meta ").append(t.targetRepetitions()).append("x");
			}
			sb.append('\n');
		}
		return sb.toString().stripTrailing();
	}

	private static String formatExercises(ExerciseDto[] exercises) {
		if (exercises.length == 0) {
			return "Nenhum exercicio cadastrado neste treino.";
		}
		StringBuilder sb = new StringBuilder();
		for (ExerciseDto e : exercises) {
			sb.append("- [id ").append(e.id()).append("] ").append(e.name()).append(" (").append(e.exerciseType())
				.append(')');
			if (e.targetBpm() != null) {
				sb.append(", alvo ").append(e.targetBpm()).append(" bpm");
			}
			if (e.targetDurationSeconds() != null) {
				sb.append(", ").append(e.targetDurationSeconds()).append('s');
			}
			sb.append('\n');
		}
		return sb.toString().stripTrailing();
	}

	private static String formatExerciseDetail(ExerciseDto e) {
		StringBuilder sb = new StringBuilder();
		sb.append("Exercicio ").append(e.id()).append(": \"").append(e.name()).append("\"\n");
		sb.append("kind: ").append(e.kind()).append('\n');
		sb.append("exerciseType: ").append(e.exerciseType()).append('\n');
		if (e.trainingId() != null) {
			sb.append("Treino: ").append(e.trainingId()).append('\n');
		}
		if (e.howToExecute() != null && !e.howToExecute().isBlank()) {
			sb.append("howToExecute: ").append(e.howToExecute()).append('\n');
		}
		if (e.targetBpm() != null) {
			sb.append("BPM alvo: ").append(e.targetBpm()).append('\n');
		}
		if (e.targetDurationSeconds() != null) {
			sb.append("Duracao alvo: ").append(e.targetDurationSeconds()).append("s\n");
		}
		if (e.pattern() != null) {
			sb.append("pattern (JSON):\n").append(prettyJson(e.pattern())).append('\n');
		}
		else if ("TOCA_JUNTO".equals(e.kind())) {
			sb.append("pattern: (ainda sem padrao - use update_exercise_pattern para definir)\n");
		}
		List<MarkedPassageDto> passages = e.passages();
		if (passages != null && !passages.isEmpty()) {
			sb.append("Trechos marcados (").append(passages.size()).append("):\n");
			for (MarkedPassageDto p : passages) {
				sb.append("  - [id ").append(p.id()).append("] de ").append(p.fromSeconds()).append('s');
				if (p.toSeconds() != null) {
					sb.append(" ate ").append(p.toSeconds()).append('s');
				}
				if (p.label() != null && !p.label().isBlank()) {
					sb.append(" - \"").append(p.label()).append('"');
				}
				sb.append('\n');
			}
		}
		else if ("TRANSCRICAO".equals(e.kind())) {
			sb.append("Trechos marcados: nenhum ainda (use add_marked_passage).\n");
		}
		return sb.toString().stripTrailing();
	}

	private static String formatExecutions(ExecutionDto[] executions) {
		if (executions.length == 0) {
			return "Nenhuma execucao registrada.";
		}
		StringBuilder sb = new StringBuilder();
		for (ExecutionDto e : executions) {
			sb.append("- [id ").append(e.id()).append("] ")
				.append(e.trainingId() != null ? "treino " + e.trainingId() : "sessao livre")
				.append(" em ")
				.append(e.executionDate());
			if (e.actualDurationMinutes() != null) {
				sb.append(", ").append(e.actualDurationMinutes()).append(" min");
			}
			if (e.feeling() != null && !e.feeling().isBlank()) {
				sb.append(", sensacao: ").append(e.feeling());
			}
			if (!e.logs().isEmpty()) {
				sb.append(", logs: ");
				boolean first = true;
				for (ExecutionExerciseLogDto log : e.logs()) {
					if (!first) {
						sb.append("; ");
					}
					first = false;
					sb.append("exercicio ").append(log.exerciseId());
					if (log.achievedBpm() != null) {
						sb.append('@').append(log.achievedBpm()).append("bpm");
					}
					if (log.actualDurationSeconds() != null) {
						sb.append('/').append(log.actualDurationSeconds()).append('s');
					}
				}
			}
			sb.append('\n');
		}
		return sb.toString().stripTrailing();
	}

	private static String formatLessons(LessonDto[] lessons) {
		if (lessons.length == 0) {
			return "Nenhuma aula registrada.";
		}
		StringBuilder sb = new StringBuilder();
		for (LessonDto l : lessons) {
			sb.append("- [id ").append(l.id()).append("] ").append(l.lessonDate());
			if (l.focusUntilNext() != null && !l.focusUntilNext().isBlank()) {
				sb.append(" - foco: ").append(l.focusUntilNext());
			}
			if (l.generatedTrainingId() != null) {
				sb.append(" (gerou treino ").append(l.generatedTrainingId()).append(')');
			}
			sb.append('\n');
		}
		return sb.toString().stripTrailing();
	}

	private static String formatLessonDetail(LessonDto l) {
		StringBuilder sb = new StringBuilder();
		sb.append("Aula ").append(l.id()).append(" em ").append(l.lessonDate()).append('\n');
		sb.append("O que foi passado: ").append(l.teacherNotes()).append('\n');
		if (l.feedback() != null && !l.feedback().isBlank()) {
			sb.append("Feedback: ").append(l.feedback()).append('\n');
		}
		if (l.focusUntilNext() != null && !l.focusUntilNext().isBlank()) {
			sb.append("Foco ate a proxima: ").append(l.focusUntilNext()).append('\n');
		}
		if (l.suggestedMaterial() != null && !l.suggestedMaterial().isBlank()) {
			sb.append("Material sugerido: ").append(l.suggestedMaterial()).append('\n');
		}
		if (l.generatedTrainingId() != null) {
			sb.append("Treino gerado: id ").append(l.generatedTrainingId()).append('\n');
		}
		return sb.toString().stripTrailing();
	}

	private static String formatRepertoire(RepertoireItemDto[] items) {
		if (items.length == 0) {
			return "Nenhum item de repertorio cadastrado.";
		}
		StringBuilder sb = new StringBuilder();
		for (RepertoireItemDto i : items) {
			sb.append("- [").append(i.status()).append("] ").append(i.songTitle());
			if (i.artist() != null && !i.artist().isBlank()) {
				sb.append(" - ").append(i.artist());
			}
			sb.append(" (id ").append(i.id()).append(')');
			if (i.currentBpm() != null || i.targetBpm() != null) {
				sb.append(", bpm ").append(i.currentBpm() != null ? i.currentBpm() : "?").append('/')
					.append(i.targetBpm() != null ? i.targetBpm() : "?");
			}
			sb.append('\n');
		}
		return sb.toString().stripTrailing();
	}

	private static String formatRepertoireItemDetail(RepertoireItemDto i) {
		StringBuilder sb = new StringBuilder();
		sb.append("Item de repertorio ").append(i.id()).append(": \"").append(i.songTitle()).append('"');
		if (i.artist() != null && !i.artist().isBlank()) {
			sb.append(" - ").append(i.artist());
		}
		sb.append('\n');
		sb.append("Status: ").append(i.status()).append('\n');
		if (i.currentBpm() != null || i.targetBpm() != null) {
			sb.append("BPM atual/alvo: ").append(i.currentBpm() != null ? i.currentBpm() : "?").append('/')
				.append(i.targetBpm() != null ? i.targetBpm() : "?").append('\n');
		}
		if (i.notes() != null && !i.notes().isBlank()) {
			sb.append("Notas: ").append(i.notes()).append('\n');
		}
		if (!i.links().isEmpty()) {
			sb.append("Links:\n");
			for (RepertoireLinkDto link : i.links()) {
				sb.append("  - ").append(link.url());
				if (link.label() != null && !link.label().isBlank()) {
					sb.append(" (").append(link.label()).append(')');
				}
				sb.append('\n');
			}
		}
		return sb.toString().stripTrailing();
	}

	// ===================== Parsing de argumentos =====================

	private static String stringArg(Map<String, Object> args, String key) {
		if (args == null) {
			return null;
		}
		Object value = args.get(key);
		return value == null ? null : value.toString();
	}

	private static String requiredStringArg(Map<String, Object> args, String key) {
		String value = stringArg(args, key);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("O parametro '" + key + "' e obrigatorio.");
		}
		return value;
	}

	private static Long longArg(Map<String, Object> args, String key) {
		if (args == null) {
			return null;
		}
		Object value = args.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number n) {
			return n.longValue();
		}
		String s = value.toString();
		if (s.isBlank()) {
			return null;
		}
		try {
			return Long.parseLong(s);
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("O parametro '" + key + "' deve ser um numero inteiro.");
		}
	}

	private static Long requiredLongArg(Map<String, Object> args, String key) {
		Long value = longArg(args, key);
		if (value == null) {
			throw new IllegalArgumentException("O parametro '" + key + "' e obrigatorio.");
		}
		return value;
	}

	private static Integer intArg(Map<String, Object> args, String key) {
		Long value = longArg(args, key);
		return value == null ? null : value.intValue();
	}

	private static int requiredIntArg(Map<String, Object> args, String key) {
		Long value = longArg(args, key);
		if (value == null) {
			throw new IllegalArgumentException("O parametro '" + key + "' e obrigatorio.");
		}
		return value.intValue();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asStringObjectMap(Object raw) {
		return (Map<String, Object>) raw;
	}

	private static List<ExerciseInput> parseExercises(Object raw) {
		if (!(raw instanceof List<?> list)) {
			return List.of();
		}
		List<ExerciseInput> result = new ArrayList<>();
		int index = 0;
		for (Object item : list) {
			index++;
			if (!(item instanceof Map<?, ?>)) {
				throw new IllegalArgumentException("O exercicio #" + index + " em 'exercises' deve ser um objeto.");
			}
			Map<String, Object> m = asStringObjectMap(item);
			String name = requiredStringArg(m, "name");
			String exerciseType = requiredStringArg(m, "exerciseType");
			String kind = requiredStringArg(m, "kind");
			int orderIndex = requiredIntArg(m, "orderIndex");
			result.add(new ExerciseInput(name, exerciseType, kind, stringArg(m, "howToExecute"), m.get("pattern"),
					intArg(m, "targetBpm"), intArg(m, "targetDurationSeconds"), stringArg(m, "videoSourceType"),
					stringArg(m, "videoUrl"), stringArg(m, "videoFilePath"), orderIndex));
		}
		return result;
	}

	private static List<ExecutionLogInput> parseLogs(Object raw) {
		if (!(raw instanceof List<?> list)) {
			return List.of();
		}
		List<ExecutionLogInput> result = new ArrayList<>();
		int index = 0;
		for (Object item : list) {
			index++;
			if (!(item instanceof Map<?, ?>)) {
				throw new IllegalArgumentException("O log #" + index + " em 'logs' deve ser um objeto.");
			}
			Map<String, Object> m = asStringObjectMap(item);
			Long exerciseId = requiredLongArg(m, "exerciseId");
			result.add(new ExecutionLogInput(exerciseId, intArg(m, "achievedBpm"),
					intArg(m, "actualDurationSeconds"), stringArg(m, "notes")));
		}
		return result;
	}

	private static List<RepertoireLinkInput> parseLinks(Object raw) {
		if (!(raw instanceof List<?> list)) {
			return List.of();
		}
		List<RepertoireLinkInput> result = new ArrayList<>();
		int index = 0;
		for (Object item : list) {
			index++;
			if (!(item instanceof Map<?, ?>)) {
				throw new IllegalArgumentException("O link #" + index + " deve ser um objeto.");
			}
			Map<String, Object> m = asStringObjectMap(item);
			String url = requiredStringArg(m, "url");
			result.add(new RepertoireLinkInput(url, stringArg(m, "label")));
		}
		return result;
	}

	// ===================== JSON pretty-print =====================

	/**
	 * Serializador JSON compacto e indentado para as estruturas {@code Map}/{@code List}/
	 * escalares que chegam das tool calls (e para os presets de pattern). Evita depender de
	 * um pretty-printer especifico do mapper Jackson que o SDK expoe so via interface.
	 */
	private static String prettyJson(Object value) {
		StringBuilder sb = new StringBuilder();
		writeJson(sb, value, 0);
		return sb.toString();
	}

	private static void writeJson(StringBuilder sb, Object value, int indent) {
		switch (value) {
			case null -> sb.append("null");
			case Map<?, ?> map -> {
				if (map.isEmpty()) {
					sb.append("{}");
					break;
				}
				sb.append("{\n");
				int i = 0;
				for (Map.Entry<?, ?> entry : map.entrySet()) {
					indent(sb, indent + 1);
					sb.append('"').append(escapeJson(String.valueOf(entry.getKey()))).append("\": ");
					writeJson(sb, entry.getValue(), indent + 1);
					if (++i < map.size()) {
						sb.append(',');
					}
					sb.append('\n');
				}
				indent(sb, indent);
				sb.append('}');
			}
			case List<?> list -> {
				if (list.isEmpty()) {
					sb.append("[]");
					break;
				}
				boolean scalarOnly = list.stream()
					.allMatch(x -> x == null || x instanceof Number || x instanceof Boolean || x instanceof String);
				if (scalarOnly) {
					sb.append('[');
					for (int j = 0; j < list.size(); j++) {
						if (j > 0) {
							sb.append(", ");
						}
						writeJson(sb, list.get(j), indent + 1);
					}
					sb.append(']');
				}
				else {
					sb.append("[\n");
					for (int j = 0; j < list.size(); j++) {
						indent(sb, indent + 1);
						writeJson(sb, list.get(j), indent + 1);
						if (j + 1 < list.size()) {
							sb.append(',');
						}
						sb.append('\n');
					}
					indent(sb, indent);
					sb.append(']');
				}
			}
			case String s -> sb.append('"').append(escapeJson(s)).append('"');
			case Number n -> sb.append(n.toString());
			case Boolean b -> sb.append(b.toString());
			default -> sb.append('"').append(escapeJson(value.toString())).append('"');
		}
	}

	private static void indent(StringBuilder sb, int level) {
		sb.append("  ".repeat(level));
	}

	private static String escapeJson(String s) {
		StringBuilder out = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					}
					else {
						out.append(c);
					}
				}
			}
		}
		return out.toString();
	}

	// ===================== Erros =====================

	private static String friendlyMessage(Exception e, String backendUrl) {
		if (e instanceof BackendException) {
			return e.getMessage();
		}
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConnectException) {
				return "nao foi possivel conectar ao back em " + backendUrl + " - confirme que ele esta rodando "
						+ "(ver README.md da raiz do projeto).";
			}
		}
		return e.getMessage() != null ? e.getMessage() : e.toString();
	}

	private static CallToolResult okResult(String text) {
		return CallToolResult.builder().addTextContent(text).isError(false).build();
	}

	private static CallToolResult errorResult(String text) {
		return CallToolResult.builder().addTextContent(text).isError(true).build();
	}
}
