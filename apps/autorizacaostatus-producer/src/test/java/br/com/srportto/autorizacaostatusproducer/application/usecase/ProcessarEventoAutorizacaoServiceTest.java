package br.com.srportto.autorizacaostatusproducer.application.usecase;

import br.com.srportto.autorizacaostatusproducer.domain.enums.TipoEventoAutorizacao;
import br.com.srportto.autorizacaostatusproducer.domain.exception.EventoAutorizacaoKafkaIndisponivelException;
import br.com.srportto.autorizacaostatusproducer.domain.model.EventoAutorizacao;
import br.com.srportto.autorizacaostatusproducer.domain.port.out.PublicadorEventoAutorizacao;
import br.com.srportto.autorizacaostatusproducer.domain.service.IdempotenciaKeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Após D1 (design.md), o caso de uso não desserializa/valida/converte mais — recebe o evento
 * já tipado. A cobertura de JSON malformado, campo obrigatório ausente etc. migrou para
 * {@link br.com.srportto.autorizacaostatusproducer.infrastructure.messaging.SqsEventoAutorizacaoListenerTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes do ProcessarEventoAutorizacaoService")
class ProcessarEventoAutorizacaoServiceTest {

    @Mock
    private PublicadorEventoAutorizacao publicador;

    private final IdempotenciaKeyGenerator keyGenerator = new IdempotenciaKeyGenerator();

    private ProcessarEventoAutorizacaoService service;

    private void inicializar() {
        service = new ProcessarEventoAutorizacaoService(keyGenerator, publicador);
    }

    private EventoAutorizacao eventoValido(UUID idAutorizacao, LocalDateTime dataHoraUltimaAtlz) {
        return new EventoAutorizacao(idAutorizacao, 950, LocalDate.of(2027, 1, 1), 1L, null, 4, null, null,
                LocalDateTime.of(2026, 7, 26, 10, 0), dataHoraUltimaAtlz, null, null, null, null, null, null,
                null, "canal", null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("deriva a key de idempotência a partir de idAutorizacao + dataHoraUltimaAtlz e publica pela porta")
    void processaComSucesso() {
        inicializar();
        UUID id = UUID.randomUUID();
        LocalDateTime dataHora = LocalDateTime.of(2026, 7, 26, 10, 0);
        EventoAutorizacao evento = eventoValido(id, dataHora);
        String keyEsperada = keyGenerator.gerar(id, dataHora);

        assertDoesNotThrow(() -> service.processar(evento, TipoEventoAutorizacao.ATIVACAO));

        verify(publicador).produzir(eq(keyEsperada), eq(evento), eq("ATIVACAO"));
    }

    @Test
    @DisplayName("a mesma transição de estado sempre deriva a mesma key (idempotência preservada)")
    void mesmaTransicaoDerivaMesmaKey() {
        inicializar();
        UUID id = UUID.randomUUID();
        LocalDateTime dataHora = LocalDateTime.of(2026, 7, 26, 10, 0);
        EventoAutorizacao evento = eventoValido(id, dataHora);

        service.processar(evento, TipoEventoAutorizacao.ATIVACAO);
        service.processar(evento, TipoEventoAutorizacao.ATIVACAO);

        String key = keyGenerator.gerar(id, dataHora);
        verify(publicador, org.mockito.Mockito.times(2)).produzir(eq(key), eq(evento), eq("ATIVACAO"));
    }

    @Test
    @DisplayName("falha do Kafka propaga sem tratamento (retryable) — quem classifica é o interceptor da fila")
    void falhaDoKafkaPropaga() {
        inicializar();
        EventoAutorizacao evento = eventoValido(UUID.randomUUID(), LocalDateTime.now());
        doThrow(new EventoAutorizacaoKafkaIndisponivelException("indisponível", new RuntimeException()))
                .when(publicador).produzir(any(), any(), any());

        assertThrows(EventoAutorizacaoKafkaIndisponivelException.class,
                () -> service.processar(evento, TipoEventoAutorizacao.ATIVACAO));
    }

}
