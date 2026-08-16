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

		LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();
		layoutError.setTimestamp(Instant.now());
		layoutError.setError("Uma regra de negocio foi violada");
		layoutError.setMessage(exception.getMessage());
		layoutError.setPath(req.getRequestURI());

		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(layoutError);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<LayoutErrosApiResponse> recursoNaoEncontrado(ResourceNotFoundException exception,
			HttpServletRequest req) {

		LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();
		layoutError.setTimestamp(Instant.now());
		layoutError.setError("Recurso nao encontrado");
		layoutError.setMessage(exception.getMessage());
		layoutError.setPath(req.getRequestURI());

		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(layoutError);
	}

	@ExceptionHandler(ApplicationException.class)
	public ResponseEntity<LayoutErrosApiResponse> erroAplicacao(ApplicationException exception,
			HttpServletRequest req) {

		log.error("Erro de aplicacao ao processar {} {}", req.getMethod(), req.getRequestURI(), exception);

		LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();
		layoutError.setTimestamp(Instant.now());
		layoutError.setError("Ocorreu um erro inesperado, entre em contato com o suporte");
		layoutError.setMessage("Consulte o suporte para mais informações");
		layoutError.setPath(req.getRequestURI());

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(layoutError);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<LayoutErrosApiResponse> erroInesperado(Exception exception,
			HttpServletRequest req) {

		log.error("Erro inesperado (nao mapeado) ao processar {} {}", req.getMethod(), req.getRequestURI(),
				exception);

		LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();
		layoutError.setTimestamp(Instant.now());
		layoutError.setError("Ocorreu um erro inesperado, entre em contato com o suporte");
		layoutError.setMessage("Consulte o suporte para mais informações");
		layoutError.setPath(req.getRequestURI());

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(layoutError);
	}

	/** Sem este handler, caminho desconhecido cairia no catch-all de Exception e responderia 500 em vez de 404. */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<LayoutErrosApiResponse> recursoEstaticoNaoEncontrado(NoResourceFoundException exception,
			HttpServletRequest req) {

		log.debug("Recurso estatico nao encontrado em {} {}: {}", req.getMethod(), req.getRequestURI(),
				exception.getMessage());

		LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();
		layoutError.setTimestamp(Instant.now());
		layoutError.setError("Recurso nao encontrado");
		layoutError.setMessage("O recurso solicitado nao foi encontrado");
		layoutError.setPath(req.getRequestURI());

		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(layoutError);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<LayoutErrosApiValidationsResponse> validation(MethodArgumentNotValidException exception,
			HttpServletRequest request) {

		LayoutErrosApiValidationsResponse layoutErrosApiValidationsResponse = new LayoutErrosApiValidationsResponse();

		layoutErrosApiValidationsResponse.setTimestamp(Instant.now());
		layoutErrosApiValidationsResponse.setError(
				"Requisicao nao respeitou as validacoes basicas do contrato, confira as occurrences para mais detalhes");
		layoutErrosApiValidationsResponse
				.setMessage("Erro durante a validacao da requisicao, confira as occurrences...");
		layoutErrosApiValidationsResponse.setPath(request.getRequestURI());

		for (FieldError f : exception.getBindingResult().getFieldErrors()) {
			layoutErrosApiValidationsResponse.addOccurrences(f.getField(), f.getDefaultMessage());
		}

		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(layoutErrosApiValidationsResponse);
	}
}
