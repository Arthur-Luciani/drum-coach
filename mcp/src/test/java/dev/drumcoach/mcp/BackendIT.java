package dev.drumcoach.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Teste de integracao end-to-end contra um {@code back} DE VERDADE, rodando fora deste
 * processo. NAO roda automaticamente em {@code mvn test}/{@code mvn clean package} - o
 * nome da classe (termina em {@code IT}, nao em {@code Test}) fica fora dos padroes
 * default do Surefire de proposito, exatamente para isso.
 *
 * <p>
 * Como rodar manualmente:
 *
 * <pre>
 * cd back
 * mvn clean install
 * "&lt;caminho do JDK 25&gt;/bin/java" -Dserver.port=8099 -Ddrumcoach.data.db-path=&lt;arquivo temporario&gt; ^
 *     -cp "target\drum-coach.jar;target\lib\*" dev.drumcoach.presentation.web.WebApplication
 * # em outro terminal, dentro de mcp/:
 * mvn test "-Dtest=BackendIT" "-Dbackend.url=http://localhost:8099"
 * </pre>
 *
 * Aponta para {@code http://localhost:8080} por padrao; sobrescreva com
 * {@code -Dbackend.url=http://localhost:PORTA} se o back de teste estiver em outra
 * porta (fortemente recomendado - NUNCA rode este teste contra a porta 8080 se ja houver
 * um back de producao la, ver README da raiz do projeto).
 *
 * <p>
 * Fase 3 (ver ADR-0009): Treino pertence direto a Meta (sem Plano) - os fluxos aqui
 * exercitam {@code GET /api/goals/{id}} (treinos + progresso), {@code add_training_to_goal}
 * com e sem {@code goalId} (avulso), {@code focus_goal} (desfoco atomico de outra meta) e
 * {@code actualDurationSeconds} nos logs de {@code record_execution}.
 */
class BackendIT {

	private static final String BACKEND_URL = System.getProperty("backend.url", "http://localhost:8080");

	private final McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();

	private McpSyncClient client;

	@AfterEach
	void tearDown() {
		if (client != null) {
			client.closeGracefully();
		}
	}

	private McpSyncClient startClient() {
		String javaClassPath = System.getProperty("java.class.path");
		String javaBin = System.getProperty("java.home") + "/bin/java";

		ServerParameters params = ServerParameters.builder(javaBin)
			.args("-cp", javaClassPath, "dev.drumcoach.mcp.McpProxyApplication")
			.addEnvVar("DRUM_COACH_BACKEND_URL", BACKEND_URL)
			.build();

		StdioClientTransport transport = new StdioClientTransport(params, McpJsonDefaults.getMapper());
		McpSyncClient client = McpClient.sync(transport)
			.clientInfo(new Implementation("drum-coach-mcp-it-client", "1.0.0"))
			.requestTimeout(Duration.ofSeconds(20))
			.initializationTimeout(Duration.ofSeconds(20))
			.build();
		this.client = client;
		client.initialize();
		return client;
	}

	@Test
	void healthCheckReportsBackendUp() {
		McpSyncClient client = startClient();

		CallToolResult result = client.callTool(CallToolRequest.builder("health_check").build());

		assertFalse(Boolean.TRUE.equals(result.isError()));
		assertTrue(firstText(result).contains("esta no ar"), "esperava confirmacao de que o back esta no ar, veio: " + firstText(result));
	}

	@Test
	void createGoalThenListGoals_createsWithClaudeAuditAndAppearsInList() throws IOException, InterruptedException {
		McpSyncClient client = startClient();

		String uniqueTitle = "IT smoke test - " + UUID.randomUUID();

		CallToolResult createResult = client.callTool(CallToolRequest.builder("create_goal")
			.arguments(Map.of("title", uniqueTitle, "description", "Criado pelo teste de integracao BackendIT",
					"targetDate", "2026-12-31", "targetMetric", "1x"))
			.build());

		assertFalse(Boolean.TRUE.equals(createResult.isError()), "create_goal falhou: " + firstText(createResult));
		String createText = firstText(createResult);
		assertTrue(createText.contains(uniqueTitle), createText);
		assertTrue(createText.contains("createdBy=CLAUDE"), "resposta da tool nao confirmou createdBy=CLAUDE: " + createText);

		CallToolResult listResult = client.callTool(CallToolRequest.builder("list_goals").build());
		assertFalse(Boolean.TRUE.equals(listResult.isError()));
		String listText = firstText(listResult);
		assertTrue(listText.contains(uniqueTitle), "meta criada nao apareceu em list_goals: " + listText);

		// Confirmacao independente: bate direto em GET /api/goals (sem passar pelo
		// proxy MCP) e confere que o registro persistido tem createdBy=CLAUDE.
		Map<String, Object> found = findByTitle("/api/goals", "title", uniqueTitle);
		assertNotNull(found, "GET /api/goals (direto, sem o proxy MCP) nao retornou a meta criada: " + uniqueTitle);
		assertEquals("CLAUDE", found.get("createdBy"), "createdBy deveria ser CLAUDE - registro: " + found);
	}

	@Test
	void updateGoalProgress_changesStatusAndDescription() throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		String uniqueTitle = "IT update-goal - " + UUID.randomUUID();

		CallToolResult createResult = client.callTool(CallToolRequest.builder("create_goal")
			.arguments(Map.of("title", uniqueTitle))
			.build());
		long goalId = extractId(firstText(createResult), "id ");

		CallToolResult updateResult = client.callTool(CallToolRequest.builder("update_goal_progress")
			.arguments(Map.of("id", goalId, "status", "IN_PROGRESS", "notes", "Progredindo bem"))
			.build());

		assertFalse(Boolean.TRUE.equals(updateResult.isError()), "update_goal_progress falhou: " + firstText(updateResult));
		assertTrue(firstText(updateResult).contains("IN_PROGRESS"), firstText(updateResult));

		Map<String, Object> found = findByTitle("/api/goals", "title", uniqueTitle);
		assertNotNull(found);
		assertEquals("IN_PROGRESS", found.get("status"));
		assertEquals("Progredindo bem", found.get("description"));
		assertEquals("CLAUDE", found.get("lastModifiedBy"));
	}

	@Test
	void getGoal_returnsTrainingsWithProgress() throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		String uniqueTitle = "IT get-goal-progress - " + UUID.randomUUID();

		CallToolResult goalResult = client.callTool(CallToolRequest.builder("create_goal")
			.arguments(Map.of("title", uniqueTitle))
			.build());
		long goalId = extractId(firstText(goalResult), "id ");

		CallToolResult trainingResult = client.callTool(CallToolRequest.builder("add_training_to_goal")
			.arguments(Map.of("goalId", goalId, "training",
					Map.of("name", "Treino com progresso", "targetDurationMinutes", 20, "targetRepetitions", 2,
							"orderIndex", 0)))
			.build());
		assertFalse(Boolean.TRUE.equals(trainingResult.isError()), "add_training_to_goal falhou: " + firstText(trainingResult));
		long trainingId = extractId(firstText(trainingResult), "Treino criado (id ");

		// Antes de qualquer execucao: get_goal mostra o treino com 0 execucoes.
		CallToolResult goalBefore = client.callTool(CallToolRequest.builder("get_goal").arguments(Map.of("id", goalId)).build());
		assertFalse(Boolean.TRUE.equals(goalBefore.isError()), firstText(goalBefore));
		String textBefore = firstText(goalBefore);
		assertTrue(textBefore.contains("Treino com progresso"), textBefore);
		assertTrue(textBefore.contains(": 0/2 execucao"), "esperava progresso 0/2 antes de qualquer execucao: " + textBefore);

		CallToolResult execResult = client.callTool(CallToolRequest.builder("record_execution")
			.arguments(Map.of("trainingId", trainingId, "executionDate", "2026-08-20"))
			.build());
		assertFalse(Boolean.TRUE.equals(execResult.isError()), firstText(execResult));

		CallToolResult goalAfter = client.callTool(CallToolRequest.builder("get_goal").arguments(Map.of("id", goalId)).build());
		assertFalse(Boolean.TRUE.equals(goalAfter.isError()), firstText(goalAfter));
		String textAfter = firstText(goalAfter);
		assertTrue(textAfter.contains(": 1/2 execucao"), "esperava progresso 1/2 depois de 1 execucao: " + textAfter);

		// Confirmacao independente: bate direto em GET /api/goals/{id}.
		Map<String, Object> detail = getJson("/api/goals/" + goalId);
		List<?> trainings = (List<?>) detail.get("trainings");
		assertEquals(1, trainings.size());
		Map<?, ?> progress = (Map<?, ?>) trainings.get(0);
		assertEquals(1L, ((Number) progress.get("completedCount")).longValue());
		assertEquals(2, ((Number) progress.get("targetRepetitions")).intValue());
	}

	@Test
	void addTrainingToGoal_withGoalId_belongsToGoal() throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		String uniqueTitle = "IT training-with-goal - " + UUID.randomUUID();

		CallToolResult goalResult = client.callTool(CallToolRequest.builder("create_goal")
			.arguments(Map.of("title", uniqueTitle))
			.build());
		long goalId = extractId(firstText(goalResult), "id ");

		CallToolResult result = client.callTool(CallToolRequest.builder("add_training_to_goal")
			.arguments(Map.of("goalId", goalId, "training",
					Map.of("name", "Treino da meta", "targetDurationMinutes", 15, "orderIndex", 0)))
			.build());

		assertFalse(Boolean.TRUE.equals(result.isError()), "add_training_to_goal falhou: " + firstText(result));
		assertTrue(firstText(result).contains("na meta " + goalId), firstText(result));
		long trainingId = extractId(firstText(result), "Treino criado (id ");

		Map<String, Object> training = findById("/api/trainings?goalId=" + goalId, trainingId);
		assertNotNull(training, "treino nao apareceu em GET /api/trainings?goalId=" + goalId);
		assertEquals(goalId, ((Number) training.get("goalId")).longValue());
	}

	@Test
	void addTrainingToGoal_withoutGoalId_createsStandaloneTraining() throws IOException, InterruptedException {
		McpSyncClient client = startClient();

		CallToolResult result = client.callTool(CallToolRequest.builder("add_training_to_goal")
			.arguments(Map.of("training",
					Map.of("name", "Treino avulso - " + UUID.randomUUID(), "targetDurationMinutes", 10, "orderIndex", 0)))
			.build());

		assertFalse(Boolean.TRUE.equals(result.isError()), "add_training_to_goal falhou: " + firstText(result));
		assertTrue(firstText(result).contains("avulso"), firstText(result));
		long trainingId = extractId(firstText(result), "Treino criado (id ");

		HttpClient http = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder(URI.create(BACKEND_URL + "/api/trainings")).GET().build();
		HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
		assertEquals(200, response.statusCode());
		List<Map<String, Object>> all = jsonMapper.readValue(response.body(), new TypeRef<List<Map<String, Object>>>() {
		});
		Map<String, Object> training = all.stream()
			.filter(t -> trainingId == ((Number) t.get("id")).longValue())
			.findFirst()
			.orElse(null);
		assertNotNull(training, "treino avulso nao encontrado em GET /api/trainings");
		assertNull(training.get("goalId"), "treino avulso deveria ter goalId nulo: " + training);
	}

	@Test
	void focusGoal_defocusesPreviouslyFocusedGoal() throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		String titleA = "IT focus-a - " + UUID.randomUUID();
		String titleB = "IT focus-b - " + UUID.randomUUID();

		long goalAId = extractId(firstText(client.callTool(
				CallToolRequest.builder("create_goal").arguments(Map.of("title", titleA)).build())), "id ");
		long goalBId = extractId(firstText(client.callTool(
				CallToolRequest.builder("create_goal").arguments(Map.of("title", titleB)).build())), "id ");

		CallToolResult focusAResult = client.callTool(CallToolRequest.builder("focus_goal")
			.arguments(Map.of("goalId", goalAId))
			.build());
		assertFalse(Boolean.TRUE.equals(focusAResult.isError()), firstText(focusAResult));

		@SuppressWarnings("unchecked")
		Map<String, Object> goalAAfterFirstFocus = (Map<String, Object>) getJson("/api/goals/" + goalAId).get("goal");
		assertEquals(Boolean.TRUE, goalAAfterFirstFocus.get("inFocus"), "meta A deveria estar em foco: " + goalAAfterFirstFocus);

		CallToolResult focusBResult = client.callTool(CallToolRequest.builder("focus_goal")
			.arguments(Map.of("goalId", goalBId))
			.build());
		assertFalse(Boolean.TRUE.equals(focusBResult.isError()), firstText(focusBResult));

		@SuppressWarnings("unchecked")
		Map<String, Object> goalAAfter = (Map<String, Object>) getJson("/api/goals/" + goalAId).get("goal");
		@SuppressWarnings("unchecked")
		Map<String, Object> goalBAfter = (Map<String, Object>) getJson("/api/goals/" + goalBId).get("goal");
		assertEquals(Boolean.FALSE, goalAAfter.get("inFocus"), "meta A deveria ter sido desfocada: " + goalAAfter);
		assertEquals(Boolean.TRUE, goalBAfter.get("inFocus"), "meta B deveria estar em foco: " + goalBAfter);
	}

	@Test
	void recordExecution_withActualDurationSecondsInLogs_persistsIt() throws IOException, InterruptedException {
		McpSyncClient client = startClient();

		CallToolResult trainingResult = client.callTool(CallToolRequest.builder("add_training_to_goal")
			.arguments(Map.of("training",
					Map.of("name", "Treino p/ log de duracao - " + UUID.randomUUID(), "targetDurationMinutes", 15,
							"orderIndex", 0, "exercises",
							List.of(Map.of("name", "Paradiddle", "exerciseType", "rudimento", "kind", "TOCA_JUNTO",
									"howToExecute", "RLRR LRLL", "targetBpm", 100, "targetDurationSeconds", 60,
									"orderIndex", 0)))))
			.build());
		assertFalse(Boolean.TRUE.equals(trainingResult.isError()), firstText(trainingResult));
		long trainingId = extractId(firstText(trainingResult), "Treino criado (id ");

		List<Map<String, Object>> exercises = getJsonList("/api/trainings/" + trainingId + "/exercises");
		assertEquals(1, exercises.size());
		long exerciseId = ((Number) exercises.get(0).get("id")).longValue();
		assertEquals(60, ((Number) exercises.get(0).get("targetDurationSeconds")).intValue());

		CallToolResult execResult = client.callTool(CallToolRequest.builder("record_execution")
			.arguments(Map.of("trainingId", trainingId, "executionDate", "2026-08-21", "logs",
					List.of(Map.of("exerciseId", exerciseId, "achievedBpm", 95, "actualDurationSeconds", 58, "notes",
							"Boa consistencia"))))
			.build());
		assertFalse(Boolean.TRUE.equals(execResult.isError()), "record_execution falhou: " + firstText(execResult));
		long executionId = extractId(firstText(execResult), "Execucao registrada (id ");

		List<Map<String, Object>> executions = getJsonList("/api/executions?trainingId=" + trainingId);
		Map<String, Object> found = executions.stream()
			.filter(e -> executionId == ((Number) e.get("id")).longValue())
			.findFirst()
			.orElse(null);
		assertNotNull(found, "execucao nao encontrada em GET /api/executions?trainingId=" + trainingId);
		List<?> logs = (List<?>) found.get("logs");
		assertEquals(1, logs.size());
		Map<?, ?> log = (Map<?, ?>) logs.get(0);
		assertEquals(58, ((Number) log.get("actualDurationSeconds")).intValue(),
				"actualDurationSeconds nao foi persistido corretamente: " + log);
		assertEquals(95, ((Number) log.get("achievedBpm")).intValue());
	}

	@Test
	void generateTrainingFromLesson_linksGeneratedTrainingIdOnLesson() throws IOException, InterruptedException {
		McpSyncClient client = startClient();

		CallToolResult lessonResult = client.callTool(CallToolRequest.builder("record_lesson")
			.arguments(Map.of("lessonDate", "2026-08-15", "teacherNotes", "Trabalhar coordenacao"))
			.build());
		long lessonId = extractId(firstText(lessonResult), "id ");

		String trainingName = "IT training-from-lesson - " + UUID.randomUUID();
		CallToolResult genResult = client.callTool(CallToolRequest.builder("generate_training_from_lesson")
			.arguments(Map.of("lessonId", lessonId, "training",
					Map.of("name", trainingName, "targetDurationMinutes", 30, "orderIndex", 0)))
			.build());

		assertFalse(Boolean.TRUE.equals(genResult.isError()), "generate_training_from_lesson falhou: " + firstText(genResult));
		assertTrue(firstText(genResult).contains(trainingName), firstText(genResult));

		Map<String, Object> lesson = getJson("/api/lessons/" + lessonId);
		assertNotNull(lesson.get("generatedTrainingId"), "lesson.generatedTrainingId nao foi vinculado: " + lesson);
	}

	@Test
	void getCoachBriefingReturnsCoherentSummary() {
		McpSyncClient client = startClient();

		client.callTool(CallToolRequest.builder("create_goal")
			.arguments(Map.of("title", "IT briefing goal - " + UUID.randomUUID()))
			.build());

		CallToolResult result = client.callTool(CallToolRequest.builder("get_coach_briefing").build());

		assertFalse(Boolean.TRUE.equals(result.isError()), "get_coach_briefing falhou: " + firstText(result));
		String text = firstText(result);
		assertTrue(text.contains("BRIEFING DO COACH"), text);
		assertTrue(text.contains("Metas em foco/andamento"), text);
		assertTrue(text.contains("Aulas recentes"), text);
		assertTrue(text.contains("Execucoes recentes"), text);
		assertFalse(text.contains("Planos ativos"), "briefing nao deveria mais mencionar planos: " + text);
	}

	@Test
	void addRepertoireItemThenUpdate_reflectsStatusChange() {
		McpSyncClient client = startClient();
		String uniqueTitle = "IT repertoire - " + UUID.randomUUID();

		CallToolResult addResult = client.callTool(CallToolRequest.builder("add_repertoire_item")
			.arguments(Map.of("songTitle", uniqueTitle, "artist", "Test Band", "targetBpm", 140))
			.build());
		assertFalse(Boolean.TRUE.equals(addResult.isError()), firstText(addResult));
		long itemId = extractId(firstText(addResult), "id ");

		CallToolResult updateResult = client.callTool(CallToolRequest.builder("update_repertoire_item")
			.arguments(Map.of("id", itemId, "status", "LEARNING", "currentBpm", 90))
			.build());
		assertFalse(Boolean.TRUE.equals(updateResult.isError()), firstText(updateResult));
		assertTrue(firstText(updateResult).contains("LEARNING"), firstText(updateResult));
	}

	// ===================== Fase 4b: kind / pattern / trechos (ADR-0011) =====================

	@Test
	void addTocaJuntoExerciseWithPresetPattern_thenGetExerciseReturnsPattern()
			throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		long trainingId = createStandaloneTraining(client, "IT toca-junto - " + UUID.randomUUID());

		CallToolResult addResult = client.callTool(CallToolRequest.builder("add_exercise_to_training")
			.arguments(Map.of("trainingId", trainingId, "exercise",
					Map.of("name", "Groove reto", "exerciseType", "leitura", "kind", "TOCA_JUNTO", "orderIndex", 0,
							"targetBpm", 90, "pattern", groovePattern(List.of(0, 8)))))
			.build());
		assertFalse(Boolean.TRUE.equals(addResult.isError()), "add_exercise_to_training falhou: " + firstText(addResult));
		assertTrue(firstText(addResult).contains("kind TOCA_JUNTO"), firstText(addResult));
		long exerciseId = extractId(firstText(addResult), "Exercicio criado (id ");

		CallToolResult getResult = client.callTool(
				CallToolRequest.builder("get_exercise").arguments(Map.of("exerciseId", exerciseId)).build());
		assertFalse(Boolean.TRUE.equals(getResult.isError()), firstText(getResult));
		String text = firstText(getResult);
		assertTrue(text.contains("kind: TOCA_JUNTO"), text);
		assertTrue(text.contains("pattern (JSON):"), text);
		assertTrue(text.contains("\"stepsPerBeat\"") && text.contains("\"kick\""), text);

		// Confirmacao independente: GET /api/exercises/{id} traz o pattern como objeto.
		Map<String, Object> exercise = getJson("/api/exercises/" + exerciseId);
		assertEquals("TOCA_JUNTO", exercise.get("kind"));
		assertNotNull(exercise.get("pattern"), "pattern deveria ter sido persistido: " + exercise);
	}

	@Test
	void updateExercisePattern_withEditedPattern_persistsIt() throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		long trainingId = createStandaloneTraining(client, "IT update-pattern - " + UUID.randomUUID());
		long exerciseId = addTocaJuntoExercise(client, trainingId, groovePattern(List.of(0, 8)));

		CallToolResult updateResult = client.callTool(CallToolRequest.builder("update_exercise")
			.arguments(Map.of("exerciseId", exerciseId, "pattern", groovePattern(List.of(0, 4, 8, 12)),
					"howToExecute", "Bumbo em toda semínima"))
			.build());
		assertFalse(Boolean.TRUE.equals(updateResult.isError()), "update_exercise falhou: " + firstText(updateResult));

		CallToolResult getResult = client.callTool(
				CallToolRequest.builder("get_exercise").arguments(Map.of("exerciseId", exerciseId)).build());
		String text = firstText(getResult);
		assertTrue(text.contains("0, 4, 8, 12"), "pattern editado nao apareceu no get_exercise: " + text);
		assertTrue(text.contains("Bumbo em toda"), "howToExecute nao foi atualizado: " + text);

		@SuppressWarnings("unchecked")
		Map<String, Object> pattern = (Map<String, Object>) getJson("/api/exercises/" + exerciseId).get("pattern");
		@SuppressWarnings("unchecked")
		Map<String, Object> hits = (Map<String, Object>) pattern.get("hits");
		assertEquals(List.of(0, 4, 8, 12), hits.get("kick"), "kick nao foi atualizado no back: " + hits);
	}

	@Test
	void updateExercisePattern_withInvalidPattern_returns400Propagated() throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		long trainingId = createStandaloneTraining(client, "IT invalid-pattern - " + UUID.randomUUID());
		long exerciseId = addTocaJuntoExercise(client, trainingId, groovePattern(List.of(0, 8)));

		Map<String, Object> broken = groovePattern(List.of(0, 8));
		@SuppressWarnings("unchecked")
		Map<String, Object> hits = (Map<String, Object>) broken.get("hits");
		hits.put("hihat", List.of(99)); // step fora de [0, 16)

		CallToolResult result = client.callTool(CallToolRequest.builder("update_exercise")
			.arguments(Map.of("exerciseId", exerciseId, "pattern", broken))
			.build());

		assertTrue(Boolean.TRUE.equals(result.isError()), "esperava erro propagado do back (400): " + firstText(result));
		assertTrue(firstText(result).toLowerCase().contains("pattern"),
				"a mensagem de erro do back deveria mencionar o pattern: " + firstText(result));
	}

	@Test
	void addMarkedPassage_thenGetExerciseShowsThePassage() throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		long trainingId = createStandaloneTraining(client, "IT passages - " + UUID.randomUUID());

		CallToolResult addResult = client.callTool(CallToolRequest.builder("add_exercise_to_training")
			.arguments(Map.of("trainingId", trainingId, "exercise", Map.of("name", "Transcrever refrao", "exerciseType",
					"transcricao", "kind", "TRANSCRICAO", "orderIndex", 0)))
			.build());
		assertFalse(Boolean.TRUE.equals(addResult.isError()), firstText(addResult));
		long exerciseId = extractId(firstText(addResult), "Exercicio criado (id ");

		CallToolResult passageResult = client.callTool(CallToolRequest.builder("add_marked_passage")
			.arguments(Map.of("exerciseId", exerciseId, "fromSeconds", 72, "toSeconds", 84, "label", "virada dificil"))
			.build());
		assertFalse(Boolean.TRUE.equals(passageResult.isError()), "add_marked_passage falhou: " + firstText(passageResult));
		assertTrue(firstText(passageResult).contains("72s") && firstText(passageResult).contains("84s"), firstText(passageResult));

		CallToolResult getResult = client.callTool(
				CallToolRequest.builder("get_exercise").arguments(Map.of("exerciseId", exerciseId)).build());
		String text = firstText(getResult);
		assertTrue(text.contains("Trechos marcados (1)"), text);
		assertTrue(text.contains("virada dificil"), text);

		Map<String, Object> exercise = getJson("/api/exercises/" + exerciseId);
		List<?> passages = (List<?>) exercise.get("passages");
		assertEquals(1, passages.size(), "trecho nao foi persistido: " + exercise);
	}

	@Test
	void listPatternPresets_returnsTheThreeBuiltInsThatPassBackValidation() throws IOException, InterruptedException {
		McpSyncClient client = startClient();

		CallToolResult result = client.callTool(CallToolRequest.builder("list_pattern_presets").build());
		assertFalse(Boolean.TRUE.equals(result.isError()), firstText(result));
		String text = firstText(result);
		assertTrue(text.contains("groove-4-4") && text.contains("paradiddle") && text.contains("shuffle"), text);

		// Cada preset embutido deve passar na validacao do DrumPattern do back: cria um
		// exercicio TOCA_JUNTO com cada um deles.
		long trainingId = createStandaloneTraining(client, "IT presets-valid - " + UUID.randomUUID());
		for (String preset : List.of("groove-4-4", "paradiddle", "shuffle")) {
			Map<String, Object> pattern = presetPattern(preset);
			CallToolResult addResult = client.callTool(CallToolRequest.builder("add_exercise_to_training")
				.arguments(Map.of("trainingId", trainingId, "exercise",
						Map.of("name", "preset " + preset, "exerciseType", "preset", "kind", "TOCA_JUNTO", "orderIndex",
								0, "pattern", pattern)))
				.build());
			assertFalse(Boolean.TRUE.equals(addResult.isError()),
					"preset '" + preset + "' nao passou na validacao do back: " + firstText(addResult));
		}
	}

	// ===================== Fase 5: update/delete de treino e exercicio, status no repertorio =====================

	@Test
	void updateTraining_changesASingleField() throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		long trainingId = createStandaloneTraining(client, "IT update-training - " + UUID.randomUUID());

		CallToolResult result = client.callTool(CallToolRequest.builder("update_training")
			.arguments(Map.of("trainingId", trainingId, "targetRepetitions", 7))
			.build());
		assertFalse(Boolean.TRUE.equals(result.isError()), "update_training falhou: " + firstText(result));

		Map<String, Object> training = findById("/api/trainings", trainingId);
		assertNotNull(training);
		assertEquals(7, ((Number) training.get("targetRepetitions")).intValue());
		assertEquals("CLAUDE", training.get("lastModifiedBy"));
	}

	@Test
	void deleteTraining_ofANewEmptyTraining_removesIt() throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		long trainingId = createStandaloneTraining(client, "IT delete-training - " + UUID.randomUUID());

		CallToolResult result = client.callTool(
				CallToolRequest.builder("delete_training").arguments(Map.of("trainingId", trainingId)).build());
		assertFalse(Boolean.TRUE.equals(result.isError()), "delete_training falhou: " + firstText(result));

		assertNull(findById("/api/trainings", trainingId), "treino deveria ter sido removido");
	}

	@Test
	void updateExercise_changesName() throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		long trainingId = createStandaloneTraining(client, "IT update-exercise-name - " + UUID.randomUUID());
		long exerciseId = addTocaJuntoExercise(client, trainingId, groovePattern(List.of(0, 8)));

		CallToolResult result = client.callTool(CallToolRequest.builder("update_exercise")
			.arguments(Map.of("exerciseId", exerciseId, "name", "Groove renomeado", "targetBpm", 105))
			.build());
		assertFalse(Boolean.TRUE.equals(result.isError()), "update_exercise falhou: " + firstText(result));

		Map<String, Object> exercise = getJson("/api/exercises/" + exerciseId);
		assertEquals("Groove renomeado", exercise.get("name"));
		assertEquals(105, ((Number) exercise.get("targetBpm")).intValue());
		assertEquals("TOCA_JUNTO", exercise.get("kind"));
	}

	@Test
	void deleteExercise_removesIt() throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		long trainingId = createStandaloneTraining(client, "IT delete-exercise - " + UUID.randomUUID());
		long exerciseId = addTocaJuntoExercise(client, trainingId, groovePattern(List.of(0, 8)));

		CallToolResult result = client.callTool(
				CallToolRequest.builder("delete_exercise").arguments(Map.of("exerciseId", exerciseId)).build());
		assertFalse(Boolean.TRUE.equals(result.isError()), "delete_exercise falhou: " + firstText(result));

		List<Map<String, Object>> exercises = getJsonList("/api/trainings/" + trainingId + "/exercises");
		assertTrue(exercises.stream().noneMatch(e -> exerciseId == ((Number) e.get("id")).longValue()),
				"exercicio deveria ter sido removido: " + exercises);
	}

	@Test
	void addRepertoireItem_withStatus_createsAlreadyInThatStatus() throws IOException, InterruptedException {
		McpSyncClient client = startClient();
		String uniqueTitle = "IT repertoire-status - " + UUID.randomUUID();

		CallToolResult addResult = client.callTool(CallToolRequest.builder("add_repertoire_item")
			.arguments(Map.of("songTitle", uniqueTitle, "artist", "Test Band", "status", "LEARNING"))
			.build());
		assertFalse(Boolean.TRUE.equals(addResult.isError()), firstText(addResult));
		assertTrue(firstText(addResult).contains("LEARNING"), "resposta deveria confirmar status LEARNING: " + firstText(addResult));

		Map<String, Object> found = findByTitle("/api/repertoire-items", "songTitle", uniqueTitle);
		assertNotNull(found);
		assertEquals("LEARNING", found.get("status"));
	}

	// ===================== Helpers =====================

	private long createStandaloneTraining(McpSyncClient client, String name) {
		CallToolResult result = client.callTool(CallToolRequest.builder("add_training_to_goal")
			.arguments(Map.of("training", Map.of("name", name, "targetDurationMinutes", 10, "orderIndex", 0)))
			.build());
		assertFalse(Boolean.TRUE.equals(result.isError()), "add_training_to_goal falhou: " + firstText(result));
		return extractId(firstText(result), "Treino criado (id ");
	}

	private long addTocaJuntoExercise(McpSyncClient client, long trainingId, Map<String, Object> pattern) {
		CallToolResult addResult = client.callTool(CallToolRequest.builder("add_exercise_to_training")
			.arguments(Map.of("trainingId", trainingId, "exercise", Map.of("name", "Groove", "exerciseType", "groove",
					"kind", "TOCA_JUNTO", "orderIndex", 0, "pattern", pattern)))
			.build());
		assertFalse(Boolean.TRUE.equals(addResult.isError()), "add_exercise_to_training falhou: " + firstText(addResult));
		return extractId(firstText(addResult), "Exercicio criado (id ");
	}

	/** Groove reto de 1 compasso (total 16 steps), variando so os hits do bumbo. */
	private static Map<String, Object> groovePattern(List<Integer> kickHits) {
		Map<String, Object> hits = new java.util.LinkedHashMap<>();
		hits.put("hihat", List.of(0, 2, 4, 6, 8, 10, 12, 14));
		hits.put("snare", List.of(4, 12));
		hits.put("kick", List.copyOf(kickHits));
		Map<String, Object> pattern = new java.util.LinkedHashMap<>();
		pattern.put("version", 1);
		pattern.put("timeSignature", List.of(4, 4));
		pattern.put("stepsPerBeat", 4);
		pattern.put("tuplet", false);
		pattern.put("bars", 1);
		pattern.put("voices", List.of("hihat", "snare", "kick"));
		pattern.put("hits", hits);
		return pattern;
	}

	private static Map<String, Object> presetPattern(String name) {
		Map<String, Object> p = new java.util.LinkedHashMap<>();
		p.put("version", 1);
		p.put("timeSignature", List.of(4, 4));
		p.put("bars", 1);
		switch (name) {
			case "groove-4-4" -> {
				p.put("stepsPerBeat", 4);
				p.put("tuplet", false);
				p.put("voices", List.of("hihat", "snare", "kick"));
				Map<String, Object> hits = new java.util.LinkedHashMap<>();
				hits.put("hihat", List.of(0, 2, 4, 6, 8, 10, 12, 14));
				hits.put("snare", List.of(4, 12));
				hits.put("kick", List.of(0, 8));
				p.put("hits", hits);
			}
			case "paradiddle" -> {
				p.put("stepsPerBeat", 4);
				p.put("tuplet", false);
				p.put("voices", List.of("snare"));
				p.put("hits", Map.of("snare", List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)));
				p.put("accents", Map.of("snare", List.of(0, 4, 8, 12)));
				p.put("sticking", List.of("R", "L", "R", "R", "L", "R", "L", "L", "R", "L", "R", "R", "L", "R", "L", "L"));
			}
			case "shuffle" -> {
				p.put("stepsPerBeat", 3);
				p.put("tuplet", true);
				p.put("voices", List.of("hihat", "snare", "kick"));
				Map<String, Object> hits = new java.util.LinkedHashMap<>();
				hits.put("hihat", List.of(0, 2, 3, 5, 6, 8, 9, 11));
				hits.put("snare", List.of(3, 9));
				hits.put("kick", List.of(0, 6));
				p.put("hits", hits);
			}
			default -> throw new IllegalArgumentException("preset desconhecido: " + name);
		}
		return p;
	}

	private long extractId(String text, String marker) {
		int idx = text.indexOf(marker);
		assertTrue(idx >= 0, "marcador '" + marker + "' nao encontrado em: " + text);
		int start = idx + marker.length();
		int end = start;
		while (end < text.length() && Character.isDigit(text.charAt(end))) {
			end++;
		}
		return Long.parseLong(text.substring(start, end));
	}

	private Map<String, Object> findByTitle(String path, String field, String value) throws IOException, InterruptedException {
		List<Map<String, Object>> items = getJsonList(path);
		return items.stream().filter(g -> value.equals(g.get(field))).findFirst().orElse(null);
	}

	private Map<String, Object> findById(String path, long id) throws IOException, InterruptedException {
		List<Map<String, Object>> items = getJsonList(path);
		return items.stream().filter(i -> id == ((Number) i.get("id")).longValue()).findFirst().orElse(null);
	}

	private List<Map<String, Object>> getJsonList(String path) throws IOException, InterruptedException {
		HttpClient http = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder(URI.create(BACKEND_URL + path)).GET().build();
		HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
		assertEquals(200, response.statusCode());
		return jsonMapper.readValue(response.body(), new TypeRef<List<Map<String, Object>>>() {
		});
	}

	private Map<String, Object> getJson(String path) throws IOException, InterruptedException {
		HttpClient http = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder(URI.create(BACKEND_URL + path)).GET().build();
		HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
		assertEquals(200, response.statusCode());
		return jsonMapper.readValue(response.body(), new TypeRef<Map<String, Object>>() {
		});
	}

	private static String firstText(CallToolResult result) {
		return result.content()
			.stream()
			.filter(TextContent.class::isInstance)
			.map(c -> ((TextContent) c).text())
			.findFirst()
			.orElse("");
	}
}
