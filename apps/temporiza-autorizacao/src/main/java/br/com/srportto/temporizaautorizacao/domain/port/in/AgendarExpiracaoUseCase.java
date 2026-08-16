package br.com.srportto.temporizaautorizacao.domain.port.in;

import java.time.LocalDateTime;
import java.util.UUID;

/** Porta de entrada: agenda a expiração a partir do id e do instante de inclusão da autorização. */
public interface AgendarExpiracaoUseCase {

    void agendar(UUID idAutorizacao, LocalDateTime dataHoraInclusao);

}
