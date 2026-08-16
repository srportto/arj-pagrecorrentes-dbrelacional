package br.com.srportto.contratoquery.infrastructure.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.srportto.contratoquery.domain.port.in.ConsultarAutorizacaoUseCase;
import br.com.srportto.contratoquery.domain.port.in.ListarAutorizacoesUseCase;
import br.com.srportto.contratoquery.domain.port.in.ListarAutorizacoesUseCase.ResultadoListagem;
import br.com.srportto.contratoquery.infrastructure.web.contratosrest.AutorizacaoDetalheResponseDto;
import br.com.srportto.contratoquery.infrastructure.web.contratosrest.AutorizacaoResumidaResponseDto;
import br.com.srportto.contratoquery.infrastructure.web.contratosrest.PaginacaoResponseDto;

@RestController
@RequestMapping("/api/autorizacoes")
public class AutorizacaoController {

    private final ListarAutorizacoesUseCase listarAutorizacoesUseCase;
    private final ConsultarAutorizacaoUseCase consultarAutorizacaoUseCase;

    public AutorizacaoController(
            ListarAutorizacoesUseCase listarAutorizacoesUseCase,
            ConsultarAutorizacaoUseCase consultarAutorizacaoUseCase) {
        this.listarAutorizacoesUseCase = listarAutorizacoesUseCase;
        this.consultarAutorizacaoUseCase = consultarAutorizacaoUseCase;
    }

    @GetMapping
    public ResponseEntity<PaginacaoResponseDto<AutorizacaoResumidaResponseDto>> listar(
            @RequestParam(required = false) UUID idUnicoContaContratante,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false, defaultValue = "0") Integer pagina,
            @RequestParam(required = false, defaultValue = "20") Integer tamanho,
            @RequestParam(required = false, defaultValue = "dataHoraInclusao,desc") String ordenarPor) {

        ResultadoListagem resultado = listarAutorizacoesUseCase.listar(
                idUnicoContaContratante,
                status,
                pagina,
                tamanho,
                ordenarPor);

        // Monta o envelope de paginação a partir do modelo de domínio — o caso de uso não conhece o DTO (D6, D7).
        PaginacaoResponseDto<AutorizacaoResumidaResponseDto> corpo =
                PaginacaoResponseDto.<AutorizacaoResumidaResponseDto>builder()
                        .conteudo(resultado.conteudo().stream()
                                .map(AutorizacaoResumidaResponseDto::from)
                                .collect(Collectors.toList()))
                        .paginaAtual(resultado.paginaAtual())
                        .totalPaginas(resultado.totalPaginas())
                        .totalElementos(resultado.totalElementos())
                        .tamanho(resultado.tamanho())
                        .build();

        return ResponseEntity.ok(corpo);
    }

    @GetMapping("/{autorizacaoId}")
    public ResponseEntity<AutorizacaoDetalheResponseDto> consultarPorId(
            @PathVariable UUID autorizacaoId) {

        var autorizacao = consultarAutorizacaoUseCase.consultarPorId(autorizacaoId);

        return ResponseEntity.ok(AutorizacaoDetalheResponseDto.from(autorizacao));
    }
}
