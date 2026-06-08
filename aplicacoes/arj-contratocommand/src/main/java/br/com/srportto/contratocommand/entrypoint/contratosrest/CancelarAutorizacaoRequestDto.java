package br.com.srportto.contratocommand.entrypoint.contratosrest;

import java.util.UUID;

import br.com.srportto.contratocommand.domain.enums.TipoProduto;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CancelarAutorizacaoRequestDto{
  
    @NotNull(message = "o campo 'codigoCanalCancelamento' é obrigatorio.")
    private String codigoCanalCancelamento;

    @NotNull  (message = "O campo 'idPessoaCancelamento' é obrigatório.")
    private UUID idPessoaCancelamento;

    private String motivoCancelamento;

    private String idAutorizacao;

    private TipoProduto produtoHeaderRequest;

    private TipoProduto TipoProdutoDoIdAutorizacao;

}
