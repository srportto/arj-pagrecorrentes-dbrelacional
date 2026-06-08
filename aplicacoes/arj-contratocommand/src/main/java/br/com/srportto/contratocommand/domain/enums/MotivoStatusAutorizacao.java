package br.com.srportto.contratocommand.domain.enums;

public enum MotivoStatusAutorizacao {
    RECEPCAO_SPI_J1(1L, "Recepcao de PAIN.009 , jornada 1"),
    LEITURA_QRC_J2(2L, "Leitura do QR Code na jornada 2"),
    LEITURA_QRC_J3(3L, "Leitura do QR Code na jornada 3"),
    LEITURA_QRC_J4(4L, "Leitura do QR Code na jornada 4"),
    PENDENTE_CONFIRMACAO_RECEBEDOR(5L,
            "Pagador ja aceitou a autorização, mas o recebedor ainda não confirmou a aceitação da autorização"),
    PENDENTE_APROVACAO_PAGADOR(6L, "Cliente pagador ainda nao aprovou autorização, valido apenas para jornada 1"),
    REJEITADA_PAGADOR(7L, "Cliente pagador rejeitou autorização advinda de jornada 1"),
    REJEITADA_PSP_PAGADOR_001(8L, "dados invalidos ou inconsistentes"),
    REJEITADA_PSP_PAGADOR_002(9L, "Conta nao aceita debitos"),
    REJEITADA_PSP_PAGADOR_003(10L, "pagador com conta bloqueada"),
    REJEITADA_PSP_PAGADOR_004(11L, "Estrutura QR Code invalida"),
    REJEITADA_PSP_PAGADOR_005(12L, "Recebedor bloqueado"),
    REJEITADA_PSP_PAGADOR_006(13L, "Recebedor fraudulento"),
    REJEITADA_PSP_PAGADOR_007(14L, "RN/RR invalido"),
    REJEITADA_PSP_PAGADOR_008(15L, "PSP recebedor excedeu limite tempo resposta(time-out)"),
    REJEITADA_PSP_PAGADOR_009(16L, "Erros operacionais do PSP pagador"),
    AUTORIZACAO_ACEITA_POR_TODOS(17L,
            "Autorização foi aceita por todos os envolvidos via troca de mensagens entre os PSPs e o cliente pagador, ou seja, autorização apta a ser ativada"),
    CANCELADA_CLIENTE_PAGADOR(18L,
            "Em algum canal de atendimento, o cliente pagador solicitou ou fez o cancelamento da autorização"),
    CANCELADA_PSP_RECEBEDOR(19L, "Pedido de cancelamento da autorização feito pelo PSP recebedor via mensageria SPI"),
    EXPIRADA_01(20L,
            "Autorizaco expirou por limite de tempo para o cliente pagador aceitar ou rejeitar a autorização, ou seja, expirada por falta de resposta do cliente pagador"),
    FINALIZADA_01(21L,
            "Autorizacao chegou ao fim do seu ciclo de vida, ou seja, autorizacao ativa chegou ao fim do seu prazo de vigencia");

    private long codigoMotivo;
    private String descricao;

    MotivoStatusAutorizacao(Long codigoMotivo, String descricao) {
        this.codigoMotivo = codigoMotivo;
        this.descricao = descricao;
    }

    public long getCodigoMotivo() {
        return this.codigoMotivo;
    }

    public String getDescricao() {
        return this.descricao;
    }

    public static MotivoStatusAutorizacao obterMotivoStatusEnumPorIdMotivo(long codigoMotivo) {
        for (MotivoStatusAutorizacao motivoStatusEnum : MotivoStatusAutorizacao.values()) {
            if (motivoStatusEnum.getCodigoMotivo() == codigoMotivo) {
                return motivoStatusEnum;
            }
        }
        throw new IllegalArgumentException(
                String.format("Motivo de status de autorização %i não conhecido ", codigoMotivo));
    }

}
