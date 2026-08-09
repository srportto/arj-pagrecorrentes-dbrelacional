package br.com.srportto.contratoquery.domain.enums;

import br.com.srportto.contratoquery.shared.exceptions.BusinessException;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public enum TipoJornadaAutorizacao {
    // Jornada desconhecida: usada apenas para ler registros persistidos antes da coluna
    // tipo_jornada existir (default 0 no banco).
    DESCONHECIDA(0L),
    SPI_J1(1L),
    QRC_J2(2L),
    QRC_J3(3L),
    QRC_J4(4L);

    private long codigoJornada;

    TipoJornadaAutorizacao(long codigoJornada) {
        this.codigoJornada = codigoJornada;
    }

    public long getCodigoJornada() {
        return this.codigoJornada;
    }

    public static TipoJornadaAutorizacao obterJornadaAutorizacaoEnumPorIdJornada(long codigoJornada) {
        for (TipoJornadaAutorizacao jornadaEnum : TipoJornadaAutorizacao.values()) {
            if (jornadaEnum.getCodigoJornada() == codigoJornada) {
                return jornadaEnum;
            }
        }
        throw new BusinessException(
                String.format("Jornada de autorização %d não conhecida", codigoJornada));
    }
}
