package dev.drumcoach.presentation.web;

import java.util.List;

import dev.drumcoach.domain.RepertoireItemStatus;

/** Corpo de {@code POST /api/repertoire-items}. {@code status} ausente/{@code null} = {@code NOT_STARTED}. */
public record CreateRepertoireItemRequest(String songTitle, String artist, RepertoireItemStatus status,
		Integer targetBpm, Integer currentBpm, String notes, List<RepertoireLinkRequest> links) {
}
