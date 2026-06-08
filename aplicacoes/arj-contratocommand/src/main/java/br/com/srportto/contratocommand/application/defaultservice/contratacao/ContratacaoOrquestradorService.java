package br.com.srportto.contratocommand.application.defaultservice.contratacao;

import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import br.com.srportto.contratocommand.shared.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ContratacaoOrquestradorService {

    private final List<ContratacaoService> produtosHabilitados;

    public AutorizacaoCompletaResponseDto criar(CriarAutorizacaoRequest request) {
        ContratacaoService produtoHabilitado = produtosHabilitados.stream()
                .filter(s -> s.validaContratacaoSuportada(request))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Produto nao suportado ou invalido (tipoProduto: " + request.tipoProduto() + ")"));

        return produtoHabilitado.criarAutorizacao(request);
    }
}
