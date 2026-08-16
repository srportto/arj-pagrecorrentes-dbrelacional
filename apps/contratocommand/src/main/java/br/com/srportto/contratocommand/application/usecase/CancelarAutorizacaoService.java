package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.event.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.exception.ApplicationException;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.model.Cancelamento;
import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.domain.service.cancelamento.CancelamentoValidator;
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
public class CancelarAutorizacaoService implements CancelarAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelarAutorizacaoService.class);

    private final AutorizacaoRepository repository;
    private final CancelamentoValidator cancelamentoValidator;
    private final ExpurgoAutorizacaoService expurgoAutorizacaoService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Autorizacao execute(CancelarAutorizacaoCommand command) {
        log.info("Iniciando cancelamento de autorização {}", command.idAutorizacao());

        var idAutorizacaoUuid = UUID.fromString(command.idAutorizacao());
        var autorizacao = obterAutorizacaoPorId(idAutorizacaoUuid);

        var statusAtual = StatusAutorizacao.obterStatusEnumPorIdStatus(autorizacao.getStatus());
        var comandoValidado = command.comAutorizacaoCarregada(autorizacao.getTipoProduto(), statusAtual);
        cancelamentoValidator.validar(comandoValidado);

        autorizacao.setStatus((int) StatusAutorizacao.CANCELADA.getStatusAutorizacao());
        var dadosCancelamento = new Cancelamento();

        var dataHoraCancelamento = LocalDateTime.now();
        dadosCancelamento.setDataHoraCancelamento(dataHoraCancelamento);
        dadosCancelamento.setCodigoCanalCancelamento(command.codigoCanalCancelamento());
        dadosCancelamento.setIdPessoaCancelamento(command.idPessoaCancelamento());

        autorizacao.setDataHoraUltimaAtualizacao(dataHoraCancelamento);

        if (command.motivoCancelamento() != null) {
            dadosCancelamento.setMotivoCancelamento(command.motivoCancelamento());
        }

        autorizacao.setCancelamento(dadosCancelamento);

        var dataCancelamento = dataHoraCancelamento.toLocalDate();
        var autorizacaoCanceladaEmNovaParticao = expurgoAutorizacaoService.transferirParaExpurgo(autorizacao, dataCancelamento);

        eventPublisher.publishEvent(new AutorizacaoPersistidaEvent(autorizacaoCanceladaEmNovaParticao));

        return autorizacaoCanceladaEmNovaParticao;
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
