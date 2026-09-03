package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.domain.exception.ApplicationException;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.model.AutorizacaoId;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

/**
 * Fonte única de carregamento da autorização por id nos três use cases de escrita (cancelar,
 * decidir, atualizar) — antes triplicado em cada um deles (design.md, D2).
 *
 * {@code ConcurrencyFailureException} (e subclasses) propaga sem ser reembalada: o
 * {@code ApiExceptionHandler} já a mapeia para 409 — capturá-la aqui e convertê-la em
 * {@code ApplicationException} anularia esse contrato (design.md, D3).
 */
@Component
public class CarregadorAutorizacao {

    private final AutorizacaoRepository repository;

    public CarregadorAutorizacao(AutorizacaoRepository repository) {
        this.repository = repository;
    }

    public Autorizacao carregar(AutorizacaoId idAutorizacao) {
        try {
            return repository.findById(idAutorizacao.valor())
                    .orElseThrow(() -> new BusinessException("Autorização não encontrada com ID: " + idAutorizacao.valor()));
        } catch (BusinessException | ConcurrencyFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationException("Falha ao obter autorização " + idAutorizacao.valor(), e);
        }
    }
}
