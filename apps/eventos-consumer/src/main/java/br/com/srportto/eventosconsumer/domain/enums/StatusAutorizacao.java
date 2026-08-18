package br.com.srportto.eventosconsumer.domain.enums;

import br.com.srportto.eventosconsumer.domain.exception.EventoAutorizacaoInvalidoException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Máquina de estados da autorização — regra de negócio pura desta app (ver CLAUDE.md). */
public enum StatusAutorizacao {
    RECEBIDA(1L),
    PENDENTE_ACEITE(2L),
    EM_PROCESSO_ATIVACAO(3L),
    ATIVA(4L),
    CANCELADA(5L),
    REJEITADA(6L),
    EXPIRADA(7L),
    FINALIZADA(8L);

    private static final Map<StatusAutorizacao, Set<StatusAutorizacao>> TRANSICOES = new EnumMap<>(StatusAutorizacao.class);

    static {
        TRANSICOES.put(RECEBIDA, EnumSet.of(PENDENTE_ACEITE, EM_PROCESSO_ATIVACAO, REJEITADA));
        TRANSICOES.put(PENDENTE_ACEITE, EnumSet.of(EM_PROCESSO_ATIVACAO, REJEITADA, EXPIRADA));
        TRANSICOES.put(EM_PROCESSO_ATIVACAO, EnumSet.of(ATIVA, REJEITADA, EXPIRADA));
        TRANSICOES.put(ATIVA, EnumSet.of(CANCELADA, FINALIZADA, REJEITADA));
        TRANSICOES.put(CANCELADA, EnumSet.noneOf(StatusAutorizacao.class));
        TRANSICOES.put(REJEITADA, EnumSet.noneOf(StatusAutorizacao.class));
        TRANSICOES.put(EXPIRADA, EnumSet.noneOf(StatusAutorizacao.class));
        TRANSICOES.put(FINALIZADA, EnumSet.noneOf(StatusAutorizacao.class));
    }

    private final long statusAutorizacao;

    StatusAutorizacao(long statusAutorizacao) {
        this.statusAutorizacao = statusAutorizacao;
    }

    public long getStatusAutorizacao() {
        return this.statusAutorizacao;
    }

    /** Consulta o grafo de transições da máquina de estados da autorização. */
    public boolean podeTransicionarPara(StatusAutorizacao destino) {
        return TRANSICOES.get(this).contains(destino);
    }

    public static StatusAutorizacao obterStatusEnumPorIdStatus(long statusAutorizacaoId) {
        for (StatusAutorizacao statusEnum : StatusAutorizacao.values()) {
            if (statusEnum.getStatusAutorizacao() == statusAutorizacaoId) {
                return statusEnum;
            }
        }
        throw new EventoAutorizacaoInvalidoException(
                String.format("Status de autorização %d não conhecido ", statusAutorizacaoId));
    }
}
