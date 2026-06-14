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
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/autorizacoes")
@AllArgsConstructor
public class AutorizacaoController {

    private final ListarAutorizacoesService listarAutorizacoesService;
    private final ConsultarAutorizacaoService consultarAutorizacaoService;

    @GetMapping
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

    @GetMapping("/{autorizacaoId}")
    public ResponseEntity<AutorizacaoDetalheResponseDto> consultarPorId(
            @PathVariable UUID autorizacaoId) {

        AutorizacaoDetalheResponseDto autorizacao = consultarAutorizacaoService.consultarPorId(autorizacaoId);

        return ResponseEntity.ok(autorizacao);
    }
}
