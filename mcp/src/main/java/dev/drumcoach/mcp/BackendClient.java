package dev.drumcoach.mcp;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.json.McpJsonMapper;

/**
 * Cliente HTTP fino para a API REST do {@code back} (ver ADR-0007). NAO conhece nada de
 * dominio/persistencia do backend - so sabe montar/parsear os DTOs desta classe (espelhos
 * dos DTOs de {@code presentation.web}) e sempre envia o header de auditoria
 * {@value #ACTOR_HEADER} nas chamadas de escrita, para que o backend marque
 * {@code createdBy}/{@code lastModifiedBy} como {@code CLAUDE} em vez de {@code USER}.
 *
 * <p>
 * Usa {@link HttpClient} nativo do JDK (sem dependencia extra) e {@link McpJsonMapper} -
 * a mesma abstracao de JSON que o SDK MCP ja traz (implementada sobre Jackson 3 por
 * {@code mcp-json-jackson3}) - em vez de somar outra lib de serializacao.
 *
 * <p>
 * Fase 3 (ver ADR-0009): o conceito de "Plano" (TrainingPlan) foi removido do backend -
 * {@code Training} agora referencia {@code Goal} direto via {@code goalId} (nullable =
 * treino avulso). Os antigos {@code /api/plans/*} nao existem mais; o detalhe de
 * progresso por treino mora agora em {@code GET /api/goals/{id}}.
 */
public class BackendClient {

	public static final String ACTOR_HEADER = "X-Drum-Coach-Actor";

	public static final String ACTOR_VALUE = "CLAUDE";

	private final String baseUrl;

	private final McpJsonMapper jsonMapper;

	private final HttpClient httpClient;

	public BackendClient(String baseUrl, McpJsonMapper jsonMapper) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.jsonMapper = jsonMapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	}

	public String baseUrl() {
		return baseUrl;
	}

	// ===================== DTOs (espelham presentation.web do back) =====================

	/** Espelha {@code presentation.web.GoalResponse}. */
	public record GoalDto(Long id, String title, String description, String targetDate, String status,
			String targetMetric, boolean inFocus, String createdBy, String lastModifiedBy, String createdAt,
			String updatedAt) {
	}

	private record CreateGoalRequestDto(String title, String description, String targetDate, String targetMetric) {
	}

	private record UpdateGoalRequestDto(String status, String description, Boolean inFocus) {
	}

	/** Espelha {@code presentation.web.TrainingProgressResponse}. */
	public record TrainingProgressDto(Long trainingId, String name, Integer targetRepetitions, long completedCount) {
	}

	/** Espelha {@code presentation.web.GoalDetailResponse} (substitui o antigo TrainingPlanDetailDto). */
	public record GoalDetailDto(GoalDto goal, List<TrainingProgressDto> trainings) {
	}

	/** Espelha {@code presentation.web.TrainingResponse}. */
	public record TrainingDto(Long id, Long goalId, String name, String description, int targetDurationMinutes,
			Integer targetRepetitions, int orderIndex, String createdBy, String lastModifiedBy, String createdAt,
			String updatedAt) {
	}

	private record CreateTrainingRequestDto(Long goalId, String name, String description,
			int targetDurationMinutes, Integer targetRepetitions, int orderIndex) {
	}

	/** Corpo de {@code PATCH /api/trainings/{id}}: todo campo {@code null} mantem o atual. */
	private record UpdateTrainingRequestDto(String name, String description, Integer targetDurationMinutes,
			Integer targetRepetitions, Long goalId, Integer orderIndex) {
	}

	/**
	 * Espelha {@code presentation.web.ExerciseResponse}. {@code kind} e {@code TOCA_JUNTO}
	 * ou {@code TRANSCRICAO} (ver ADR-0011); {@code pattern} volta como objeto JSON (um
	 * {@code Map}, ja desserializado - {@code null} quando nao ha padrao); {@code passages}
	 * vem preenchida em {@code GET /api/exercises/{id}} / {@code PATCH} e vazia nas
	 * listagens.
	 */
	public record ExerciseDto(Long id, Long trainingId, String name, String exerciseType, String kind,
			String howToExecute, Object pattern, Integer targetBpm, Integer targetDurationSeconds,
			String videoSourceType, String videoUrl, String videoFilePath, int orderIndex, String createdBy,
			String lastModifiedBy, String createdAt, String updatedAt, List<MarkedPassageDto> passages) {
	}

	/** Espelha {@code presentation.web.MarkedPassageResponse} ({@code exercise_passage}). */
	public record MarkedPassageDto(Long id, int fromSeconds, Integer toSeconds, String label, String createdAt) {
	}

	private record CreateExerciseRequestDto(String name, String exerciseType, String kind, String howToExecute,
			Object pattern, Integer targetBpm, Integer targetDurationSeconds, String videoSourceType, String videoUrl,
			String videoFilePath, int orderIndex) {
	}

	/**
	 * Corpo de {@code PATCH /api/exercises/{id}}: todo campo {@code null} mantem o atual;
	 * nao envia {@code kind} (imutavel). {@code pattern} substitui o padrao inteiro.
	 */
	private record UpdateExerciseRequestDto(String name, String exerciseType, String howToExecute, Object pattern,
			Integer targetBpm, Integer targetDurationSeconds, Integer orderIndex) {
	}

	private record MarkedPassageRequestDto(Integer fromSeconds, Integer toSeconds, String label) {
	}

	/** Espelha {@code presentation.web.ExecutionExerciseLogResponse}. */
	public record ExecutionExerciseLogDto(Long id, Long exerciseId, Integer achievedBpm,
			Integer actualDurationSeconds, String notes, String createdAt) {
	}

	private record ExecutionExerciseLogRequestDto(Long exerciseId, Integer achievedBpm,
			Integer actualDurationSeconds, String notes) {
	}

	/** Espelha {@code presentation.web.ExecutionResponse}. */
	public record ExecutionDto(Long id, Long trainingId, String executionDate, Integer actualDurationMinutes,
			String feeling, String generalNotes, Long goalId, List<ExecutionExerciseLogDto> logs, String createdBy,
			String lastModifiedBy, String createdAt, String updatedAt) {
	}

	private record CreateExecutionRequestDto(Long trainingId, String executionDate, Integer actualDurationMinutes,
			String feeling, String generalNotes, Long goalId, List<ExecutionExerciseLogRequestDto> logs) {
	}

	/** Espelha {@code presentation.web.LessonResponse}. */
	public record LessonDto(Long id, String lessonDate, String teacherNotes, String feedback, String focusUntilNext,
			String suggestedMaterial, Long generatedTrainingId, String createdBy, String lastModifiedBy,
			String createdAt, String updatedAt) {
	}

	private record CreateLessonRequestDto(String lessonDate, String teacherNotes, String feedback,
			String focusUntilNext, String suggestedMaterial, Long generatedTrainingId) {
	}

	private record UpdateLessonRequestDto(Long generatedTrainingId) {
	}

	/** Espelha {@code presentation.web.RepertoireLinkResponse}. */
	public record RepertoireLinkDto(Long id, String url, String label, String createdAt) {
	}

	private record RepertoireLinkRequestDto(String url, String label) {
	}

	/** Espelha {@code presentation.web.RepertoireItemResponse}. */
	public record RepertoireItemDto(Long id, String songTitle, String artist, String status, Integer targetBpm,
			Integer currentBpm, String notes, List<RepertoireLinkDto> links, String createdBy, String lastModifiedBy,
			String createdAt, String updatedAt) {
	}

	private record CreateRepertoireItemRequestDto(String songTitle, String artist, String status, Integer targetBpm,
			Integer currentBpm, String notes, List<RepertoireLinkRequestDto> links) {
	}

	private record UpdateRepertoireItemRequestDto(String status, Integer currentBpm, String notes,
			List<RepertoireLinkRequestDto> newLinks) {
	}

	/**
	 * Item de entrada de {@code exercises} dentro da criacao de um treino. {@code kind} e
	 * obrigatorio ({@code TOCA_JUNTO}|{@code TRANSCRICAO}); {@code pattern} (objeto JSON /
	 * {@code Map}) e opcional e so faz sentido em {@code TOCA_JUNTO}; {@code howToExecute}
	 * passou a ser opcional (ver ADR-0011).
	 */
	public record ExerciseInput(String name, String exerciseType, String kind, String howToExecute, Object pattern,
			Integer targetBpm, Integer targetDurationSeconds, String videoSourceType, String videoUrl,
			String videoFilePath, int orderIndex) {
	}

	/** Item de entrada de {@code logs} para {@code recordExecution}. */
	public record ExecutionLogInput(Long exerciseId, Integer achievedBpm, Integer actualDurationSeconds,
			String notes) {
	}

	/** Item de entrada de {@code links} para {@code addRepertoireItem}. */
	public record RepertoireLinkInput(String url, String label) {
	}

	// ===================== Goal =====================

	public GoalDto[] listGoals() throws IOException, InterruptedException {
		return get("/api/goals", GoalDto[].class);
	}

	/** @return {@code null} se a meta nao existir (404). */
	public GoalDetailDto getGoalDetail(long id) throws IOException, InterruptedException {
		return getOptional("/api/goals/" + id, GoalDetailDto.class);
	}

	public GoalDto createGoal(String title, String description, String targetDate, String targetMetric)
			throws IOException, InterruptedException {
		return post("/api/goals", new CreateGoalRequestDto(title, description, targetDate, targetMetric),
				GoalDto.class, "POST /api/goals");
	}

	public GoalDto updateGoal(long id, String status, String description, Boolean inFocus)
			throws IOException, InterruptedException {
		return patch("/api/goals/" + id, new UpdateGoalRequestDto(status, description, inFocus), GoalDto.class,
				"PATCH /api/goals/" + id);
	}

	// ===================== Training =====================

	public TrainingDto[] listTrainings(Long goalId) throws IOException, InterruptedException {
		Map<String, String> params = new LinkedHashMap<>();
		if (goalId != null) {
			params.put("goalId", goalId.toString());
		}
		return get("/api/trainings", params, TrainingDto[].class);
	}

	public TrainingDto createTraining(Long goalId, String name, String description, int targetDurationMinutes,
			Integer targetRepetitions, int orderIndex) throws IOException, InterruptedException {
		return post("/api/trainings",
				new CreateTrainingRequestDto(goalId, name, description, targetDurationMinutes, targetRepetitions,
						orderIndex),
				TrainingDto.class, "POST /api/trainings");
	}

	/** {@code PATCH /api/trainings/{id}} - edicao parcial; todo argumento {@code null} mantem o atual. */
	public TrainingDto updateTraining(long id, String name, String description, Integer targetDurationMinutes,
			Integer targetRepetitions, Long goalId, Integer orderIndex) throws IOException, InterruptedException {
		return patch("/api/trainings/" + id, new UpdateTrainingRequestDto(name, description, targetDurationMinutes,
				targetRepetitions, goalId, orderIndex), TrainingDto.class, "PATCH /api/trainings/" + id);
	}

	/**
	 * {@code DELETE /api/trainings/{id}} - apaga o treino e, em cascata, seus exercicios e
	 * trechos marcados. O back responde 409 (propagado como {@link BackendException}) se
	 * houver execucoes registradas neste treino.
	 */
	public void deleteTraining(long id) throws IOException, InterruptedException {
		delete("/api/trainings/" + id, "DELETE /api/trainings/" + id);
	}

	// ===================== Exercise =====================

	public ExerciseDto[] listExercises(long trainingId) throws IOException, InterruptedException {
		return get("/api/trainings/" + trainingId + "/exercises", ExerciseDto[].class);
	}

	/** @return {@code null} se o exercicio nao existir (404). */
	public ExerciseDto getExercise(long id) throws IOException, InterruptedException {
		return getOptional("/api/exercises/" + id, ExerciseDto.class);
	}

	/**
	 * @param kind {@code TOCA_JUNTO} ou {@code TRANSCRICAO} (obrigatorio).
	 * @param howToExecute nota livre opcional (pode ser {@code null}).
	 * @param pattern documento do padrao tocavel como objeto JSON ja montado ({@code Map} /
	 * {@code JsonNode}) - opcional, so aceito em {@code TOCA_JUNTO}. Vai como campo
	 * {@code pattern} (objeto, nao string escapada) no corpo do POST.
	 */
	public ExerciseDto createExercise(long trainingId, String name, String exerciseType, String kind,
			String howToExecute, Object pattern, Integer targetBpm, Integer targetDurationSeconds,
			String videoSourceType, String videoUrl, String videoFilePath, int orderIndex)
			throws IOException, InterruptedException {
		String path = "/api/trainings/" + trainingId + "/exercises";
		return post(path,
				new CreateExerciseRequestDto(name, exerciseType, kind, howToExecute, pattern, targetBpm,
						targetDurationSeconds, videoSourceType, videoUrl, videoFilePath, orderIndex),
				ExerciseDto.class, "POST " + path);
	}

	/**
	 * {@code PATCH /api/exercises/{id}} - edicao parcial de qualquer campo editavel; todo
	 * argumento {@code null} mantem o atual. {@code pattern}, quando presente, substitui o
	 * padrao inteiro (nao incremental) e e re-validado pelo back (400 com mensagem se
	 * malformado). Nao envia {@code kind} (imutavel).
	 */
	public ExerciseDto updateExercise(long id, String name, String exerciseType, String howToExecute, Object pattern,
			Integer targetBpm, Integer targetDurationSeconds, Integer orderIndex)
			throws IOException, InterruptedException {
		return patch("/api/exercises/" + id, new UpdateExerciseRequestDto(name, exerciseType, howToExecute, pattern,
				targetBpm, targetDurationSeconds, orderIndex), ExerciseDto.class, "PATCH /api/exercises/" + id);
	}

	/**
	 * {@code DELETE /api/exercises/{id}} - apaga o exercicio e seus trechos marcados em
	 * cascata. O back responde 409 (propagado como {@link BackendException}) se houver
	 * execucao com log deste exercicio.
	 */
	public void deleteExercise(long id) throws IOException, InterruptedException {
		delete("/api/exercises/" + id, "DELETE /api/exercises/" + id);
	}

	/** {@code POST /api/exercises/{id}/passages} - adiciona um trecho marcado ao exercicio. */
	public MarkedPassageDto addMarkedPassage(long exerciseId, int fromSeconds, Integer toSeconds, String label)
			throws IOException, InterruptedException {
		String path = "/api/exercises/" + exerciseId + "/passages";
		return post(path, new MarkedPassageRequestDto(fromSeconds, toSeconds, label), MarkedPassageDto.class,
				"POST " + path);
	}

	// ===================== Execution =====================

	public ExecutionDto[] listExecutions(Long trainingId, Long goalId) throws IOException, InterruptedException {
		Map<String, String> params = new LinkedHashMap<>();
		if (trainingId != null) {
			params.put("trainingId", trainingId.toString());
		}
		if (goalId != null) {
			params.put("goalId", goalId.toString());
		}
		return get("/api/executions", params, ExecutionDto[].class);
	}

	public ExecutionDto createExecution(Long trainingId, String executionDate, Integer actualDurationMinutes,
			String feeling, String generalNotes, Long goalId, List<ExecutionLogInput> logs)
			throws IOException, InterruptedException {
		List<ExecutionExerciseLogRequestDto> logDtos = logs == null ? List.of()
				: logs.stream()
					.map(log -> new ExecutionExerciseLogRequestDto(log.exerciseId(), log.achievedBpm(),
							log.actualDurationSeconds(), log.notes()))
					.toList();
		return post("/api/executions", new CreateExecutionRequestDto(trainingId, executionDate,
				actualDurationMinutes, feeling, generalNotes, goalId, logDtos), ExecutionDto.class,
				"POST /api/executions");
	}

	// ===================== Lesson =====================

	public LessonDto[] listLessons(String from, String to) throws IOException, InterruptedException {
		Map<String, String> params = new LinkedHashMap<>();
		if (from != null) {
			params.put("from", from);
		}
		if (to != null) {
			params.put("to", to);
		}
		return get("/api/lessons", params, LessonDto[].class);
	}

	/** @return {@code null} se a aula nao existir (404). */
	public LessonDto getLesson(long id) throws IOException, InterruptedException {
		return getOptional("/api/lessons/" + id, LessonDto.class);
	}

	public LessonDto createLesson(String lessonDate, String teacherNotes, String feedback, String focusUntilNext,
			String suggestedMaterial, Long generatedTrainingId) throws IOException, InterruptedException {
		return post("/api/lessons", new CreateLessonRequestDto(lessonDate, teacherNotes, feedback, focusUntilNext,
				suggestedMaterial, generatedTrainingId), LessonDto.class, "POST /api/lessons");
	}

	public LessonDto linkGeneratedTraining(long lessonId, long generatedTrainingId)
			throws IOException, InterruptedException {
		return patch("/api/lessons/" + lessonId, new UpdateLessonRequestDto(generatedTrainingId), LessonDto.class,
				"PATCH /api/lessons/" + lessonId);
	}

	// ===================== RepertoireItem =====================

	public RepertoireItemDto[] listRepertoire() throws IOException, InterruptedException {
		return get("/api/repertoire-items", RepertoireItemDto[].class);
	}

	public RepertoireItemDto createRepertoireItem(String songTitle, String artist, String status, Integer targetBpm,
			Integer currentBpm, String notes, List<RepertoireLinkInput> links) throws IOException, InterruptedException {
		List<RepertoireLinkRequestDto> linkDtos = links == null ? List.of()
				: links.stream().map(link -> new RepertoireLinkRequestDto(link.url(), link.label())).toList();
		return post("/api/repertoire-items",
				new CreateRepertoireItemRequestDto(songTitle, artist, status, targetBpm, currentBpm, notes, linkDtos),
				RepertoireItemDto.class, "POST /api/repertoire-items");
	}

	public RepertoireItemDto updateRepertoireItem(long id, String status, Integer currentBpm, String notes,
			List<RepertoireLinkInput> newLinks) throws IOException, InterruptedException {
		List<RepertoireLinkRequestDto> linkDtos = newLinks == null ? List.of()
				: newLinks.stream().map(link -> new RepertoireLinkRequestDto(link.url(), link.label())).toList();
		return patch("/api/repertoire-items/" + id,
				new UpdateRepertoireItemRequestDto(status, currentBpm, notes, linkDtos), RepertoireItemDto.class,
				"PATCH /api/repertoire-items/" + id);
	}

	// ===================== Health =====================

	/** @return o corpo bruto da resposta de {@code GET /api/health} (formato de corpo nao importa - so serve de diagnostico). */
	public String healthCheck() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/health"))
			.header(ACTOR_HEADER, ACTOR_VALUE)
			.timeout(Duration.ofSeconds(5))
			.GET()
			.build();
		HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
		requireSuccess(response, "GET /api/health");
		return response.body();
	}

	// ===================== Infra HTTP generica =====================

	private <T> T get(String path, Class<T> type) throws IOException, InterruptedException {
		return get(path, Map.of(), type);
	}

	private <T> T get(String path, Map<String, String> queryParams, Class<T> type)
			throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri(path, queryParams))
			.header(ACTOR_HEADER, ACTOR_VALUE)
			.timeout(Duration.ofSeconds(10))
			.GET()
			.build();
		HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
		requireSuccess(response, "GET " + path);
		return parse(response.body(), type, "GET " + path);
	}

	/** Como {@link #get(String, Class)}, mas retorna {@code null} em vez de lancar em 404. */
	private <T> T getOptional(String path, Class<T> type) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
			.header(ACTOR_HEADER, ACTOR_VALUE)
			.timeout(Duration.ofSeconds(10))
			.GET()
			.build();
		HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
		if (response.statusCode() == 404) {
			return null;
		}
		requireSuccess(response, "GET " + path);
		return parse(response.body(), type, "GET " + path);
	}

	private <T> T post(String path, Object body, Class<T> type, String call) throws IOException, InterruptedException {
		String json = serialize(body, call);
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
			.header(ACTOR_HEADER, ACTOR_VALUE)
			.header("Content-Type", "application/json")
			.timeout(Duration.ofSeconds(10))
			.POST(BodyPublishers.ofString(json))
			.build();
		HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
		if (response.statusCode() != 201) {
			throw new BackendException(
					call + " respondeu status " + response.statusCode() + " (esperava 201): " + response.body());
		}
		return parse(response.body(), type, call);
	}

	private <T> T patch(String path, Object body, Class<T> type, String call) throws IOException, InterruptedException {
		String json = serialize(body, call);
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
			.header(ACTOR_HEADER, ACTOR_VALUE)
			.header("Content-Type", "application/json")
			.timeout(Duration.ofSeconds(10))
			.method("PATCH", BodyPublishers.ofString(json))
			.build();
		HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
		if (response.statusCode() == 404) {
			throw new BackendException(call + " respondeu 404 (recurso nao encontrado).");
		}
		requireSuccess(response, call);
		return parse(response.body(), type, call);
	}

	private void delete(String path, String call) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
			.header(ACTOR_HEADER, ACTOR_VALUE)
			.timeout(Duration.ofSeconds(10))
			.DELETE()
			.build();
		HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
		if (response.statusCode() == 404) {
			throw new BackendException(call + " respondeu 404 (recurso nao encontrado).");
		}
		requireSuccess(response, call);
	}

	private URI uri(String path, Map<String, String> queryParams) {
		if (queryParams == null || queryParams.isEmpty()) {
			return URI.create(baseUrl + path);
		}
		StringBuilder sb = new StringBuilder(baseUrl).append(path).append('?');
		boolean first = true;
		for (Map.Entry<String, String> entry : queryParams.entrySet()) {
			if (!first) {
				sb.append('&');
			}
			first = false;
			sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
				.append('=')
				.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
		}
		return URI.create(sb.toString());
	}

	private void requireSuccess(HttpResponse<String> response, String call) {
		if (response.statusCode() / 100 != 2) {
			throw new BackendException(call + " respondeu status " + response.statusCode() + ": " + response.body());
		}
	}

	private String serialize(Object body, String call) {
		try {
			return jsonMapper.writeValueAsString(body);
		}
		catch (IOException e) {
			throw new BackendException("Falha ao serializar o corpo de " + call + ": " + e.getMessage(), e);
		}
	}

	private <T> T parse(String json, Class<T> type, String call) {
		try {
			return jsonMapper.readValue(json, type);
		}
		catch (IOException e) {
			throw new BackendException("Resposta invalida de " + call + ": " + e.getMessage(), e);
		}
	}

	/** Erro de comunicacao com o back (conexao recusada, status inesperado, JSON invalido, etc). */
	public static class BackendException extends RuntimeException {

		public BackendException(String message) {
			super(message);
		}

		public BackendException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
