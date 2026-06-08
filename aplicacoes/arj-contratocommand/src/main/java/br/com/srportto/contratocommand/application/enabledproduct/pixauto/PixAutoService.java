package br.com.srportto.contratocommand.application.enabledproduct.pixauto;

import br.com.srportto.contratocommand.application.defaultservice.cancelamento.CancelamentoService;
import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoService;
import br.com.srportto.contratocommand.application.enabledproduct.pixauto.usecases.CancelarPixAutoUseCase;
import br.com.srportto.contratocommand.application.enabledproduct.pixauto.usecases.CriarPixAutoUseCase;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class PixAutoService implements ContratacaoService, CancelamentoService {

    private final CriarPixAutoUseCase criarPixAutoUseCase;
    private final CancelarPixAutoUseCase cancelarPixAutoUseCase;

    @Override
    public boolean validaContratacaoSuportada(CriarAutorizacaoRequest request) {
        return request.tipoProduto() != null && TipoProduto.PIX_AUTO.name().equalsIgnoreCase(request.tipoProduto());
    }

    @Override
    public AutorizacaoCompletaResponseDto criarAutorizacao(CriarAutorizacaoRequest request) {
        return criarPixAutoUseCase.execute(request);
    }


    @Override
    public boolean validaCancelamentoSuportado(CancelarAutorizacaoRequestDto request) {
        return request.getProdutoHeaderRequest() != null && TipoProduto.PIX_AUTO.name().equalsIgnoreCase(request.getProdutoHeaderRequest().name());
    }

    @Override
    public AutorizacaoCompletaResponseDto cancelarAutorizacao(CancelarAutorizacaoRequestDto request) {
        return cancelarPixAutoUseCase.execute(request);
    }
}