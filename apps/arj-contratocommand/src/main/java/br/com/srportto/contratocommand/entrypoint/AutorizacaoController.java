package br.com.srportto.contratocommand.entrypoint;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

import br.com.srportto.contratocommand.application.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.application.cancelamento.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.contratacao.ContratacaoContext;
import br.com.srportto.contratocommand.application.contratacao.CriarAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.decisao.DecidirAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.decisao.DecisaoContext;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequest;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import br.com.srportto.contratocommand.entrypoint.contratosrest.DecisaoAutorizacaoRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/autorizacoes")
@AllArgsConstructor
public class AutorizacaoController {

    private final CriarAutorizacaoUseCase criarAutorizacaoUseCase;
    private final CancelarAutorizacaoUseCase cancelarAutorizacaoUseCase;
    private final DecidirAutorizacaoUseCase decidirAutorizacaoUseCase;

    @PostMapping
    public ResponseEntity<AutorizacaoCompletaResponseDto> insert(
            @RequestBody @Valid CriarAutorizacaoRequest request,
            @RequestHeader String tipoJornada) {
        var jornada = TipoJornadaAutorizacao.obterJornadaAutorizacaoEnumPorNome(tipoJornada);
        var context = ContratacaoContext.doRequest(jornada, request);

        AutorizacaoCompletaResponseDto autorizadaResponse = criarAutorizacaoUseCase.execute(context);

        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(autorizadaResponse.getIdAutorizacao())
                .toUri();

        return ResponseEntity.created(uri).body(autorizadaResponse);
    }

    @PatchMapping("/{idAutorizacao}/cancelar")
    public ResponseEntity<AutorizacaoCompletaResponseDto> cancelar(
            @PathVariable String idAutorizacao,
            @RequestHeader String tipoProduto,
            @RequestBody @Valid CancelarAutorizacaoRequest dados) {

        var produto = TipoProduto.obterTipoProdutoEnumPorNome(tipoProduto);
        var context = CancelamentoContext.doRequest(idAutorizacao, produto, dados);

        AutorizacaoCompletaResponseDto autorizacaoCanceladaResponse = cancelarAutorizacaoUseCase.execute(context);

        return ResponseEntity.ok(autorizacaoCanceladaResponse);
    }

    @PatchMapping("/{idAutorizacao}/decisao")
    public ResponseEntity<AutorizacaoCompletaResponseDto> decidir(
            @PathVariable String idAutorizacao,
            @RequestHeader String tipoProduto,
            @RequestBody @Valid DecisaoAutorizacaoRequest dados) {

        var produto = TipoProduto.obterTipoProdutoEnumPorNome(tipoProduto);
        var context = DecisaoContext.doRequest(idAutorizacao, produto, dados);

        AutorizacaoCompletaResponseDto autorizacaoDecididaResponse = decidirAutorizacaoUseCase.execute(context);

        return ResponseEntity.ok(autorizacaoDecididaResponse);
    }
}
