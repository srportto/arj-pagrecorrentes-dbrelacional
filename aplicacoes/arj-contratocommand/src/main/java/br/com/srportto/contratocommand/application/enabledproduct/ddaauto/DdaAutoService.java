package br.com.srportto.contratocommand.application.enabledproduct.ddaauto;

import br.com.srportto.contratocommand.application.defaultservice.cancelamento.CancelamentoService;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import org.springframework.stereotype.Service;

import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoService;
import br.com.srportto.contratocommand.application.enabledproduct.ddaauto.usecases.CancelarDdaAutoUseCase;
import br.com.srportto.contratocommand.application.enabledproduct.ddaauto.usecases.CriarDdaAutoUseCase;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class DdaAutoService implements ContratacaoService, CancelamentoService {

    private final CriarDdaAutoUseCase criarDdaAutoUseCase;
    private final CancelarDdaAutoUseCase cancelarDdaAutoUseCase;

    @Override
    public boolean validaContratacaoSuportada(CriarAutorizacaoRequest request) {
        return request.tipoProduto() != null && TipoProduto.DDA_AUTO.name().equalsIgnoreCase(request.tipoProduto());
    }

    @Override
    public AutorizacaoCompletaResponseDto criarAutorizacao(CriarAutorizacaoRequest request) {
        return criarDdaAutoUseCase.execute(request);
    }


    @Override
    public boolean validaCancelamentoSuportado(CancelarAutorizacaoRequestDto request) {
        return request.getProdutoHeaderRequest() != null && TipoProduto.DDA_AUTO.name().equalsIgnoreCase(request.getProdutoHeaderRequest().name());
    }

    @Override
    public AutorizacaoCompletaResponseDto cancelarAutorizacao(CancelarAutorizacaoRequestDto request) {
        return cancelarDdaAutoUseCase.execute(request);
    }
}