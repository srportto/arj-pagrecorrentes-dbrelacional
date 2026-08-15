package br.com.srportto.contratocommand.application;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** Transferência de autorizações em estado terminal para a partição de expurgo, compartilhada por todo use case que leva uma autorização a CANCELADA, REJEITADA, EXPIRADA ou FINALIZADA. */
@Service
@AllArgsConstructor
public class ExpurgoAutorizacaoService {

    private final AutorizacaoRepository repository;

    public Autorizacao transferirParaExpurgo(Autorizacao autorizacao, LocalDate dataReferenciaExpurgo) {
        return repository.transferirParaExpurgo(autorizacao, dataReferenciaExpurgo);
    }
}
