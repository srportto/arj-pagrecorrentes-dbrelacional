package br.com.srportto.temporizaautorizacao.domain.port.in;

/** Porta de entrada: varredura atômica que move agendamentos vencidos para o stream de expirações. */
public interface VarrerAgendamentosVencidosUseCase {

    void varrer();

}
