package br.com.srportto.contratocommand.application.services.cancelamento;

import br.com.srportto.contratocommand.domain.services.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.domain.services.cancelamento.CancelamentoService;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CancelamentoOrquestradorService {

    private final List<CancelamentoService> produtosHabilitados;

    public AutorizacaoCompletaResponseDto cancelar(CancelamentoContext context) {
        CancelamentoService produtoHabilitado = produtosHabilitados.stream()
                .filter(s -> s.validaCancelamentoSuportado(context))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Produto nao suportado ou invalido (tipoProduto: " + context.tipoProduto().name() + ")"));

        return produtoHabilitado.cancelarAutorizacao(context);
    }
}
