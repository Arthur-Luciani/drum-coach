package dev.drumcoach.domain;

/**
 * Quem originou uma escrita no caderno: o usuario (via interface web) ou o Claude
 * (via MCP). Ver ADR-0005 (auditoria de origem).
 */
public enum Origin {
	USER,
	CLAUDE
}
