package br.com.srportto.contratocommand.application.enabledproduct.ddaauto.usecases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import br.com.srportto.contratocommand.application.defaultservice.cancelamento.CancelamentoValidator;
import br.com.srportto.contratocommand.application.enabledproduct.ddaauto.DdaAutoRepository;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.Cancelamento;
import br.com.srportto.contratocommand.domain.utilities.ControleExpurgoAutorizacao;
import br.com.srportto.contratocommand.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class CancelarDdaAutoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelarDdaAutoUseCase.class);

    private final DdaAutoRepository repository;
    private final CancelamentoValidator cancelamentoValidator;


    public AutorizacaoCompletaResponseDto execute(CancelarAutorizacaoRequestDto request) {
        log.info("Iniciando cancelamento de autorização DDA {}", request.getIdAutorizacao());

        var idAutorizacaoStr = request.getIdAutorizacao();

        var idParticaoAutorizacao = ReversibleUUIDv7.extract(UUID.fromString(idAutorizacaoStr));

        var autorizacao = obterAutorizacaoPorIdEParticao(idAutorizacaoStr, idParticaoAutorizacao);

        var idProdutoAutorizacao = autorizacao.getTipoProduto();
        request.setTipoProdutoDoIdAutorizacao(idProdutoAutorizacao);

        cancelamentoValidator.validar(request);

        autorizacao.setStatus(5); // cancelada
        var dadosCancelamento = new Cancelamento();

        var dataHoraCancelamento = LocalDateTime.now();
        dadosCancelamento.setDataHoraCancelamento(dataHoraCancelamento);
        dadosCancelamento.setCodigoCanalCancelamento(request.getCodigoCanalCancelamento());
        dadosCancelamento.setIdPessoaCancelamento(request.getIdPessoaCancelamento());

        autorizacao.setDataHoraUltimaAtualizacao(dataHoraCancelamento);

        if (request.getMotivoCancelamento() != null) {
            dadosCancelamento.setMotivoCancelamento(request.getMotivoCancelamento());
        }

        autorizacao.setCancelamento(dadosCancelamento);

        // Captura partição de expurgo do momento do cancelamento
        var dataCancelamento = dataHoraCancelamento.toLocalDate();
        var particaoExpurgoWrite = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(dataCancelamento);

        // Como a chave composta foi modificada, precisamos fazer delete+insert
        var autorizacaoCanceladaEmNovaParticao = transferirParaNovaParticao(autorizacao, particaoExpurgoWrite);

        return AutorizacaoCompletaResponseDto.from(autorizacaoCanceladaEmNovaParticao);
    }

    private Autorizacao obterAutorizacaoPorIdEParticao(String idAutorizacao, int idParticaoAutorizacao) {
        try {
            var idAutorizacaoUuid = UUID.fromString(idAutorizacao);
            return repository.findByIdAutorizacaoAndParticao(idAutorizacaoUuid, idParticaoAutorizacao)
                    .orElseThrow(() -> new BusinessException("Autorização DDA não encontrada com ID: " + idAutorizacao));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationContextException(e.getMessage());
        }
    }

    @Transactional
    private Autorizacao transferirParaNovaParticao(Autorizacao autorizacao, Integer novaParticao) {
        UUID idAutorizacaoUuid = autorizacao.getIdAutorizacao().getIdAutorizacao();
        Integer particaoAntiga = autorizacao.getIdAutorizacao().getIdParticaoConta();

        // Se a partição não mudou, apenas persistir normalmente
        if (novaParticao.equals(particaoAntiga)) {
            return repository.save(autorizacao);
        }

        log.info("Transferindo autorização DDA{} da partição {} para partição {}",
                idAutorizacaoUuid, particaoAntiga, novaParticao);

        // Delete do banco com a chave antiga
        repository.deleteById(autorizacao.getIdAutorizacao());

        // Altera a partição e salva novamente na nova partição
        autorizacao.getIdAutorizacao().setIdParticaoConta(novaParticao);
        return repository.save(autorizacao);
    }

}