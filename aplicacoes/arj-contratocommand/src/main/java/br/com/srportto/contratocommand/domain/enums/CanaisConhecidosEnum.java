package br.com.srportto.contratocommand.domain.enums;

public enum CanaisConhecidosEnum {
    MB1("MB1", "Mobile - celular android"),
    MB2("MB2", "Mobile - celular ios"),
    MB3("MB3", "Mobile - outros"),
    BK1("BK1", "Internet banking - WEB"),
    BK2("BK2", "internet banking - APP"),
    CX1("CX1", "Interno - Caixa"),
    CX2("CX2", "Interno - Caixa 36h"),
    IN1("IN1", "backoffice - interno"),
    IN2("IN2", "bankPhone - interno");

    private String codigoCanal;
    private String descricao;

    CanaisConhecidosEnum(String codigoCanal, String descricao) {
        this.codigoCanal = codigoCanal;
        this.descricao = descricao;
    }

    public String getCodigoCanal() {
        return this.codigoCanal;
    }

    public String getDescricao() {
        return this.descricao;
    }

    public static CanaisConhecidosEnum obterCanalEnumPorIdCanal(String codigoCanal) {
        for (CanaisConhecidosEnum canalEnum : CanaisConhecidosEnum.values()) {
            if (canalEnum.getCodigoCanal().equalsIgnoreCase(codigoCanal)) {
                return canalEnum;
            }
        }
        throw new IllegalArgumentException(
                String.format("Canal %s não conhecido ", codigoCanal));
    }
}
