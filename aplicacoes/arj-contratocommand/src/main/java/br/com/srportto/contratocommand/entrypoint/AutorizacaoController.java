package br.com.srportto.contratocommand.entrypoint;

import br.com.srportto.contratocommand.application.defaultservice.cancelamento.CancelamentoOrquestradorService;
import br.com.srportto.contratocommand.application.enabledproduct.pixauto.ListarAutorizacoesService;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import br.com.srportto.contratocommand.application.defaultservice.contratacao.ContratacaoOrquestradorService;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.AutorizacaoResumidaResponseDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CancelarAutorizacaoRequestDto;
import br.com.srportto.contratocommand.entrypoint.contratosrest.CriarAutorizacaoRequest;
import br.com.srportto.contratocommand.entrypoint.contratosrest.PaginacaoResponseDto;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/autorizacoes")
@AllArgsConstructor
public class AutorizacaoController {

    private final ContratacaoOrquestradorService orquestradorContratacaoService;
    private final CancelamentoOrquestradorService orquestradorCancelamentoService;
    private final ListarAutorizacoesService listarAutorizacoesService;

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
    public ResponseEntity<AutorizacaoCompletaResponseDto> cancelar(@PathVariable String idAutorizacao, @RequestHeader String tipoProduto,
            @RequestBody @Valid CancelarAutorizacaoRequestDto request) {

        request.setIdAutorizacao(idAutorizacao);
        var produto = TipoProduto.obterTipoProdutoEnumPorNome(tipoProduto);
        request.setProdutoHeaderRequest(produto);

        AutorizacaoCompletaResponseDto autorizacaoCanceladaResponse = orquestradorCancelamentoService.cancelar(request);

        return ResponseEntity.ok(autorizacaoCanceladaResponse);
    }

    /**
     * Lista autorizações de uma conta contratante com filtros opcionais.
     *
     * @param idUnicoContaContratante UUID da conta contratante (obrigatório)
     * @param status lista de status desejados (opcional)
     * @param pagina número da página (opcional, padrão: 0)
     * @param tamanho quantidade de itens por página (opcional, padrão: 20)
     * @param ordenarPor campo e direção de ordenação (opcional, padrão: dataCriacao,desc)
     * @return resposta paginada com autorizações resumidas
     */
    @GetMapping("/listar")
    public ResponseEntity<PaginacaoResponseDto<AutorizacaoResumidaResponseDto>> listar(
            @RequestParam UUID idUnicoContaContratante,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false, defaultValue = "0") Integer pagina,
            @RequestParam(required = false, defaultValue = "20") Integer tamanho,
            @RequestParam(required = false, defaultValue = "dataHoraInclusao,desc") String ordenarPor) {

        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado = listarAutorizacoesService.listar(
                idUnicoContaContratante,
                status,
                pagina,
                tamanho,
                ordenarPor);

        return ResponseEntity.ok(resultado);
    }

}
