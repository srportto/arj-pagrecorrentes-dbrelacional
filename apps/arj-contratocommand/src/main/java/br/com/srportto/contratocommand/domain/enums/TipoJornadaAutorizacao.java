package br.com.srportto.contratocommand.domain.enums;

import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public enum TipoJornadaAutorizacao {
    // Jornada desconhecida: usada apenas para ler registros persistidos antes da coluna
    // tipo_jornada existir (default 0 no banco) - nunca é escrita na criação de uma autorização.
    DESCONHECIDA(0L, "Jornada desconhecida - registro anterior a persistencia da jornada"),
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
        for (TipoJornadaAutorizacao jornadaEnum : TipoJornadaAutorizacao.values()) {
            if (jornadaEnum.getCodigoJornada() == codigoJornada) {
                return jornadaEnum;
            }
        }
        throw new BusinessException(
                String.format("Jornada de autorização %d não conhecida", codigoJornada));
    }

    public static TipoJornadaAutorizacao obterJornadaAutorizacaoEnumPorNome(String nome) {
        for (TipoJornadaAutorizacao jornadaEnum : TipoJornadaAutorizacao.values()) {
            if (jornadaEnum.name().equalsIgnoreCase(nome)) {
                return jornadaEnum;
            }
        }
        throw new BusinessException(
                String.format("Jornada de autorização '%s' não conhecida", nome));
    }

}
