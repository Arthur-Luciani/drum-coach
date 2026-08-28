package dev.drumcoach.presentation.web;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.drumcoach.application.CreateRepertoireItemCommand;
import dev.drumcoach.application.CreateRepertoireItemUseCase;
import dev.drumcoach.application.ListRepertoireItemsUseCase;
import dev.drumcoach.application.RepertoireLinkCommand;
import dev.drumcoach.application.UpdateRepertoireItemCommand;
import dev.drumcoach.application.UpdateRepertoireItemUseCase;
import dev.drumcoach.domain.RepertoireItem;

/**
 * Adapter HTTP para a entidade RepertoireItem (+ seus RepertoireLink). Nunca toca em
 * {@code infra} diretamente - so monta o comando e chama o caso de uso, exatamente como
 * uma tool MCP faria.
 */
@RestController
@RequestMapping("/api/repertoire-items")
public class RepertoireItemController {

	private final CreateRepertoireItemUseCase createRepertoireItemUseCase;
	private final ListRepertoireItemsUseCase listRepertoireItemsUseCase;
	private final UpdateRepertoireItemUseCase updateRepertoireItemUseCase;

	public RepertoireItemController(CreateRepertoireItemUseCase createRepertoireItemUseCase,
			ListRepertoireItemsUseCase listRepertoireItemsUseCase,
			UpdateRepertoireItemUseCase updateRepertoireItemUseCase) {
		this.createRepertoireItemUseCase = createRepertoireItemUseCase;
		this.listRepertoireItemsUseCase = listRepertoireItemsUseCase;
		this.updateRepertoireItemUseCase = updateRepertoireItemUseCase;
	}

	@PostMapping
	public ResponseEntity<RepertoireItemResponse> create(@RequestBody CreateRepertoireItemRequest request) {
		List<RepertoireLinkCommand> links = toLinkCommands(request.links());
		RepertoireItem item = createRepertoireItemUseCase.execute(new CreateRepertoireItemCommand(
				request.songTitle(), request.artist(), request.status(), request.targetBpm(), request.currentBpm(),
				request.notes(), links));
		return ResponseEntity.created(URI.create("/api/repertoire-items/" + item.id()))
			.body(RepertoireItemResponse.from(item));
	}

	@GetMapping
	public List<RepertoireItemResponse> list() {
		return listRepertoireItemsUseCase.execute().stream().map(RepertoireItemResponse::from).toList();
	}

	@PatchMapping("/{id}")
	public RepertoireItemResponse update(@PathVariable long id, @RequestBody UpdateRepertoireItemRequest request) {
		List<RepertoireLinkCommand> newLinks = toLinkCommands(request.newLinks());
		RepertoireItem updated = updateRepertoireItemUseCase.execute(new UpdateRepertoireItemCommand(id,
				request.status(), request.currentBpm(), request.notes(), newLinks));
		return RepertoireItemResponse.from(updated);
	}

	private static List<RepertoireLinkCommand> toLinkCommands(List<RepertoireLinkRequest> links) {
		return links == null ? List.of()
				: links.stream().map(link -> new RepertoireLinkCommand(link.url(), link.label())).toList();
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public void handleNotFound() {
		// corpo vazio - so sinaliza 404 quando o item de repertorio nao existe.
	}
}
