package br.com.srportto.contratoquery.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.srportto.contratoquery.domain.exception.ResourceNotFoundException;
import br.com.srportto.contratoquery.domain.model.Autorizacao;
import br.com.srportto.contratoquery.domain.port.in.ConsultarAutorizacaoUseCase;
import br.com.srportto.contratoquery.domain.port.out.AutorizacaoRepository;

/**
 * Caso de uso de consulta por id. A cascata de localização em partições — que antes vivia aqui —
 * é detalhe de armazenamento e mora em {@code AutorizacaoJpaAdapter} (ver design.md, D3).
 */
@Service
public class ConsultarAutorizacaoService implements ConsultarAutorizacaoUseCase {

    private final AutorizacaoRepository repository;

    public ConsultarAutorizacaoService(AutorizacaoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Autorizacao consultarPorId(UUID autorizacaoId) {
        return repository.buscarPorId(autorizacaoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Autorizacao nao encontrada para o id %s", autorizacaoId)));
    }
}
