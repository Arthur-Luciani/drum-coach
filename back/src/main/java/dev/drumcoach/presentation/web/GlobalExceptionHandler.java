package dev.drumcoach.presentation.web;

import java.util.NoSuchElementException;

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

	/**
	 * Recurso referenciado por um id que nao existe (ex.: PATCH/DELETE de um treino ou
	 * exercicio inexistente). Um {@code @ExceptionHandler} local num controller ainda tem
	 * precedencia sobre este quando quiser um corpo diferente.
	 */
	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
	}

	/**
	 * Conflito de estado: a operacao e valida, mas o estado atual do recurso a impede (ex.:
	 * apagar um treino/exercicio que ainda tem execucoes registradas). Mapeia para 409.
	 */
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
	}

	/** Corpo de erro simples: {@code {"message": "..."}}. */
	public record ErrorResponse(String message) {
	}
}
