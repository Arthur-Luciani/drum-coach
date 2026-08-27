package dev.drumcoach.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Teste de integracao ponta a ponta do transporte stdio, SEM depender do {@code back}
 * estar rodando: sobe {@link McpProxyApplication} como subprocesso real (via
 * {@code java -cp <classpath de teste> ...}, apontando para uma porta sem ninguem
 * escutando) e fala o protocolo MCP de verdade (initialize / tools/list / tools/call)
 * usando o proprio {@link McpSyncClient} do SDK, exercitando o processo exatamente como
 * o Claude Desktop faria.
 *
 * <p>
 * Os testes que precisam do {@code back} de verdade (fluxos de escrita/composicao com
 * auditoria CLAUDE) ficam em {@code BackendIT} (nao roda automaticamente em
 * {@code mvn test}/{@code mvn clean package} - ver README do modulo).
 */
class McpProxyStdioTest {

	/**
	 * As 25 tools esperadas. Fase 3 (ADR-0009): modelo Treino direto na Meta, sem Plano -
	 * nenhuma tool baseada em Plano deve aparecer aqui. Fase 4b (ADR-0011): o eixo
	 * {@code kind} do exercicio e o {@code pattern} tocavel trazem 4 tools novas
	 * ({@code get_exercise}, {@code list_pattern_presets}, {@code update_exercise_pattern},
	 * {@code add_marked_passage}).
	 */
	private static final Set<String> EXPECTED_TOOL_NAMES = Set.of(
			// leitura
			"list_goals", "get_goal", "list_trainings", "list_exercises", "get_exercise", "get_execution_history",
			"list_lessons", "get_lesson", "list_repertoire", "get_repertoire_item", "list_pattern_presets",
			"get_coach_briefing", "health_check",
			// escrita
			"create_goal", "update_goal_progress", "focus_goal", "add_training_to_goal",
			"add_exercise_to_training", "update_exercise_pattern", "add_marked_passage", "record_execution",
			"record_lesson", "generate_training_from_lesson", "add_repertoire_item", "update_repertoire_item");

	/** Tools baseadas no conceito de Plano, removido pelo ADR-0009 - nao devem mais existir. */
	private static final Set<String> REMOVED_PLAN_TOOL_NAMES = Set.of("list_active_plans", "get_plan_details",
			"create_plan", "add_training_to_plan", "generate_plan_from_lesson");

	private McpSyncClient client;

	@AfterEach
	void tearDown() {
		if (client != null) {
			client.closeGracefully();
		}
	}

	private McpSyncClient startClient(String backendUrl) {
		String javaClassPath = System.getProperty("java.class.path");
		String javaBin = System.getProperty("java.home") + "/bin/java";

		ServerParameters params = ServerParameters.builder(javaBin)
			.args("-cp", javaClassPath, "dev.drumcoach.mcp.McpProxyApplication")
			.addEnvVar("DRUM_COACH_BACKEND_URL", backendUrl)
			.build();

		StdioClientTransport transport = new StdioClientTransport(params, McpJsonDefaults.getMapper());

		McpSyncClient client = McpClient.sync(transport)
			.clientInfo(new Implementation("drum-coach-mcp-test-client", "1.0.0"))
			.requestTimeout(Duration.ofSeconds(20))
			.initializationTimeout(Duration.ofSeconds(20))
			.build();
		this.client = client;
		return client;
	}

	@Test
	void initializeAndListToolsReturnsAllExpectedTools() {
		McpSyncClient client = startClient("http://localhost:1");

		InitializeResult init = client.initialize();
		assertNotNull(init);
		assertEquals("drum-coach-mcp", init.serverInfo().name());

		ListToolsResult toolsResult = client.listTools();
		List<Tool> tools = toolsResult.tools();
		assertEquals(EXPECTED_TOOL_NAMES.size(), tools.size(),
				"esperava exatamente " + EXPECTED_TOOL_NAMES.size() + " tools registradas: " + tools);

		Set<String> names = tools.stream().map(Tool::name).collect(Collectors.toSet());
		assertEquals(EXPECTED_TOOL_NAMES, names);
	}

	@Test
	void noPlanBasedToolsAreRegistered() {
		// ADR-0009: o conceito de Plano foi removido - nenhuma tool baseada nele pode
		// sobreviver no tools/list, nem sob outro nome por engano.
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		Set<String> names = client.listTools().tools().stream().map(Tool::name).collect(Collectors.toSet());
		for (String removed : REMOVED_PLAN_TOOL_NAMES) {
			assertFalse(names.contains(removed), "tool '" + removed + "' baseada em Plano nao deveria mais existir");
		}
	}

	@Test
	void healthCheckToolReportsBackendDownWithoutCrashingTheProcess() {
		// Porta sem ninguem escutando - simula o `back` fora do ar.
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		CallToolResult result = client.callTool(CallToolRequest.builder("health_check").build());

		assertNotNull(result);
		assertFalse(Boolean.TRUE.equals(result.isError()), "health_check nao deveria reportar isError=true so por o back estar fora do ar");
		String text = result.content().stream().filter(TextContent.class::isInstance).map(c -> ((TextContent) c).text()).findFirst().orElse("");
		assertTrue(text.contains("nao respondeu") || text.toLowerCase().contains("conectar"),
				"esperava uma mensagem amigavel de falha de conexao, veio: " + text);

		// Prova de que o processo continua vivo e respondendo depois do erro tratado.
		ListToolsResult toolsResult = client.listTools();
		assertEquals(EXPECTED_TOOL_NAMES.size(), toolsResult.tools().size());
	}

	@Test
	void createGoalWithoutTitleReturnsFriendlyErrorInsteadOfCrashing() {
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		CallToolResult result = client.callTool(CallToolRequest.builder("create_goal").arguments(Map.of()).build());

		assertNotNull(result);
		assertTrue(Boolean.TRUE.equals(result.isError()), "esperava isError=true por falta de 'title'");
	}

	@Test
	void getGoalWithoutIdReturnsFriendlyErrorInsteadOfCrashing() {
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		CallToolResult result = client.callTool(CallToolRequest.builder("get_goal").arguments(Map.of()).build());

		assertNotNull(result);
		assertTrue(Boolean.TRUE.equals(result.isError()), "esperava isError=true por falta de 'id'");
	}

	@Test
	void focusGoalWithoutGoalIdReturnsFriendlyErrorInsteadOfCrashing() {
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		CallToolResult result = client.callTool(CallToolRequest.builder("focus_goal").arguments(Map.of()).build());

		assertNotNull(result);
		assertTrue(Boolean.TRUE.equals(result.isError()), "esperava isError=true por falta de 'goalId'");
	}

	@Test
	void addTrainingToGoalWithoutTrainingReturnsFriendlyErrorInsteadOfCrashing() {
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		CallToolResult result = client.callTool(
				CallToolRequest.builder("add_training_to_goal").arguments(Map.of("goalId", 1)).build());

		assertNotNull(result);
		assertTrue(Boolean.TRUE.equals(result.isError()), "esperava isError=true por falta de 'training'");
	}

	@Test
	void addTrainingToGoalWithMissingNestedFieldReturnsFriendlyErrorInsteadOfCrashing() {
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		// 'training' presente, mas sem 'targetDurationMinutes'/'orderIndex' obrigatorios;
		// 'goalId' omitido de proposito - deve ser aceito como opcional (treino avulso).
		CallToolResult result = client.callTool(CallToolRequest.builder("add_training_to_goal")
			.arguments(Map.of("training", Map.of("name", "Treino sem duracao")))
			.build());

		assertNotNull(result);
		assertTrue(Boolean.TRUE.equals(result.isError()), "esperava isError=true por falta de campo obrigatorio aninhado");
	}

	@Test
	void generateTrainingFromLessonWithoutTrainingReturnsFriendlyErrorInsteadOfCrashing() {
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		CallToolResult result = client.callTool(
				CallToolRequest.builder("generate_training_from_lesson").arguments(Map.of("lessonId", 1)).build());

		assertNotNull(result);
		assertTrue(Boolean.TRUE.equals(result.isError()), "esperava isError=true por falta de 'training'");
	}

	@Test
	@SuppressWarnings("unchecked")
	void phase4bToolsExposeTheirRequiredFieldsInTheInputSchema() {
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		Map<String, Tool> byName = client.listTools()
			.tools()
			.stream()
			.collect(Collectors.toMap(Tool::name, t -> t));

		Tool getExercise = byName.get("get_exercise");
		assertNotNull(getExercise, "get_exercise deveria estar registrada");
		assertTrue(((List<String>) getExercise.inputSchema().get("required")).contains("exerciseId"));

		Tool updatePattern = byName.get("update_exercise_pattern");
		assertNotNull(updatePattern, "update_exercise_pattern deveria estar registrada");
		List<String> updateRequired = (List<String>) updatePattern.inputSchema().get("required");
		assertTrue(updateRequired.contains("exerciseId"), "faltou 'exerciseId' em update_exercise_pattern: " + updateRequired);
		assertTrue(updateRequired.contains("pattern"), "faltou 'pattern' em update_exercise_pattern: " + updateRequired);
		Map<String, Object> updateProps = (Map<String, Object>) updatePattern.inputSchema().get("properties");
		assertTrue(updateProps.containsKey("howToExecute"), "faltou a prop opcional 'howToExecute'");

		Tool addPassage = byName.get("add_marked_passage");
		assertNotNull(addPassage, "add_marked_passage deveria estar registrada");
		List<String> passageRequired = (List<String>) addPassage.inputSchema().get("required");
		assertTrue(passageRequired.contains("exerciseId") && passageRequired.contains("fromSeconds"),
				"add_marked_passage deveria exigir exerciseId e fromSeconds: " + passageRequired);
		Map<String, Object> passageProps = (Map<String, Object>) addPassage.inputSchema().get("properties");
		assertTrue(passageProps.containsKey("toSeconds") && passageProps.containsKey("label"));

		Tool presets = byName.get("list_pattern_presets");
		assertNotNull(presets, "list_pattern_presets deveria estar registrada");
		List<String> presetsRequired = (List<String>) presets.inputSchema().get("required");
		assertTrue(presetsRequired == null || presetsRequired.isEmpty(), "list_pattern_presets nao deveria ter args");

		// add_exercise_to_training: o schema aninhado 'exercise' agora exige 'kind' e
		// aceita 'pattern', e 'howToExecute' deixou de ser obrigatorio.
		Tool addExercise = byName.get("add_exercise_to_training");
		Map<String, Object> exerciseSchema = (Map<String, Object>) ((Map<String, Object>) addExercise.inputSchema()
			.get("properties")).get("exercise");
		Map<String, Object> exerciseProps = (Map<String, Object>) exerciseSchema.get("properties");
		assertTrue(exerciseProps.containsKey("kind") && exerciseProps.containsKey("pattern"),
				"o schema de 'exercise' deveria ter 'kind' e 'pattern'");
		List<String> exerciseRequired = (List<String>) exerciseSchema.get("required");
		assertTrue(exerciseRequired.contains("kind"), "'kind' deveria ser obrigatorio no exercicio inline");
		assertFalse(exerciseRequired.contains("howToExecute"), "'howToExecute' nao deveria mais ser obrigatorio");
	}

	@Test
	void listPatternPresetsReturnsTheThreeBuiltInPresetsWithoutBackend() {
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		CallToolResult result = client.callTool(CallToolRequest.builder("list_pattern_presets").build());

		assertNotNull(result);
		assertFalse(Boolean.TRUE.equals(result.isError()), "list_pattern_presets nao depende do back");
		String text = result.content()
			.stream()
			.filter(TextContent.class::isInstance)
			.map(c -> ((TextContent) c).text())
			.findFirst()
			.orElse("");
		assertTrue(text.contains("groove-4-4") && text.contains("paradiddle") && text.contains("shuffle"),
				"esperava os 3 presets embutidos, veio: " + text);
		assertTrue(text.contains("\"stepsPerBeat\"") && text.contains("\"voices\""),
				"o JSON do pattern deveria aparecer no resultado: " + text);
	}

	@Test
	void updateExercisePatternWithoutPatternReturnsFriendlyError() {
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		CallToolResult result = client.callTool(CallToolRequest.builder("update_exercise_pattern")
			.arguments(Map.of("exerciseId", 1))
			.build());

		assertNotNull(result);
		assertTrue(Boolean.TRUE.equals(result.isError()), "esperava isError=true por falta de 'pattern'");
	}

	@Test
	void addMarkedPassageWithoutFromSecondsReturnsFriendlyError() {
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		CallToolResult result = client.callTool(CallToolRequest.builder("add_marked_passage")
			.arguments(Map.of("exerciseId", 1))
			.build());

		assertNotNull(result);
		assertTrue(Boolean.TRUE.equals(result.isError()), "esperava isError=true por falta de 'fromSeconds'");
	}

	@Test
	void getCoachBriefingReportsBackendDownAsErrorInsteadOfCrashing() {
		// Diferente de health_check, get_coach_briefing depende do back para ter algo a
		// agregar - falha de conexao deve virar isError=true (nao um "ok" vazio).
		McpSyncClient client = startClient("http://localhost:1");
		client.initialize();

		CallToolResult result = client.callTool(CallToolRequest.builder("get_coach_briefing").build());

		assertNotNull(result);
		assertTrue(Boolean.TRUE.equals(result.isError()), "esperava isError=true com o back fora do ar");
	}
}
