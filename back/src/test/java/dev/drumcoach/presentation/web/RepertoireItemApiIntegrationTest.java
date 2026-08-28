package dev.drumcoach.presentation.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Teste ponta a ponta: item de repertorio criado com multiplos links, listado, e depois
 * atualizado via PATCH (status + BPM atual + um link adicional) - validando que os links
 * originais sao preservados e o novo e adicionado.
 */
@SpringBootTest(classes = WebApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class RepertoireItemApiIntegrationTest {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void overrideDbPath(DynamicPropertyRegistry registry) {
		registry.add("drumcoach.data.db-path", () -> tempDir.resolve("drum-coach-repertoire-test.db").toString());
	}

	@LocalServerPort
	private int port;

	@Test
	void createsListsAndUpdatesRepertoireItemWithLinks() {
		RestClient client = RestClient.create("http://localhost:" + port);

		Map<String, Object> createRequest = Map.of("songTitle", "Tom Sawyer", "artist", "Rush", "targetBpm", 160,
				"links",
				List.of(Map.of("url", "https://youtube.com/example1", "label", "video de referencia"),
						Map.of("url", "https://example.com/sheet.pdf", "label", "partitura")));

		var createResponse = client.post()
			.uri("/api/repertoire-items")
			.body(createRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		Map<String, Object> created = createResponse.getBody();
		assertThat(created).isNotNull();
		assertThat(created.get("status")).isEqualTo("NOT_STARTED");
		assertThat(created.get("createdBy")).isEqualTo("USER");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> createdLinks = (List<Map<String, Object>>) created.get("links");
		assertThat(createdLinks).hasSize(2);
		Number itemId = (Number) created.get("id");

		List<Map<String, Object>> items = client.get()
			.uri("/api/repertoire-items")
			.retrieve()
			.body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
			});
		assertThat(items).hasSize(1);

		Map<String, Object> patchRequest = Map.of("status", "LEARNING", "currentBpm", 110, "newLinks",
				List.of(Map.of("url", "https://example.com/backing-track.mp3", "label", "playback")));

		Map<String, Object> updated = client.patch()
			.uri("/api/repertoire-items/{id}", itemId)
			.body(patchRequest)
			.retrieve()
			.body(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		assertThat(updated).isNotNull();
		assertThat(updated.get("status")).isEqualTo("LEARNING");
		assertThat(updated.get("currentBpm")).isEqualTo(110);
		// campo nao enviado no patch (songTitle) deve permanecer intacto
		assertThat(updated.get("songTitle")).isEqualTo("Tom Sawyer");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> updatedLinks = (List<Map<String, Object>>) updated.get("links");
		assertThat(updatedLinks).hasSize(3);
		assertThat(updatedLinks).extracting(link -> link.get("url"))
			.contains("https://youtube.com/example1", "https://example.com/sheet.pdf",
					"https://example.com/backing-track.mp3");

		// Fase 5: POST aceita 'status' e ja devolve o item nesse estado (sem PATCH depois).
		Map<String, Object> learning = client.post()
			.uri("/api/repertoire-items")
			.body(Map.of("songTitle", "YYZ", "artist", "Rush", "status", "LEARNING"))
			.retrieve()
			.body(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		assertThat(learning).isNotNull();
		assertThat(learning.get("status")).isEqualTo("LEARNING");
	}
}
