package br.com.srportto.contratocommand.shared.interceptors.api;

import br.com.srportto.contratocommand.shared.exceptions.ApplicationException;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;

@ControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<LayoutErrosApiResponse> erroNegociosResponseEntity(BusinessException exception,
            HttpServletRequest req) {

        LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();
        layoutError.setTimestamp(Instant.now());
        layoutError.setError("Uma regra de negocio foi violada");
        layoutError.setMessage(exception.getMessage());
        layoutError.setPath(req.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(layoutError);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<LayoutErrosApiResponse> erroNegociosResponseEntity(ApplicationException exception,
            HttpServletRequest req) {

        log.error("Erro de aplicacao ao processar {} {}", req.getMethod(), req.getRequestURI(), exception);

        LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();

        layoutError.setTimestamp(Instant.now());
        layoutError.setError("Ocorreu um erro inesperado, entre em contato com o suporte");
        layoutError.setMessage(exception.getMessage());
        layoutError.setPath(req.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(layoutError);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<LayoutErrosApiResponse> erroInesperadoResponseEntity(Exception exception,
            HttpServletRequest req) {

        log.error("Erro inesperado (nao mapeado) ao processar {} {}", req.getMethod(), req.getRequestURI(),
                exception);

        LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();

        layoutError.setTimestamp(Instant.now());
        layoutError.setError("Ocorreu um erro inesperado, entre em contato com o suporte");
        layoutError.setMessage(exception.getMessage());
        layoutError.setPath(req.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(layoutError);
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
