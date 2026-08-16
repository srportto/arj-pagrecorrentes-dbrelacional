package br.com.srportto.contratocommand.infrastructure.web;

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

import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.port.in.CancelarAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.port.in.CriarAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoUseCase;
import br.com.srportto.contratocommand.domain.port.in.DecidirAutorizacaoCommand;
import br.com.srportto.contratocommand.domain.enums.TipoJornadaAutorizacao;
import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import br.com.srportto.contratocommand.infrastructure.web.contratosrest.AutorizacaoCompletaResponseDto;
import br.com.srportto.contratocommand.infrastructure.web.contratosrest.CancelarAutorizacaoRequest;
import br.com.srportto.contratocommand.infrastructure.web.contratosrest.CriarAutorizacaoRequest;
import br.com.srportto.contratocommand.infrastructure.web.contratosrest.DecisaoAutorizacaoRequest;
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
        var command = new CriarAutorizacaoCommand(
                jornada,
                request.dataFimVigencia(),
                request.tipoProduto(),
                request.valor(),
                request.idAutorizacaoEmpresa(),
                request.valorLimite(),
                request.frequencia(),
                request.quantidadeDividasCiclo(),
                request.indicadorUsoLimiteConta(),
                request.codigoCanalContratacao(),
                request.descricao(),
                request.idUnicoContaContratante(),
                request.idPessoaPagadora(),
                request.idPessoaDevedora(),
                request.idPessoaRecebedora(),
                request.metadados() == null ? null : request.metadados().toString());

        var autorizadaPersistida = criarAutorizacaoUseCase.execute(command);
        var autorizadaResponse = AutorizacaoCompletaResponseDto.from(autorizadaPersistida);

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
        var command = CancelarAutorizacaoCommand.doRequest(idAutorizacao, produto,
                dados.codigoCanalCancelamento(), dados.idPessoaCancelamento(), dados.motivoCancelamento());

        var autorizacaoCancelada = cancelarAutorizacaoUseCase.execute(command);

        return ResponseEntity.ok(AutorizacaoCompletaResponseDto.from(autorizacaoCancelada));
    }

    @PatchMapping("/{idAutorizacao}/decisao")
    public ResponseEntity<AutorizacaoCompletaResponseDto> decidir(
            @PathVariable String idAutorizacao,
            @RequestHeader String tipoProduto,
            @RequestBody @Valid DecisaoAutorizacaoRequest dados) {

        var produto = TipoProduto.obterTipoProdutoEnumPorNome(tipoProduto);
        var command = DecidirAutorizacaoCommand.doRequest(idAutorizacao, produto,
                dados.acao(), dados.codigoCanalDecisao(), dados.idPessoaDecisao());

        var autorizacaoDecidida = decidirAutorizacaoUseCase.execute(command);

        return ResponseEntity.ok(AutorizacaoCompletaResponseDto.from(autorizacaoDecidida));
    }
}
