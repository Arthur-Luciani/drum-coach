package dev.drumcoach.presentation.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Fallback de SPA: reencaminha internamente as rotas de deep-link do Angular para o
 * {@code index.html}, para que o roteador do lado do cliente assuma quando a pagina e
 * aberta direto pela URL (F5, link colado, favorito).
 *
 * <p>O processo web serve a SPA a partir de {@code classpath:/static/index.html} (build
 * do Angular empacotado pelo frontend-maven-plugin). Sem este fallback, so {@code /}
 * funciona: pedir {@code /dashboard}, {@code /goals/42} etc. faz o Spring procurar um
 * handler ou recurso estatico com aquele path, nao achar, e devolver a "Whitelabel Error
 * Page" (404).
 *
 * <p>O que <b>nao</b> e interceptado aqui:
 * <ul>
 *   <li><b>{@code /api/**}</b> - o lookahead {@code (?!api$)} exclui o segmento {@code api},
 *       e de qualquer forma os mapeamentos literais dos {@code @RestController} tem
 *       precedencia sobre padroes com variavel;
 *   <li><b>arquivos estaticos</b> - cada segmento casa {@code [^.]*} (sem ponto), entao
 *       {@code main-ABC.js}, {@code styles.css}, {@code favicon.ico}, {@code assets/logo.svg}
 *       etc. nao casam e seguem para o resource handler normalmente.
 * </ul>
 *
 * <p>Os padroes cobrem ate 3 niveis de path; hoje a rota mais profunda do Angular e
 * {@code goals/:id} (2 niveis). Se surgir uma rota mais aninhada, acrescente um padrao.
 */
@Controller
public class SpaForwardController {

	@GetMapping(value = {
			"/{p1:(?!api$)[^.]*}",
			"/{p1:(?!api$)[^.]*}/{p2:[^.]*}",
			"/{p1:(?!api$)[^.]*}/{p2:[^.]*}/{p3:[^.]*}"
	})
	public String forwardToSpa() {
		return "forward:/index.html";
	}
}
