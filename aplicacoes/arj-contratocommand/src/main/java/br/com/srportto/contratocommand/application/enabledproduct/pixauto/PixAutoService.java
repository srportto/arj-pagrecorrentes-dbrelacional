package br.com.srportto.contratocommand.application.enabledproduct.pixauto;

import br.com.srportto.contratocommand.application.autorizacao.usecases.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.autorizacao.usecases.CriarAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.services.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.domain.services.cancelamento.CancelamentoService;
import br.com.srportto.contratocommand.domain.services.contratacao.ContratacaoService;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Strategy de PIX_AUTO: declara o produto suportado e delega aos casos de uso compartilhados.
 * Não há lógica de persistência/mapeamento aqui — apenas a identidade do produto.
 */
@Service
@AllArgsConstructor
public class PixAutoService implements ContratacaoService, CancelamentoService {

    private final CriarAutorizacaoUseCase criarAutorizacaoUseCase;
    private final CancelarAutorizacaoUseCase cancelarAutorizacaoUseCase;

    @Override
    public boolean validaContratacaoSuportada(CriarAutorizacaoRequest request) {
        return request.tipoProduto() != null && TipoProduto.PIX_AUTO.name().equalsIgnoreCase(request.tipoProduto());
    }

    @Override
    public AutorizacaoCompletaResponseDto criarAutorizacao(CriarAutorizacaoRequest request) {
        return criarAutorizacaoUseCase.execute(request);
    }

    @Override
    public boolean validaCancelamentoSuportado(CancelamentoContext context) {
        return context.tipoProduto() != null
                && TipoProduto.PIX_AUTO.name().equalsIgnoreCase(context.tipoProduto().name());
    }

    @Override
    public AutorizacaoCompletaResponseDto cancelarAutorizacao(CancelamentoContext context) {
        return cancelarAutorizacaoUseCase.execute(context);
    }
}
