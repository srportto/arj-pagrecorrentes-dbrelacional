package br.com.srportto.autorizacaostatusproducer.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modelo de domínio puro do evento de autorização (D2-b de design.md): mesmos ~25 campos que
 * o Avro EventoAutorizacao carrega, sem nenhum import de {@code org.apache.avro.*} nem da
 * classe gerada pelo avro-maven-plugin — domínio livre de framework/formato de fio.
 *
 * <p>Fica entre dois mapeamentos: {@code EventoAutorizacaoConverter} (infrastructure/messaging)
 * traduz o payload JSON para este tipo; {@code EventoAutorizacaoAvroMapper}
 * (infrastructure/messaging) traduz este tipo para o record Avro publicado no Kafka.
 */
public record EventoAutorizacao(

        UUID idAutorizacao,

        Integer idParticaoConta,

        LocalDate dataFimVigencia,

        Long tipoProduto,

        Long tipoJornada,

        Integer status,

        String motivoStatus,

        LocalDate dataInicioVigencia,

        LocalDateTime dataHoraInclusao,

        LocalDateTime dataHoraUltimaAtlz,

        BigDecimal valor,

        String idAutorizacaoEmpresa,

        BigDecimal valorLimite,

        Integer frequencia,

        Integer quantidadeDividasCiclo,

        Integer indicadorUsoLimiteConta,

        Integer indicadorTipoMensageria,

        String codigoCanalContratacao,

        String descricao,

        UUID idUnicoContaContratante,

        UUID idPessoaPagadora,

        UUID idPessoaDevedora,

        UUID idPessoaRecebedora,

        String codigoCanalCancelamento,

        UUID idPessoaCancelamento,

        LocalDateTime dataHoraCancelamento,

        String motivoCancelamento,

        String metadados) {
}
