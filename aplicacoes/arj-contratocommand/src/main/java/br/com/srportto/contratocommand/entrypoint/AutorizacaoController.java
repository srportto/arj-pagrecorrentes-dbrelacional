package br.com.srportto.contratocommand.entrypoint;

import br.com.srportto.contratocommand.application.defaultservice.cancelamento.CancelamentoContext;
import br.com.srportto.contratocommand.application.defaultservice.cancelamento.CancelamentoOrquestradorService;
import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoOrquestradorService;
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

    private final ContratacaoOrquestradorService orquestradorContratacaoService;
    private final CancelamentoOrquestradorService orquestradorCancelamentoService;

    @PostMapping
    public ResponseEntity<AutorizacaoCompletaResponseDto> insert(
            @RequestBody @Valid CriarAutorizacaoRequest requestRecord,
            @RequestHeader String tipoJornada) {
        var jornada = TipoJornadaAutorizacao.obterJornadaAutorizacaoEnumPorNome(tipoJornada);
        var request = new CriarAutorizacaoRequest(
                requestRecord.dataFimVigencia(),
                requestRecord.tipoProduto(),
                requestRecord.valor(),
                requestRecord.idAutorizacaoEmpresa(),
                requestRecord.valorLimite(),
                requestRecord.frequencia(),
                requestRecord.quantidadeDividasCiclo(),
                requestRecord.indicadorUsoLimiteConta(),
                requestRecord.codigoCanalContratacao(),
                requestRecord.descricao(),
                requestRecord.idUnicoContaContratante(),
                requestRecord.idPessoaPagadora(),
                requestRecord.idPessoaDevedora(),
                requestRecord.idPessoaRecebedora(),
                requestRecord.metadados(),
                jornada);
        AutorizacaoCompletaResponseDto autorizadaResponse = orquestradorContratacaoService.criar(request);

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

        AutorizacaoCompletaResponseDto autorizacaoCanceladaResponse = orquestradorCancelamentoService.cancelar(context);

        return ResponseEntity.ok(autorizacaoCanceladaResponse);
    }
}
