package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.enums.AcaoDecisao;
import br.com.srportto.contratocommand.domain.enums.MotivoStatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.event.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.exception.ApplicationException;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.domain.service.decisao.DecisaoValidator;
import br.com.srportto.contratocommand.domain.utilities.ReversibleUUIDv7;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final ExpurgoAutorizacaoService expurgoAutorizacaoService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Autorizacao execute(DecidirAutorizacaoCommand command) {
        log.info("Iniciando decisão '{}' sobre autorização {}", command.acao(), command.idAutorizacao());

        var idAutorizacaoStr = command.idAutorizacao();
        var idParticaoAutorizacao = ReversibleUUIDv7.extract(UUID.fromString(idAutorizacaoStr));

        var autorizacao = obterAutorizacaoPorIdEParticao(idAutorizacaoStr, idParticaoAutorizacao);

        var statusAtual = StatusAutorizacao.obterStatusEnumPorIdStatus(autorizacao.getStatus());
        var comandoValidado = command.comAutorizacaoCarregada(autorizacao.getTipoProduto(), statusAtual);
        decisaoValidator.validar(comandoValidado);

        var acao = AcaoDecisao.obterAcaoDecisaoEnumPorNome(command.acao());
        aplicarDecisao(autorizacao, acao);

        var dataHoraDecisao = LocalDateTime.now();
        autorizacao.setDataHoraUltimaAtualizacao(dataHoraDecisao);

        var statusResultante = StatusAutorizacao.obterStatusEnumPorIdStatus(autorizacao.getStatus());
        var autorizacaoDecidida = statusResultante == StatusAutorizacao.REJEITADA
                ? expurgoAutorizacaoService.transferirParaExpurgo(autorizacao, dataHoraDecisao.toLocalDate())
                : repository.save(autorizacao);

        eventPublisher.publishEvent(new AutorizacaoPersistidaEvent(autorizacaoDecidida));

        return autorizacaoDecidida;
    }

    private void aplicarDecisao(Autorizacao autorizacao, AcaoDecisao acao) {
        switch (acao) {
            case APROVAR -> {
                autorizacao.setStatus((int) StatusAutorizacao.ATIVA.getStatusAutorizacao());
                autorizacao.setMotivoStatus(MotivoStatusAutorizacao.AUTORIZACAO_ACEITA_POR_TODOS.name());
            }
            case REJEITAR -> {
                autorizacao.setStatus((int) StatusAutorizacao.REJEITADA.getStatusAutorizacao());
                autorizacao.setMotivoStatus(MotivoStatusAutorizacao.REJEITADA_PAGADOR.name());
            }
            case EXPIRAR -> {
                autorizacao.setStatus((int) StatusAutorizacao.REJEITADA.getStatusAutorizacao());
                autorizacao.setMotivoStatus(MotivoStatusAutorizacao.REJEITADA_SISTEMA_TIMEOUT_J1.name());
            }
        }
    }

    private Autorizacao obterAutorizacaoPorIdEParticao(String idAutorizacao, int idParticaoAutorizacao) {
        try {
            var idAutorizacaoUuid = UUID.fromString(idAutorizacao);
            return repository.findByIdAutorizacaoAndParticao(idAutorizacaoUuid, idParticaoAutorizacao)
                    .orElseThrow(() -> new BusinessException("Autorização não encontrada com ID: " + idAutorizacao));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationException(
                    "Falha ao obter autorização " + idAutorizacao + " na partição " + idParticaoAutorizacao, e);
        }
    }
}
