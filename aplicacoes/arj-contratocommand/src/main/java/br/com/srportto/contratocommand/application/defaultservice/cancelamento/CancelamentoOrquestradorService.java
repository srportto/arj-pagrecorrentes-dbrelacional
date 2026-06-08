package br.com.srportto.contratocommand.application.defaultservice.cancelamento;


import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CancelamentoOrquestradorService {

    private final List<CancelamentoService> produtosHabilitados;

    public AutorizacaoCompletaResponseDto cancelar(CancelarAutorizacaoRequestDto request) {
        CancelamentoService produtoHabilitado = produtosHabilitados.stream()
                .filter(s -> s.validaCancelamentoSuportado(request))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Produto nao suportado ou invalido (tipoProduto: " + request.getProdutoHeaderRequest().name()+ ")"));

        return produtoHabilitado.cancelarAutorizacao(request);
    }
}
