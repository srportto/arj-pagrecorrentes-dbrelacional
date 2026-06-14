package br.com.srportto.contratoquery.domain.enums;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public enum TipoJornadaAutorizacao {
    SPI_J1(1L, "Recepcao de PAIN.009 do SPI, jornada 1"),
    QRC_J2(2L, "Leitura do QR Code, em jornada 2"),
    QRC_J3(3L, "Leitura do QR Code, em jornada 3"),
    QRC_J4(4L, "Leitura do QR Code,em jornada 4");


    private long codigoJornada;
    private String descricao;

    TipoJornadaAutorizacao(Long codigoJornada, String descricao) {
        this.codigoJornada = codigoJornada;
        this.descricao = descricao;
    }

    public long getCodigoJornada() {
        return this.codigoJornada;
    }

    public String getDescricao() {
        return this.descricao;
    }

    public static TipoJornadaAutorizacao obterJornadaAutorizacaoEnumPorIdJornada(long codigoJornada) {
        for (TipoJornadaAutorizacao motivoStatusEnum : TipoJornadaAutorizacao.values()) {
            if (motivoStatusEnum.getCodigoJornada() == codigoJornada) {
                return motivoStatusEnum;
            }
        }
        throw new IllegalArgumentException(
                String.format("jornada de status de autorização %i não conhecida ", codigoJornada));
    }

}
