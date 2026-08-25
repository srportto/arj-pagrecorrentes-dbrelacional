package br.com.srportto.contratocommand.application.usecase;

import br.com.srportto.contratocommand.domain.enums.StatusAutorizacao;
import br.com.srportto.contratocommand.domain.event.AutorizacaoPersistidaEvent;
import br.com.srportto.contratocommand.domain.exception.ApplicationException;
import br.com.srportto.contratocommand.domain.exception.BusinessException;
import br.com.srportto.contratocommand.domain.model.Autorizacao;
import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaCommand;
import br.com.srportto.contratocommand.domain.port.in.AtualizarDadosRecorrenciaUseCase;
import br.com.srportto.contratocommand.domain.port.out.AutorizacaoRepository;
import br.com.srportto.contratocommand.domain.service.atualizacao.AtualizacaoValidator;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso único de atualização parcial de dados de uma autorização ATIVA: não transiciona
 * estado, só corrige/renegocia valorLimite, dataFimVigencia, indicadorUsoLimiteConta e
 * quantidadeDividasCiclo.
 */
@Service
@AllArgsConstructor
public class AtualizarDadosRecorrenciaService implements AtualizarDadosRecorrenciaUseCase {

    private static final Logger log = LoggerFactory.getLogger(AtualizarDadosRecorrenciaService.class);

    private final AutorizacaoRepository repository;
    private final AtualizacaoValidator atualizacaoValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Autorizacao execute(AtualizarDadosRecorrenciaCommand command) {
        log.info("Iniciando atualização de dados da recorrência da autorização {}", command.idAutorizacao());

        var idAutorizacaoUuid = UUID.fromString(command.idAutorizacao());
        var autorizacao = obterAutorizacaoPorId(idAutorizacaoUuid);

        var statusAtual = StatusAutorizacao.obterStatusEnumPorIdStatus(autorizacao.getStatus());
        var comandoValidado = command.comAutorizacaoCarregada(autorizacao.getTipoProduto(), statusAtual);
        atualizacaoValidator.validar(comandoValidado);

        autorizacao.atualizarDadosRecorrencia(command.valorLimite(), command.dataFimVigencia(),
                command.indicadorUsoLimiteConta(), command.quantidadeDividasCiclo());

        var autorizacaoAtualizada = repository.save(autorizacao);

        eventPublisher.publishEvent(new AutorizacaoPersistidaEvent(autorizacaoAtualizada));

        return autorizacaoAtualizada;
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
