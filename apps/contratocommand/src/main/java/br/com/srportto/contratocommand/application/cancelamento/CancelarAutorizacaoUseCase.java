package br.com.srportto.contratocommand.application.cancelamento;

import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.application.ExpurgoAutorizacaoService;
import br.com.srportto.contratocommand.domain.event.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.Cancelamento;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequest;
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

/** Caso de uso único de cancelamento, compartilhado por todos os produtos: marca status, registra dados e transfere a autorização para a partição de expurgo. */
@Service
@AllArgsConstructor
public class CancelarAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelarAutorizacaoUseCase.class);

    private final AutorizacaoRepository repository;
    private final CancelamentoValidator cancelamentoValidator;
    private final ExpurgoAutorizacaoService expurgoAutorizacaoService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AutorizacaoCompletaResponseDto execute(CancelamentoContext context) {
        log.info("Iniciando cancelamento de autorização {}", context.idAutorizacao());

        var idAutorizacaoStr = context.idAutorizacao();

        var idParticaoAutorizacao = ReversibleUUIDv7.extract(UUID.fromString(idAutorizacaoStr));

        var autorizacao = obterAutorizacaoPorIdEParticao(idAutorizacaoStr, idParticaoAutorizacao);

        var statusAtual = StatusAutorizacao.obterStatusEnumPorIdStatus(autorizacao.getStatus());
        var contextoValidado = context.comAutorizacaoCarregada(autorizacao.getTipoProduto(), statusAtual);
        cancelamentoValidator.validar(contextoValidado);

        CancelarAutorizacaoRequest dados = context.dados();

        autorizacao.setStatus((int) StatusAutorizacao.CANCELADA.getStatusAutorizacao());
        var dadosCancelamento = new Cancelamento();

        var dataHoraCancelamento = LocalDateTime.now();
        dadosCancelamento.setDataHoraCancelamento(dataHoraCancelamento);
        dadosCancelamento.setCodigoCanalCancelamento(dados.codigoCanalCancelamento());
        dadosCancelamento.setIdPessoaCancelamento(dados.idPessoaCancelamento());

        autorizacao.setDataHoraUltimaAtualizacao(dataHoraCancelamento);

        if (dados.motivoCancelamento() != null) {
            dadosCancelamento.setMotivoCancelamento(dados.motivoCancelamento());
        }

        autorizacao.setCancelamento(dadosCancelamento);

        var dataCancelamento = dataHoraCancelamento.toLocalDate();
        var autorizacaoCanceladaEmNovaParticao = expurgoAutorizacaoService.transferirParaExpurgo(autorizacao, dataCancelamento);

        eventPublisher.publishEvent(new AutorizacaoPersistidaEvent(autorizacaoCanceladaEmNovaParticao));

        return AutorizacaoCompletaResponseDto.from(autorizacaoCanceladaEmNovaParticao);
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
