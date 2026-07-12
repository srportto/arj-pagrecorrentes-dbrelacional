package br.com.srportto.contratocommand.entrypoint;

import br.com.srportto.contratocommand.application.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.application.cancelamento.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.application.contratacao.ContratacaoContext;
import br.com.srportto.contratocommand.application.contratacao.CriarAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequest;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/autorizacoes")
@AllArgsConstructor
public class AutorizacaoController {

    private final CriarAutorizacaoUseCase criarAutorizacaoUseCase;
    private final CancelarAutorizacaoUseCase cancelarAutorizacaoUseCase;

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
}
