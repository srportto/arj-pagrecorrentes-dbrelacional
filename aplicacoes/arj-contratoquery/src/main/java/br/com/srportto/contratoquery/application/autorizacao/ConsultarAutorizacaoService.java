package br.com.srportto.contratoquery.application.autorizacao;

import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.srportto.contratoquery.domain.entities.Autorizacao;
import br.com.srportto.contratoquery.domain.entities.IdAutorizacao;
import br.com.srportto.contratoquery.domain.utilities.ReversibleUUIDv7;
import br.com.srportto.contratoquery.entrypoint.contratosrest.AutorizacaoDetalheResponseDto;
import br.com.srportto.contratoquery.shared.exceptions.ResourceNotFoundException;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ConsultarAutorizacaoService {

    private static final int PARTICAO_MIN = 0;
    private static final int PARTICAO_MAX = 889;

    private final AutorizacaoQueryRepository autorizacaoQueryRepository;

    public AutorizacaoDetalheResponseDto consultarPorId(UUID autorizacaoId) {
        int idParticaoConta = extrairParticao(autorizacaoId);

        Autorizacao autorizacao = autorizacaoQueryRepository
                .findById(new IdAutorizacao(autorizacaoId, idParticaoConta))
                .orElseThrow(() -> autorizacaoNaoEncontrada(autorizacaoId));

        return AutorizacaoDetalheResponseDto.from(autorizacao);
    }

    /**
     * Deriva a particao a partir do proprio UUID (ReversibleUUIDv7). Um id que nao
     * tenha sido gerado pelo ReversibleUUIDv7, ou cuja particao esteja fora da faixa
     * valida, nao pode corresponder a nenhuma autorizacao persistida -> 404.
     */
    private int extrairParticao(UUID autorizacaoId) {
        int idParticaoConta;
        try {
            idParticaoConta = ReversibleUUIDv7.extract(autorizacaoId);
        } catch (IllegalArgumentException e) {
            throw autorizacaoNaoEncontrada(autorizacaoId);
        }

        if (idParticaoConta < PARTICAO_MIN || idParticaoConta > PARTICAO_MAX) {
            throw autorizacaoNaoEncontrada(autorizacaoId);
        }

        return idParticaoConta;
    }

    private ResourceNotFoundException autorizacaoNaoEncontrada(UUID autorizacaoId) {
        return new ResourceNotFoundException(
                String.format("Autorizacao nao encontrada para o id %s", autorizacaoId));
    }
}
