package br.com.srportto.autorizacaostatusproducer.application.eventos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Testes do ProcessarEventoAutorizacaoUseCase")
class ProcessarEventoAutorizacaoUseCaseTest {

    private final ProcessarEventoAutorizacaoUseCase useCase = new ProcessarEventoAutorizacaoUseCase();

    @Test
    @DisplayName("processa com sucesso um evento JSON válido")
    void processaEventoValido() {
        String mensagem = "{\"id_autorizacao\":\"550e8400-e29b-41d4-a716-446655440000\","
                + "\"id_particao_conta\":950,\"status\":4}";

        assertDoesNotThrow(() -> useCase.processar(mensagem));
    }

    @Test
    @DisplayName("lança exceção para JSON malformado, para que a mensagem não seja confirmada")
    void naoProcessaJsonMalformado() {
        assertThrows(JacksonException.class, () -> useCase.processar("{isso nao e json"));
    }

}
