package dev.drumcoach.presentation.web;

import java.time.Instant;
import java.util.List;

import dev.drumcoach.domain.Origin;
import dev.drumcoach.domain.RepertoireItem;
import dev.drumcoach.domain.RepertoireItemStatus;

/** DTO de saida de {@code RepertoireItem} (+ seus links) para a API REST. */
public record RepertoireItemResponse(Long id, String songTitle, String artist, RepertoireItemStatus status,
		Integer targetBpm, Integer currentBpm, String notes, List<RepertoireLinkResponse> links, Origin createdBy,
		Origin lastModifiedBy, Instant createdAt, Instant updatedAt) {

	public static RepertoireItemResponse from(RepertoireItem item) {
		List<RepertoireLinkResponse> links = item.links().stream().map(RepertoireLinkResponse::from).toList();
		return new RepertoireItemResponse(item.id(), item.songTitle(), item.artist(), item.status(),
				item.targetBpm(), item.currentBpm(), item.notes(), links, item.createdBy(), item.lastModifiedBy(),
				item.createdAt(), item.updatedAt());
	}
}
