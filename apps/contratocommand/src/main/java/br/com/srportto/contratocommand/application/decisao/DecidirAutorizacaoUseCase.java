package br.com.srportto.contratocommand.application.decisao;

import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.domain.service.decisao.DecisaoValidator;
import br.com.srportto.contratocommand.application.ExpurgoAutorizacaoService;
import br.com.srportto.contratocommand.domain.event.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.enums.AcaoDecisao;
import br.com.srportto.contratocommand.domain.enums.MotivoStatusAutorizacao;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.domain.exception.ApplicationException;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
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
public class DecidirAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(DecidirAutorizacaoUseCase.class);

    private final AutorizacaoRepository repository;
    private final DecisaoValidator decisaoValidator;
    private final ExpurgoAutorizacaoService expurgoAutorizacaoService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AutorizacaoCompletaResponseDto execute(DecisaoContext context) {
        log.info("Iniciando decisão '{}' sobre autorização {}", context.dados().acao(), context.idAutorizacao());

        var idAutorizacaoStr = context.idAutorizacao();
        var idParticaoAutorizacao = ReversibleUUIDv7.extract(UUID.fromString(idAutorizacaoStr));

        var autorizacao = obterAutorizacaoPorIdEParticao(idAutorizacaoStr, idParticaoAutorizacao);

        var statusAtual = StatusAutorizacao.obterStatusEnumPorIdStatus(autorizacao.getStatus());
        var contextoValidado = context.comAutorizacaoCarregada(autorizacao.getTipoProduto(), statusAtual);
        decisaoValidator.validar(contextoValidado);

        var acao = AcaoDecisao.obterAcaoDecisaoEnumPorNome(context.dados().acao());
        aplicarDecisao(autorizacao, acao);

        var dataHoraDecisao = LocalDateTime.now();
        autorizacao.setDataHoraUltimaAtualizacao(dataHoraDecisao);

        var statusResultante = StatusAutorizacao.obterStatusEnumPorIdStatus(autorizacao.getStatus());
        var autorizacaoDecidida = statusResultante == StatusAutorizacao.REJEITADA
                ? expurgoAutorizacaoService.transferirParaExpurgo(autorizacao, dataHoraDecisao.toLocalDate())
                : repository.save(autorizacao);

        eventPublisher.publishEvent(new AutorizacaoPersistidaEvent(autorizacaoDecidida));

        return AutorizacaoCompletaResponseDto.from(autorizacaoDecidida);
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
