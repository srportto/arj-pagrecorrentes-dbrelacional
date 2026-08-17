package br.com.srportto.eventosconsumer.application.usecase;

import br.com.srportto.eventosconsumer.domain.model.EventoAutorizacaoConsumido;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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

    @Test
    @DisplayName("processa (loga) um evento válido sem lançar exceção")
    void processaEventoValido() {
        UUID idAutorizacao = UUID.randomUUID();
        EventoAutorizacaoConsumido evento = new EventoAutorizacaoConsumido(idAutorizacao, 4);

        assertDoesNotThrow(() -> useCase.processar(evento));
    }

    @Test
    @DisplayName("log do consumo cita idAutorizacao e tipoEvento — o modelo de domínio não carrega "
            + "campo sensível para vazar (garantia estrutural: EventoAutorizacaoConsumido só tem "
            + "idAutorizacao/status, o PII fica confinado ao Avro em infrastructure/messaging)")
    void logCitaIdAutorizacaoETipoEvento() {
        UUID idAutorizacao = UUID.randomUUID();
        EventoAutorizacaoConsumido evento = new EventoAutorizacaoConsumido(idAutorizacao, 4);

        useCase.processar(evento);

        assertThat(logAppender.list).hasSize(1);
        String mensagemFormatada = logAppender.list.get(0).getFormattedMessage();

        assertThat(mensagemFormatada)
                .contains(idAutorizacao.toString())
                .contains("ATIVACAO");
    }

}
