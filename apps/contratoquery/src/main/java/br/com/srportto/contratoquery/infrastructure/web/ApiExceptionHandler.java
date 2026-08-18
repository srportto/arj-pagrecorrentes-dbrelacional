package br.com.srportto.contratoquery.infrastructure.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

import br.com.srportto.contratoquery.domain.exception.ApplicationException;
import br.com.srportto.contratoquery.domain.exception.BusinessException;
import br.com.srportto.contratoquery.domain.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<LayoutErrosApiResponse> erroNegocio(BusinessException exception,
			HttpServletRequest req) {

		var layoutError = new LayoutErrosApiResponse(
				Instant.now(),
				"Uma regra de negocio foi violada",
				exception.getMessage(),
				req.getRequestURI());

		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(layoutError);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<LayoutErrosApiResponse> recursoNaoEncontrado(ResourceNotFoundException exception,
			HttpServletRequest req) {

		var layoutError = new LayoutErrosApiResponse(
				Instant.now(),
				"Recurso nao encontrado",
				exception.getMessage(),
				req.getRequestURI());

		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(layoutError);
	}

	@ExceptionHandler(ApplicationException.class)
	public ResponseEntity<LayoutErrosApiResponse> erroAplicacao(ApplicationException exception,
			HttpServletRequest req) {

		log.error("Erro de aplicacao ao processar {} {}", req.getMethod(), req.getRequestURI(), exception);

		var layoutError = new LayoutErrosApiResponse(
				Instant.now(),
				"Ocorreu um erro inesperado, entre em contato com o suporte",
				"Consulte o suporte para mais informações",
				req.getRequestURI());

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(layoutError);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<LayoutErrosApiResponse> erroInesperado(Exception exception,
			HttpServletRequest req) {

		log.error("Erro inesperado (nao mapeado) ao processar {} {}", req.getMethod(), req.getRequestURI(),
				exception);

		var layoutError = new LayoutErrosApiResponse(
				Instant.now(),
				"Ocorreu um erro inesperado, entre em contato com o suporte",
				"Consulte o suporte para mais informações",
				req.getRequestURI());

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(layoutError);
	}

	/** Sem este handler, caminho desconhecido cairia no catch-all de Exception e responderia 500 em vez de 404. */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<LayoutErrosApiResponse> recursoEstaticoNaoEncontrado(NoResourceFoundException exception,
			HttpServletRequest req) {

		log.debug("Recurso estatico nao encontrado em {} {}: {}", req.getMethod(), req.getRequestURI(),
				exception.getMessage());

		var layoutError = new LayoutErrosApiResponse(
				Instant.now(),
				"Recurso nao encontrado",
				"O recurso solicitado nao foi encontrado",
				req.getRequestURI());

		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(layoutError);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<LayoutErrosApiValidationsResponse> validation(MethodArgumentNotValidException exception,
			HttpServletRequest request) {

		var layoutErrosApiValidationsResponse = new LayoutErrosApiValidationsResponse(
				Instant.now(),
				"Requisicao nao respeitou as validacoes basicas do contrato, confira as occurrences para mais detalhes",
				"Erro durante a validacao da requisicao, confira as occurrences...",
				request.getRequestURI());

		for (FieldError f : exception.getBindingResult().getFieldErrors()) {
			layoutErrosApiValidationsResponse.addOccurrence(f.getField(), f.getDefaultMessage());
		}

		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(layoutErrosApiValidationsResponse);
	}
}
