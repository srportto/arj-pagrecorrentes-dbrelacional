package br.com.srportto.contratocommand.infrastructure.web;

import br.com.srportto.contratocommand.domain.exception.ApplicationException;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import br.com.srportto.contratocommand.domain.exception.RecursoJaExisteException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Testes do ApiExceptionHandler")
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private HttpServletRequest req() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/autorizacoes");
        return req;
    }

    @Test
    @DisplayName("BusinessException → 422")
    void negocio422() {
        ResponseEntity<LayoutErrosApiResponse> resp =
                handler.erroNegociosResponseEntity(new BusinessException("regra"), req());

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, resp.getStatusCode());
        assertEquals("regra", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("RecursoJaExisteException → 409 (não 422 — recurso já existe é conflito, não regra de negócio)")
    void recursoJaExiste409() {
        ResponseEntity<LayoutErrosApiResponse> resp =
                handler.recursoJaExiste(new RecursoJaExisteException("já existe"), req());

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals("já existe", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("ApplicationException → 500 com mensagem genérica (sem detalhe técnico)")
    void aplicacao500() {
        ResponseEntity<LayoutErrosApiResponse> resp =
                handler.erroNegociosResponseEntity(new ApplicationException("falha"), req());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        // Resposta não contém o getMessage() da exceção original (proteção contra vazamento de detalhe técnico)
        assertEquals("Consulte o suporte para mais informações", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("Exception genérica não mapeada → 500 com mensagem genérica (sem detalhe técnico)")
    void erroNaoMapeado500() {
        ResponseEntity<LayoutErrosApiResponse> resp =
                handler.erroInesperadoResponseEntity(new NullPointerException("npe inesperada"), req());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        // Resposta não contém o getMessage() da exceção original (proteção contra vazamento de detalhe técnico)
        assertEquals("Consulte o suporte para mais informações", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("MethodArgumentNotValidException → 422 com ocorrências por campo")
    void validacao422() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(new FieldError("obj", "valor", "obrigatório")));

        ResponseEntity<LayoutErrosApiValidationsResponse> resp = handler.validation(ex, req());

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, resp.getStatusCode());
        assertEquals(1, resp.getBody().getOccurrences().size());
        assertEquals("valor", resp.getBody().getOccurrences().get(0).getFieldName());
    }

    // ========== Testes dos novos handlers de concorrência ==========

    @Test
    @DisplayName("OptimisticLockException → 409 Conflict")
    void optimisticLockException_Retorna409() {
        OptimisticLockException ex = new OptimisticLockException("Versão conflitante");

        ResponseEntity<LayoutErrosApiResponse> response = handler.conflitoLockOtimista(ex, req());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Conflito de concorrencia: a autorizacao foi modificada por outra requisicao",
                response.getBody().getError());
        assertFalse(response.getBody().getMessage().contains("Exception"));
    }

    @Test
    @DisplayName("ObjectOptimisticLockingFailureException → 409 Conflict")
    void objectOptimisticLockingFailure_Retorna409() {
        ObjectOptimisticLockingFailureException ex = mock(ObjectOptimisticLockingFailureException.class);

        ResponseEntity<LayoutErrosApiResponse> response = handler.conflitoLockOtimista(ex, req());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Conflito de concorrencia: a autorizacao foi modificada por outra requisicao",
                response.getBody().getError());
    }

    @Test
    @DisplayName("DataIntegrityViolationException → 409 Conflict (sem expor nome de constraint)")
    void dataIntegrityViolation_Retorna409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "Violação de constraint UNIQUE", new Exception("Constraint viola uk_autorizacao_empresa_particao"));

        ResponseEntity<LayoutErrosApiResponse> response = handler.conflitoIntegridade(ex, req());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Conflito de dados: a operacao viola uma regra de unicidade ou integridade",
                response.getBody().getError());
        // Valida que o nome da constraint não é exposto
        assertFalse(response.getBody().getMessage().contains("uk_autorizacao_empresa_particao"));
    }

    @Test
    @DisplayName("StaleStateException → 409 Conflict (estado obsoleto)")
    void staleStateException_Retorna409() {
        ResponseEntity<LayoutErrosApiResponse> response = handler.conflitoEstadoObsoleto(req());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Conflito de concorrencia: a autorizacao foi modificada ou removida por outra requisicao",
                response.getBody().getError());
    }

    @Test
    @DisplayName("CannotAcquireLockException → 409 Conflict (linha movida de partição por transação concorrente)")
    void cannotAcquireLock_Retorna409() {
        // Forma que o conflito assume ao mover linha entre partições (SQLSTATE 40001); sem
        // tratamento explícito cairia no catch-all e viraria 500.
        CannotAcquireLockException ex = new CannotAcquireLockException(
                "tuple to be locked was already moved to another partition due to concurrent update");

        ResponseEntity<LayoutErrosApiResponse> response = handler.conflitoConcorrencia(ex, req());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().getMessage().contains("CannotAcquireLockException"));
        assertFalse(response.getBody().getError().contains("partition"));
    }

    @Test
    @DisplayName("Respostas de concorrência não expõem stack trace ou detalhes técnicos")
    void resposta_SanitizadaSemDetalhes() {
        OptimisticLockException ex = new OptimisticLockException("Detalhes técnicos que não devem ser expostos");

        ResponseEntity<LayoutErrosApiResponse> response = handler.conflitoLockOtimista(ex, req());

        String error = response.getBody().getError();
        String message = response.getBody().getMessage();

        assertFalse(error.contains("OptimisticLockException"));
        assertFalse(message.contains("OptimisticLockException"));
        assertFalse(error.contains("Detalhes técnicos"));
        assertFalse(message.contains("Detalhes técnicos"));
    }

    @Test
    @DisplayName("NoResourceFoundException → 404 (caminho desconhecido não cai no catch-all de 500)")
    void recursoEstaticoNaoEncontrado404() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/swagger-ui/index.html", null);

        ResponseEntity<LayoutErrosApiResponse> resp = handler.recursoEstaticoNaoEncontrado(ex, req());

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }
}
