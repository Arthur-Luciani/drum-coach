package dev.drumcoach.application;

import dev.drumcoach.domain.Origin;

/**
 * Port central de auditoria (ver ADR-0005 e ADR-0006). Cada entry point de
 * {@code presentation} registra uma implementacao fixa: {@code presentation.web} ->
 * {@link Origin#USER}. A origem nunca e passada explicitamente por um controller/tool -
 * ela e automatica por processo.
 */
public interface OriginProvider {

	Origin currentOrigin();
}
