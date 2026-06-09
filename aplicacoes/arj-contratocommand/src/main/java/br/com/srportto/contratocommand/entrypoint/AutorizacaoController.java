package br.com.srportto.contratocommand.entrypoint;

import br.com.srportto.contratocommand.application.defaultservice.cancelamento.CancelamentoOrquestradorService;
import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoOrquestradorService;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
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

    private final ContratacaoOrquestradorService orquestradorContratacaoService;
    private final CancelamentoOrquestradorService orquestradorCancelamentoService;

    @PostMapping
    public ResponseEntity<AutorizacaoCompletaResponseDto> insert(
            @RequestBody @Valid CriarAutorizacaoRequest requestRecord) {
        AutorizacaoCompletaResponseDto autorizadaResponse = orquestradorContratacaoService.criar(requestRecord);

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
            @RequestBody @Valid CancelarAutorizacaoRequestDto request) {

        request.setIdAutorizacao(idAutorizacao);
        var produto = TipoProduto.obterTipoProdutoEnumPorNome(tipoProduto);
        request.setProdutoHeaderRequest(produto);

        AutorizacaoCompletaResponseDto autorizacaoCanceladaResponse = orquestradorCancelamentoService.cancelar(request);

        return ResponseEntity.ok(autorizacaoCanceladaResponse);
    }
}
