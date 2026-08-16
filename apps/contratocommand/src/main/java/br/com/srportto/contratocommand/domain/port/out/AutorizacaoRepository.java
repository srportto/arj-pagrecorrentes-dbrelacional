package br.com.srportto.contratocommand.domain.port.out;

import br.com.srportto.contratocommand.domain.model.Autorizacao;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Porta de saída de persistência de {@link Autorizacao}, sem dependência de JPA/Spring Data e sem conhecimento de particionamento. */
public interface AutorizacaoRepository {

    /**
     * Cria (quando {@code autorizacao.getVersion()} é nulo) ou atualiza (quando não é) a
     * autorização. Update aplica os campos sobre a entidade gerenciada — nunca monta e salva uma
     * instância nova com version não nulo (ver {@code AutorizacaoPersistenceMapper}).
     */
    Autorizacao save(Autorizacao autorizacao);

    /** Busca por UUID. A extração da partição física é responsabilidade exclusiva do adaptador. */
    Optional<Autorizacao> findById(UUID idAutorizacao);

    /**
     * Verifica a existência de autorização ativa com a chave de negócio (id_autorizacao_empresa)
     * para a conta informada — a poda para a partição quente da conta é responsabilidade do
     * adaptador.
     */
    boolean existeAutorizacaoAtivaComIdEmpresa(UUID idUnicoContaContratante, String idAutorizacaoEmpresa);

    /**
     * Transfere a autorização para a partição de expurgo correspondente à data de referência,
     * protegida por lock otimista. Quem calcula a partição de destino e faz o row movement é o
     * adaptador — o domínio pede a transferência, não sabe como ela é feita fisicamente.
     */
    Autorizacao transferirParaExpurgo(Autorizacao autorizacao, LocalDate dataReferenciaExpurgo);

}
