package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.event.AutorizacaoPersistidaEvent;
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

/** Caso de uso único de cancelamento, compartilhado por todos os produtos: marca status, registra dados e transfere a autorização para a partição de expurgo. */
@Service
@AllArgsConstructor
public class CancelarAutorizacaoService implements CancelarAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelarAutorizacaoService.class);

    private final AutorizacaoRepository repository;
    private final CancelamentoValidator cancelamentoValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final CarregadorAutorizacao carregadorAutorizacao;

    @Override
    @Transactional
    public Autorizacao execute(CancelarAutorizacaoCommand command) {
        log.info("Iniciando cancelamento de autorização {}", command.idAutorizacao().valor());

        var autorizacao = carregadorAutorizacao.carregar(command.idAutorizacao());

        var statusAtual = StatusAutorizacao.obterStatusEnumPorIdStatus(autorizacao.getStatus());
        var comandoValidado = command.comAutorizacaoCarregada(autorizacao.getTipoProduto(), statusAtual);
        cancelamentoValidator.validar(comandoValidado);

        var dadosCancelamento = new Cancelamento();

        var dataHoraCancelamento = LocalDateTime.now();
        dadosCancelamento.setDataHoraCancelamento(dataHoraCancelamento);
        dadosCancelamento.setCodigoCanalCancelamento(command.codigoCanalCancelamento());
        dadosCancelamento.setIdPessoaCancelamento(command.idPessoaCancelamento());

        if (command.motivoCancelamento() != null) {
            dadosCancelamento.setMotivoCancelamento(command.motivoCancelamento());
        }

        autorizacao.cancelar(dadosCancelamento);

        var dataCancelamento = dataHoraCancelamento.toLocalDate();
        var autorizacaoCanceladaEmNovaParticao = repository.transferirParaExpurgo(autorizacao, dataCancelamento);

        eventPublisher.publishEvent(new AutorizacaoPersistidaEvent(autorizacaoCanceladaEmNovaParticao));

        return autorizacaoCanceladaEmNovaParticao;
    }
}
