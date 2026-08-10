package br.com.srportto.contratocommand.shared.interceptors.api;

import org.hibernate.StaleStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

import br.com.srportto.contratocommand.shared.exceptions.ApplicationException;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import br.com.srportto.contratocommand.shared.exceptions.RecursoJaExisteException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;

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
        layoutError.setMessage("Consulte o suporte para mais informações");
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
        layoutError.setMessage("Consulte o suporte para mais informações");
        layoutError.setPath(req.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(layoutError);
    }

    /**
     * Deixa o handler de "recurso estatico nao encontrado" passar direto como 404 nativo,
     * sem interceptar com o catch-all (que devolveria 500). Importante para o endpoint
     * `/v3/api-docs` do springdoc em testes de slice sem auto-configuracao completa.
     */
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

    // Mapeia OptimisticLockException (lock otimista falhou) para 409 Conflict
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<LayoutErrosApiResponse> conflitoLockOtimista(OptimisticLockException exception,
            HttpServletRequest req) {

        log.warn("Conflito de concorrencia detectado (OptimisticLockException) ao processar {} {}",
                req.getMethod(), req.getRequestURI());

        LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();
        layoutError.setTimestamp(Instant.now());
        layoutError.setError("Conflito de concorrencia: a autorizacao foi modificada por outra requisicao");
        layoutError.setMessage("Tente novamente");
        layoutError.setPath(req.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(layoutError);
    }

    // Mapeia ObjectOptimisticLockingFailureException (variant do Spring) para 409 Conflict
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<LayoutErrosApiResponse> conflitoLockOtimista(ObjectOptimisticLockingFailureException exception,
            HttpServletRequest req) {

        log.warn("Conflito de concorrencia detectado (ObjectOptimisticLockingFailureException) ao processar {} {}",
                req.getMethod(), req.getRequestURI());

        LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();
        layoutError.setTimestamp(Instant.now());
        layoutError.setError("Conflito de concorrencia: a autorizacao foi modificada por outra requisicao");
        layoutError.setMessage("Tente novamente");
        layoutError.setPath(req.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(layoutError);
    }

    /**
     * Mapeia as demais falhas de concorrencia do Spring para 409 Conflict.
     *
     * <p>Cobre em especial {@code CannotAcquireLockException}, a forma que o conflito assume quando
     * a transacao vencedora move a linha para a particao de expurgo: o PostgreSQL nao consegue
     * seguir a cadeia de atualizacao entre particoes e devolve "tuple to be locked was already
     * moved to another partition due to concurrent update" (SQLSTATE 40001), que nao e um conflito
     * de versao e portanto nao casa com os handlers de lock otimista acima. Sem este handler, um
     * conflito real entre chamadores cairia no catch-all e viraria 500.
     *
     * <p>Os handlers de {@code OptimisticLockException} e {@code ObjectOptimisticLockingFailureException}
     * permanecem: o Spring escolhe sempre o mais especifico.
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<LayoutErrosApiResponse> conflitoConcorrencia(ConcurrencyFailureException exception,
            HttpServletRequest req) {

        log.warn("Conflito de concorrencia detectado ({}) ao processar {} {}",
                exception.getClass().getSimpleName(), req.getMethod(), req.getRequestURI());

        LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();
        layoutError.setTimestamp(Instant.now());
        layoutError.setError("Conflito de concorrencia: a autorizacao foi modificada por outra requisicao");
        layoutError.setMessage("Tente novamente");
        layoutError.setPath(req.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(layoutError);
    }

    // Mapeia tentativa de criar recurso que ja existe (chave de negocio duplicada) para 409 Conflict
    @ExceptionHandler(RecursoJaExisteException.class)
    public ResponseEntity<LayoutErrosApiResponse> recursoJaExiste(RecursoJaExisteException exception,
            HttpServletRequest req) {

        LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();
        layoutError.setTimestamp(Instant.now());
        layoutError.setError("O recurso ja existe");
        layoutError.setMessage(exception.getMessage());
        layoutError.setPath(req.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(layoutError);
    }

    // Mapeia violacoes de integridade (constraint UNIQUE, chave duplicada, etc.) para 409 Conflict
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<LayoutErrosApiResponse> conflitoIntegridade(DataIntegrityViolationException exception,
            HttpServletRequest req) {

        log.warn("Violacao de integridade detectada (DataIntegrityViolationException) ao processar {} {} - causa: {}",
                req.getMethod(), req.getRequestURI(), exception.getCause());

        LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();
        layoutError.setTimestamp(Instant.now());
        layoutError.setError("Conflito de dados: a operacao viola uma regra de unicidade ou integridade");
        layoutError.setMessage("Verifique se ja existe um recurso com as mesmas caracteristicas");
        layoutError.setPath(req.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(layoutError);
    }

    // Mapeia StaleStateException (estado obsoleto, tipicamente do delete+insert do ExpurgoAutorizacaoService) para 409 Conflict
    @ExceptionHandler(StaleStateException.class)
    public ResponseEntity<LayoutErrosApiResponse> conflitoEstadoObsoleto(StaleStateException exception,
            HttpServletRequest req) {

        log.warn("Estado obsoleto detectado (StaleStateException) ao processar {} {} - linha nao encontrada apos operacao",
                req.getMethod(), req.getRequestURI());

        LayoutErrosApiResponse layoutError = new LayoutErrosApiResponse();
        layoutError.setTimestamp(Instant.now());
        layoutError.setError("Conflito de concorrencia: a autorizacao foi modificada ou removida por outra requisicao");
        layoutError.setMessage("Tente novamente");
        layoutError.setPath(req.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(layoutError);
    }
}
