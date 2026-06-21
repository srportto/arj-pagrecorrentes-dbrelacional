package br.com.srportto.contratocommand.application.enabledproduct.ddaauto;

import br.com.srportto.contratocommand.application.autorizacao.usecases.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.autorizacao.usecases.CriarAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.defaultservice.cancelamento.CancelamentoService;
import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoService;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Strategy de DDA_AUTO: declara o produto suportado e delega aos casos de uso compartilhados.
 * Não há lógica de persistência/mapeamento aqui — apenas a identidade do produto.
 */
@Service
@AllArgsConstructor
public class DdaAutoService implements ContratacaoService, CancelamentoService {

    private final CriarAutorizacaoUseCase criarAutorizacaoUseCase;
    private final CancelarAutorizacaoUseCase cancelarAutorizacaoUseCase;

    @Override
    public boolean validaContratacaoSuportada(CriarAutorizacaoRequest request) {
        return request.tipoProduto() != null && TipoProduto.DDA_AUTO.name().equalsIgnoreCase(request.tipoProduto());
    }

    @Override
    public AutorizacaoCompletaResponseDto criarAutorizacao(CriarAutorizacaoRequest request) {
        return criarAutorizacaoUseCase.execute(request);
    }

    @Override
    public boolean validaCancelamentoSuportado(CancelarAutorizacaoRequestDto request) {
        return request.getProdutoHeaderRequest() != null
                && TipoProduto.DDA_AUTO.name().equalsIgnoreCase(request.getProdutoHeaderRequest().name());
    }

    @Override
    public AutorizacaoCompletaResponseDto cancelarAutorizacao(CancelarAutorizacaoRequestDto request) {
        return cancelarAutorizacaoUseCase.execute(request);
    }
}
