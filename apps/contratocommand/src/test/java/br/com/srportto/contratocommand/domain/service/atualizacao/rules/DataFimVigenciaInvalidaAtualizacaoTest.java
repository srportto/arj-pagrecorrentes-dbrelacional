package br.com.srportto.contratocommand.domain.service.atualizacao.rules;

import br.com.srportto.contratocommand.application.TestFixtures;
import br.com.srportto.contratocommand.domain.model.AutorizacaoId;
import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes da regra DataFimVigenciaInvalidaAtualizacao")
class DataFimVigenciaInvalidaAtualizacaoTest {

    private final DataFimVigenciaInvalidaAtualizacao regra = new DataFimVigenciaInvalidaAtualizacao();

    @Test
    @DisplayName("aceita sempre retorna true")
    void aceitaTrue() {
        assertTrue(regra.aceita(TestFixtures.atualizarContext("11111111-1111-1111-1111-111111111111", TipoProduto.PIX_AUTO)));
    }

    @Test
    @DisplayName("validar lança BusinessException quando a data está no passado")
    void dataPassadoLanca() {
        AtualizarDadosRecorrenciaCommand context = new AtualizarDadosRecorrenciaCommand(
                AutorizacaoId.de("11111111-1111-1111-1111-111111111111"), TipoProduto.PIX_AUTO, TipoProduto.PIX_AUTO, StatusAutorizacao.ATIVA,
                null, LocalDate.now().minusDays(1), null, null, "C1", null);
        assertThrows(BusinessException.class, () -> regra.validar(context));
    }

    @Test
    @DisplayName("validar aceita data futura e campo ausente (não altera)")
    void dataFuturaOuAusenteOk() {
        AtualizarDadosRecorrenciaCommand comDataFutura = new AtualizarDadosRecorrenciaCommand(
                AutorizacaoId.de("11111111-1111-1111-1111-111111111111"), TipoProduto.PIX_AUTO, TipoProduto.PIX_AUTO, StatusAutorizacao.ATIVA,
                null, LocalDate.now().plusDays(10), null, null, "C1", null);
        assertDoesNotThrow(() -> regra.validar(comDataFutura));

        AtualizarDadosRecorrenciaCommand semData = new AtualizarDadosRecorrenciaCommand(
                AutorizacaoId.de("11111111-1111-1111-1111-111111111111"), TipoProduto.PIX_AUTO, TipoProduto.PIX_AUTO, StatusAutorizacao.ATIVA,
                null, null, null, null, "C1", null);
        assertDoesNotThrow(() -> regra.validar(semData));
    }
}
