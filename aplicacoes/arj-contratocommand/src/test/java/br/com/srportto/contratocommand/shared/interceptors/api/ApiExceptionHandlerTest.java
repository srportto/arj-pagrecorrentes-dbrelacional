package br.com.srportto.contratocommand.shared.interceptors.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import br.com.srportto.contratocommand.shared.exceptions.ApplicationException;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
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
    @DisplayName("BusinessException → 422")
    void negocio422() {
        ResponseEntity<LayoutErrosApiResponse> resp =
                handler.erroNegociosResponseEntity(new BusinessException("regra"), req());

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, resp.getStatusCode());
        assertEquals("regra", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("ApplicationException → 500")
    void aplicacao500() {
        ResponseEntity<LayoutErrosApiResponse> resp =
                handler.erroNegociosResponseEntity(new ApplicationException("falha"), req());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals("falha", resp.getBody().getMessage());
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
}
