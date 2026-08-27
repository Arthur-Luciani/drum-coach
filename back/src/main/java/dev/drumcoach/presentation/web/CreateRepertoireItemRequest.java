package dev.drumcoach.presentation.web;

import java.util.List;

/** Corpo de {@code POST /api/repertoire-items}. */
public record CreateRepertoireItemRequest(String songTitle, String artist, Integer targetBpm, Integer currentBpm,
		String notes, List<RepertoireLinkRequest> links) {
}
