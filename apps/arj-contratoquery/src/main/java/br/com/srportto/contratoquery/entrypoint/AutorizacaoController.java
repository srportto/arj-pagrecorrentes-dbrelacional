package br.com.srportto.contratoquery.entrypoint;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import br.com.srportto.contratoquery.application.autorizacao.ConsultarAutorizacaoService;
import br.com.srportto.contratoquery.application.autorizacao.ListarAutorizacoesService;
import br.com.srportto.contratoquery.entrypoint.contratosrest.AutorizacaoDetalheResponseDto;
import br.com.srportto.contratoquery.entrypoint.contratosrest.AutorizacaoResumidaResponseDto;
import br.com.srportto.contratoquery.entrypoint.contratosrest.PaginacaoResponseDto;
import br.com.srportto.contratoquery.shared.interceptors.api.LayoutErrosApiResponse;
import br.com.srportto.contratoquery.shared.interceptors.api.LayoutErrosApiValidationsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/autorizacoes")
@AllArgsConstructor
@Tag(name = "Autorizacoes", description = "API de leitura de autorizacoes de produtos financeiros (PIX_AUTO, DDA_AUTO). "
        + "Servico somente leitura; as escritas ficam no `arj-contratocommand` (porta 8080).")
public class AutorizacaoController {

    private final ListarAutorizacoesService listarAutorizacoesService;
    private final ConsultarAutorizacaoService consultarAutorizacaoService;

    @GetMapping
    @Operation(
            summary = "Listar autorizacoes paginadas por conta contratante",
            description = "Retorna uma pagina de autorizacoes resumidas da conta contratante. "
                    + "Suporta filtro por status, paginacao (pagina, tamanho) e ordenacao (ordenarPor). "
                    + "Parametros de borda sao validados como regra de negocio (ver codigos 422).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de autorizacoes retornada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PaginacaoResponseDto.class))),
            @ApiResponse(responseCode = "422", description = "Falha de validacao de formato ou violacao de regra de negocio "
                    + "(idUnicoContaContratante ausente, pagina/tamanho fora da faixa, ordenarPor desconhecido)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(oneOf = {
                                    LayoutErrosApiValidationsResponse.class,
                                    LayoutErrosApiResponse.class}))),
            @ApiResponse(responseCode = "500", description = "Erro inesperado de aplicacao",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LayoutErrosApiResponse.class)))
    })
    public ResponseEntity<PaginacaoResponseDto<AutorizacaoResumidaResponseDto>> listar(
            @Parameter(description = "UUID da conta contratante. Obrigatorio (validado como regra de negocio).",
                    example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestParam(required = false) UUID idUnicoContaContratante,
            @Parameter(description = "Filtro por status (nome do enum, ex.: ATIVA, RECEBIDA, CANCELADA). "
                    + "Pode ser repetido para filtro multi-valor.",
                    example = "ATIVA")
            @RequestParam(required = false) List<String> status,
            @Parameter(description = "Indice da pagina (base 0). Default 0.", example = "0")
            @RequestParam(required = false, defaultValue = "0") Integer pagina,
            @Parameter(description = "Tamanho da pagina (1-100). Default 20.", example = "20")
            @RequestParam(required = false, defaultValue = "20") Integer tamanho,
            @Parameter(description = "Campo e direcao de ordenacao. Default `dataHoraInclusao,desc`. "
                    + "Whitelist fechada aceita apenas valores reconhecidos pelo service.",
                    example = "dataHoraInclusao,desc")
            @RequestParam(required = false, defaultValue = "dataHoraInclusao,desc") String ordenarPor) {

        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> resultado = listarAutorizacoesService.listar(
                idUnicoContaContratante,
                status,
                pagina,
                tamanho,
                ordenarPor);

        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{autorizacaoId}")
    @Operation(
            summary = "Consultar autorizacao por id",
            description = "Retorna a representacao detalhada de uma autorizacao. A particao e extraida do UUID "
                    + "(UUIDs fora da faixa 0-889 resultam em 404 sem hit no banco).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autorizacao encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AutorizacaoDetalheResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Autorizacao inexistente (ou UUID com particao fora da faixa 0-889)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LayoutErrosApiResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro inesperado de aplicacao",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LayoutErrosApiResponse.class)))
    })
    public ResponseEntity<AutorizacaoDetalheResponseDto> consultarPorId(
            @Parameter(description = "UUID da autorizacao (a particao e derivada do UUID reversivel).", required = true)
            @PathVariable UUID autorizacaoId) {

        AutorizacaoDetalheResponseDto autorizacao = consultarAutorizacaoService.consultarPorId(autorizacaoId);

        return ResponseEntity.ok(autorizacao);
    }
}
