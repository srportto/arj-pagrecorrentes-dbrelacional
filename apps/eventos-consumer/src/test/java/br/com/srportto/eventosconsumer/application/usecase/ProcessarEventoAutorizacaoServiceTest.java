package br.com.srportto.eventosconsumer.application.usecase;

import br.com.srportto.eventos.autorizacao.EventoAutorizacao;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("Testes do ProcessarEventoAutorizacaoService")
class ProcessarEventoAutorizacaoServiceTest {

    private final ProcessarEventoAutorizacaoService useCase = new ProcessarEventoAutorizacaoService();

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(ProcessarEventoAutorizacaoService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(ProcessarEventoAutorizacaoService.class);
        logger.detachAppender(logAppender);
    }

    private EventoAutorizacao eventoComDadosSensiveis(UUID idAutorizacao) {
        return EventoAutorizacao.newBuilder()
                .setIdAutorizacao(idAutorizacao)
                .setIdParticaoConta(950)
                .setDataFimVigencia(LocalDate.now())
                .setTipoProduto(1L)
                .setStatus(4)
                .setDataHoraInclusao(LocalDateTime.now())
                .setDataHoraUltimaAtlz(LocalDateTime.now())
                .setCodigoCanalContratacao("canal")
                .setValor(new java.math.BigDecimal("1234.56"))
                .setDescricao("descricao sigilosa do contrato")
                .setIdPessoaPagadora(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .setIdPessoaDevedora(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .setIdPessoaRecebedora(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .build();
    }

    @Test
    @DisplayName("processa (loga) um evento válido sem lançar exceção")
    void processaEventoValido() {
        UUID idAutorizacao = UUID.randomUUID();
        EventoAutorizacao evento = eventoComDadosSensiveis(idAutorizacao);

        assertDoesNotThrow(() -> useCase.processar(evento));
    }

    @Test
    @DisplayName("log do consumo cita idAutorizacao e tipoEvento, mas nunca o record inteiro nem campos sensíveis")
    void logNaoVazaDadoSensivel() {
        UUID idAutorizacao = UUID.randomUUID();
        EventoAutorizacao evento = eventoComDadosSensiveis(idAutorizacao);

        useCase.processar(evento);

        assertThat(logAppender.list).hasSize(1);
        String mensagemFormatada = logAppender.list.get(0).getFormattedMessage();

        assertThat(mensagemFormatada)
                .contains(idAutorizacao.toString())
                .contains("ATIVACAO")
                .doesNotContain("1234.56")
                .doesNotContain("descricao sigilosa do contrato")
                .doesNotContain("11111111-1111-1111-1111-111111111111")
                .doesNotContain("22222222-2222-2222-2222-222222222222")
                .doesNotContain("33333333-3333-3333-3333-333333333333");
    }

}
