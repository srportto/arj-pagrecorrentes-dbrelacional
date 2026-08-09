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
import br.com.srportto.contratocommand.shared.interceptors.api.LayoutErrosApiResponse;
import br.com.srportto.contratocommand.shared.interceptors.api.LayoutErrosApiValidationsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/autorizacoes")
@AllArgsConstructor
@Tag(name = "Autorizacoes", description = "API de escrita de autorizacoes de produtos financeiros (PIX_AUTO, DDA_AUTO). "
        + "Cria, cancela e decide (aprova/rejeita) autorizacoes em jornada 1 do PIX_AUTO.")
public class AutorizacaoController {

    private final CriarAutorizacaoUseCase criarAutorizacaoUseCase;
    private final CancelarAutorizacaoUseCase cancelarAutorizacaoUseCase;
    private final DecidirAutorizacaoUseCase decidirAutorizacaoUseCase;

    @PostMapping
    @Operation(
            summary = "Criar autorizacao (multi-produto)",
            description = "Cria uma autorizacao para o produto informado no campo `tipoProduto` "
                    + "(PIX_AUTO nasce em RECEBIDA aguardando aprovacao; DDA_AUTO nasce ATIVA). "
                    + "O header `tipoJornada` identifica a jornada (ex.: SPI_J1).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Autorizacao criada",
                    headers = @Header(name = "Location", description = "URI do recurso criado",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AutorizacaoCompletaResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Recurso duplicado (idAutorizacaoEmpresa ja existe na particao) "
                    + "ou conflito de concorrencia",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LayoutErrosApiResponse.class))),
            @ApiResponse(responseCode = "422", description = "Falha de validacao de formato (@Valid) ou violacao de regra de negocio",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(oneOf = {
                                    LayoutErrosApiValidationsResponse.class,
                                    LayoutErrosApiResponse.class}))),
            @ApiResponse(responseCode = "500", description = "Erro inesperado de aplicacao",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LayoutErrosApiResponse.class)))
    })
    public ResponseEntity<AutorizacaoCompletaResponseDto> insert(
            @RequestBody @Valid CriarAutorizacaoRequest request,
            @Parameter(description = "Jornada da autorizacao (ex.: SPI_J1). Resolvido para o enum TipoJornadaAutorizacao.",
                    required = true, example = "SPI_J1")
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
    @Operation(
            summary = "Cancelar autorizacao",
            description = "Cancela uma autorizacao existente. Header `tipoProduto` obrigatorio; "
                    + "deve bater com o produto persistido (validado por `TipoProdutoCancelamento`).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autorizacao cancelada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AutorizacaoCompletaResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Autorizacao inexistente",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LayoutErrosApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito de concorrencia (lock otimista, "
                    + "stale state ou violacao de integridade)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LayoutErrosApiResponse.class))),
            @ApiResponse(responseCode = "422", description = "Falha de validacao de formato ou violacao de regra de negocio "
                    + "(transicao de status invalida, produto divergente, etc.)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(oneOf = {
                                    LayoutErrosApiValidationsResponse.class,
                                    LayoutErrosApiResponse.class}))),
            @ApiResponse(responseCode = "500", description = "Erro inesperado de aplicacao",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LayoutErrosApiResponse.class)))
    })
    public ResponseEntity<AutorizacaoCompletaResponseDto> cancelar(
            @Parameter(description = "UUID da autorizacao", required = true)
            @PathVariable String idAutorizacao,
            @Parameter(description = "Tipo do produto (PIX_AUTO, DDA_AUTO). Resolvido para o enum TipoProduto.",
                    required = true, example = "PIX_AUTO")
            @RequestHeader String tipoProduto,
            @RequestBody @Valid CancelarAutorizacaoRequest dados) {

        var produto = TipoProduto.obterTipoProdutoEnumPorNome(tipoProduto);
        var context = CancelamentoContext.doRequest(idAutorizacao, produto, dados);

        AutorizacaoCompletaResponseDto autorizacaoCanceladaResponse = cancelarAutorizacaoUseCase.execute(context);

        return ResponseEntity.ok(autorizacaoCanceladaResponse);
    }

    @PatchMapping("/{idAutorizacao}/decisao")
    @Operation(
            summary = "Decidir sobre autorizacao em RECEBIDA (jornada 1 do PIX_AUTO)",
            description = "Aplica uma decisao (`APROVAR` / `REJEITAR` / `EXPIRAR`) sobre uma autorizacao "
                    + "que esteja em status RECEBIDA. Idempotente: status diferente de RECEBIDA resulta em 422 "
                    + "sem alterar a linha (sinal para o chamador automatizado nao repetir).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Decisao aplicada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AutorizacaoCompletaResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Autorizacao inexistente",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LayoutErrosApiResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito de concorrencia",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LayoutErrosApiResponse.class))),
            @ApiResponse(responseCode = "422", description = "Status atual nao permite decisao (ja resolvida), "
                    + "acao invalida ou divergencia de produto",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(oneOf = {
                                    LayoutErrosApiValidationsResponse.class,
                                    LayoutErrosApiResponse.class}))),
            @ApiResponse(responseCode = "500", description = "Erro inesperado de aplicacao",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LayoutErrosApiResponse.class)))
    })
    public ResponseEntity<AutorizacaoCompletaResponseDto> decidir(
            @Parameter(description = "UUID da autorizacao", required = true)
            @PathVariable String idAutorizacao,
            @Parameter(description = "Tipo do produto (PIX_AUTO na jornada 1). Resolvido para o enum TipoProduto.",
                    required = true, example = "PIX_AUTO")
            @RequestHeader String tipoProduto,
            @RequestBody @Valid DecisaoAutorizacaoRequest dados) {

        var produto = TipoProduto.obterTipoProdutoEnumPorNome(tipoProduto);
        var context = DecisaoContext.doRequest(idAutorizacao, produto, dados);

        AutorizacaoCompletaResponseDto autorizacaoDecididaResponse = decidirAutorizacaoUseCase.execute(context);

        return ResponseEntity.ok(autorizacaoDecididaResponse);
    }
}
