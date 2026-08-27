package dev.drumcoach.presentation.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Rede de seguranca da API: entidades de dominio validam campos obrigatorios via
 * {@code Objects.requireNonNull} (ver {@code domain.*}), o que gera
 * {@link NullPointerException} quando um controller passa um campo ausente adiante sem
 * checar antes - sem este handler, isso vira um 500 opaco em vez de um 400 explicando o
 * que faltou. Nao substitui validacao de campo especifica onde ela ja existe (ex.:
 * {@code IllegalArgumentException} lancada deliberadamente), so cobre o restante da API
 * de uma vez, sem precisar duplicar checagem de nulidade em cada controller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler({ NullPointerException.class, IllegalArgumentException.class })
	public ResponseEntity<ErrorResponse> handleValidation(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
	}

	/** Corpo de erro simples: {@code {"message": "..."}}. */
	public record ErrorResponse(String message) {
	}
}
