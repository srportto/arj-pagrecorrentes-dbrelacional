package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.domain.enums.AcaoDecisao;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.event.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.exception.ApplicationException;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.domain.service.decisao.DecisaoValidator;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso único de decisão sobre autorização em RECEBIDA: aprovar (-> ATIVA), rejeitar pelo
 * cliente (-> REJEITADA) ou expirar por timeout do sistema (-> REJEITADA). A revalidação de
 * status ocorre sempre sob transação, tornando a rota segura para chamada repetida por um
 * chamador at-least-once (ver {@code TransicaoValidaDecisao}).
 */
@Service
@AllArgsConstructor
public class DecidirAutorizacaoService implements DecidirAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(DecidirAutorizacaoService.class);

    private final AutorizacaoRepository repository;
    private final DecisaoValidator decisaoValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Autorizacao execute(DecidirAutorizacaoCommand command) {
        log.info("Iniciando decisão '{}' sobre autorização {}", command.acao(), command.idAutorizacao());

        var idAutorizacaoUuid = UUID.fromString(command.idAutorizacao());
        var autorizacao = obterAutorizacaoPorId(idAutorizacaoUuid);

        var statusAtual = StatusAutorizacao.obterStatusEnumPorIdStatus(autorizacao.getStatus());
        var comandoValidado = command.comAutorizacaoCarregada(autorizacao.getTipoProduto(), statusAtual);
        decisaoValidator.validar(comandoValidado);

        var acao = AcaoDecisao.obterAcaoDecisaoEnumPorNome(command.acao());
        aplicarDecisao(autorizacao, acao);

        var dataHoraDecisao = autorizacao.getDataHoraUltimaAtualizacao();

        var statusResultante = StatusAutorizacao.obterStatusEnumPorIdStatus(autorizacao.getStatus());
        var autorizacaoDecidida = statusResultante == StatusAutorizacao.REJEITADA
                ? repository.transferirParaExpurgo(autorizacao, dataHoraDecisao.toLocalDate())
                : repository.save(autorizacao);

        eventPublisher.publishEvent(new AutorizacaoPersistidaEvent(autorizacaoDecidida));

        return autorizacaoDecidida;
    }

    private void aplicarDecisao(Autorizacao autorizacao, AcaoDecisao acao) {
        switch (acao) {
            case APROVAR -> autorizacao.aprovar();
            case REJEITAR -> autorizacao.rejeitarPeloPagador();
            case EXPIRAR -> autorizacao.expirarJornada1();
        }
    }

    private Autorizacao obterAutorizacaoPorId(UUID idAutorizacao) {
        try {
            return repository.findById(idAutorizacao)
                    .orElseThrow(() -> new BusinessException("Autorização não encontrada com ID: " + idAutorizacao));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationException("Falha ao obter autorização " + idAutorizacao, e);
        }
    }
}
