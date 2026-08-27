package dev.drumcoach.presentation.web;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import dev.drumcoach.application.OriginProvider;
import dev.drumcoach.domain.Origin;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolve o {@link OriginProvider} por requisicao HTTP, a partir do header
 * {@value #ACTOR_HEADER} (ver ADR-0007): presente e igual a {@code CLAUDE}, a origem e
 * {@link Origin#CLAUDE} (chamadas do proxy MCP); ausente, a origem e {@link Origin#USER}
 * (chamadas do front). Escopo de requisicao porque a origem muda a cada chamada, ao
 * contrario do desenho anterior (ADR-0006) em que era um bean fixo por processo.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = org.springframework.context.annotation.ScopedProxyMode.TARGET_CLASS)
public class RequestOriginProvider implements OriginProvider {

	static final String ACTOR_HEADER = "X-Drum-Coach-Actor";

	private final HttpServletRequest request;

	public RequestOriginProvider(HttpServletRequest request) {
		this.request = request;
	}

	@Override
	public Origin currentOrigin() {
		String actor = request.getHeader(ACTOR_HEADER);
		return Origin.CLAUDE.name().equalsIgnoreCase(actor) ? Origin.CLAUDE : Origin.USER;
	}
}
