package br.com.srportto.contratocommand.application.cancelamento;

import br.com.srportto.contratocommand.application.AutorizacaoRepository;
import br.com.srportto.contratocommand.domain.entities.Autorizacao;
import br.com.srportto.contratocommand.domain.entities.Cancelamento;
import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.utilities.ControleExpurgoAutorizacao;
import br.com.srportto.contratocommand.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequest;
import br.com.srportto.contratocommand.shared.exceptions.ApplicationException;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Caso de uso único de cancelamento de autorização, compartilhado por todos os produtos.
 * Marca o status como cancelada, registra os dados de cancelamento e transfere a autorização
 * para a partição de expurgo via delete+insert, tudo dentro do mesmo limite transacional.
 */
@Service
@AllArgsConstructor
public class CancelarAutorizacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelarAutorizacaoUseCase.class);

    private final AutorizacaoRepository repository;
    private final CancelamentoValidator cancelamentoValidator;
    private final EntityManager entityManager;

    @Transactional
    public AutorizacaoCompletaResponseDto execute(CancelamentoContext context) {
        log.info("Iniciando cancelamento de autorização {}", context.idAutorizacao());

        var idAutorizacaoStr = context.idAutorizacao();

        var idParticaoAutorizacao = ReversibleUUIDv7.extract(UUID.fromString(idAutorizacaoStr));

        var autorizacao = obterAutorizacaoPorIdEParticao(idAutorizacaoStr, idParticaoAutorizacao);

        var contextoValidado = context.comProdutoAutorizacao(autorizacao.getTipoProduto());
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
        var particaoExpurgoWrite = ControleExpurgoAutorizacao.obterParticaoExpurgoWrite(dataCancelamento);

        var autorizacaoCanceladaEmNovaParticao = transferirParaNovaParticao(autorizacao, particaoExpurgoWrite);

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
            throw new ApplicationException(e.getMessage());
        }
    }

    private Autorizacao transferirParaNovaParticao(Autorizacao autorizacao, Integer novaParticao) {
        UUID idAutorizacaoUuid = autorizacao.getIdAutorizacao().getIdAutorizacao();
        Integer particaoAntiga = autorizacao.getIdAutorizacao().getIdParticaoConta();

        if (novaParticao.equals(particaoAntiga)) {
            return repository.save(autorizacao);
        }

        log.info("Transferindo autorização {} da partição {} para partição {}",
                idAutorizacaoUuid, particaoAntiga, novaParticao);

        repository.deleteById(autorizacao.getIdAutorizacao());

        // Dentro do mesmo persistence context (@Transactional no execute), o JPA não permite
        // alterar o @EmbeddedId de uma entidade gerenciada nem fazer merge de uma instância já
        // removida (ObjectDeletedException). O flush executa o DELETE imediatamente e o detach
        // desanexa a instância, permitindo reaproveitá-la como nova linha na partição de expurgo.
        repository.flush();
        entityManager.detach(autorizacao);

        autorizacao.getIdAutorizacao().setIdParticaoConta(novaParticao);
        return repository.save(autorizacao);
    }
}
