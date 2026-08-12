package br.com.srportto.contratoquery.shared.interceptors.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import br.com.srportto.contratoquery.shared.exceptions.ApplicationException;
import br.com.srportto.contratoquery.shared.exceptions.BusinessException;
import br.com.srportto.contratoquery.shared.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

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
    @DisplayName("BusinessException → 422 com mensagem")
    void negocio422() {
        ResponseEntity<LayoutErrosApiResponse> resp =
                handler.erroNegocio(new BusinessException("regra violada"), req());

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, resp.getStatusCode());
        assertEquals("regra violada", resp.getBody().getMessage());
        assertEquals("/api/autorizacoes", resp.getBody().getPath());
        assertNotNull(resp.getBody().getTimestamp());
    }

    @Test
    @DisplayName("ApplicationException → 500 com mensagem genérica (sem detalhe técnico)")
    void aplicacao500() {
        ResponseEntity<LayoutErrosApiResponse> resp =
                handler.erroAplicacao(new ApplicationException("falha"), req());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        // Resposta não contém o getMessage() da exceção original (proteção contra vazamento de detalhe técnico)
        assertEquals("Consulte o suporte para mais informações", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("ResourceNotFoundException → 404")
    void naoEncontrado404() {
        ResponseEntity<LayoutErrosApiResponse> resp =
                handler.recursoNaoEncontrado(new ResourceNotFoundException("sumiu"), req());

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals("sumiu", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("MethodArgumentNotValidException → 422 com ocorrências por campo")
    void validacao422() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(
                new FieldError("obj", "idUnicoContaContratante", "é obrigatório")));

        ResponseEntity<LayoutErrosApiValidationsResponse> resp = handler.validation(ex, req());

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, resp.getStatusCode());
        assertEquals(1, resp.getBody().getOccurrences().size());
        assertEquals("idUnicoContaContratante", resp.getBody().getOccurrences().get(0).getFieldName());
        assertEquals("é obrigatório", resp.getBody().getOccurrences().get(0).getMessage());
    }

    @Test
    @DisplayName("Exception genérica não mapeada → 500 com mensagem genérica (sem detalhe técnico)")
    void erroNaoMapeado500() {
        ResponseEntity<LayoutErrosApiResponse> resp =
                handler.erroInesperado(new NullPointerException("npe inesperada"), req());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        // Resposta não contém o getMessage() da exceção original (proteção contra vazamento de detalhe técnico)
        assertEquals("Consulte o suporte para mais informações", resp.getBody().getMessage());
        assertEquals("/api/autorizacoes", resp.getBody().getPath());
        assertNotNull(resp.getBody().getTimestamp());
    }

    @Test
    @DisplayName("NoResourceFoundException → 404 (caminho desconhecido não cai no catch-all de 500)")
    void recursoEstaticoNaoEncontrado404() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/swagger-ui/index.html", null);

        ResponseEntity<LayoutErrosApiResponse> resp = handler.recursoEstaticoNaoEncontrado(ex, req());

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }
}
