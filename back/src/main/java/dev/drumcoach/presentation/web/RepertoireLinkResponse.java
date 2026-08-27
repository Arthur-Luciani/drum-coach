package dev.drumcoach.presentation.web;

import java.time.Instant;

import dev.drumcoach.domain.RepertoireLink;

/** DTO de saida de {@code RepertoireLink} para a API REST. */
public record RepertoireLinkResponse(Long id, String url, String label, Instant createdAt) {

	public static RepertoireLinkResponse from(RepertoireLink link) {
		return new RepertoireLinkResponse(link.id(), link.url(), link.label(), link.createdAt());
	}
}
