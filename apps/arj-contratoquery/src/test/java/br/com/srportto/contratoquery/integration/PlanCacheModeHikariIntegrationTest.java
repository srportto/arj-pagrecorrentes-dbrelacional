package br.com.srportto.contratoquery.integration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prova empírica de que {@code hikari.connection-init-sql} aplica {@code plan_cache_mode} a
 * TODAS as conexões físicas do pool, não só à primeira — é exatamente o que a tarefa 2.1 da
 * change {@code reduzir-custo-planejamento-consultas} pede para provar, não supor.
 *
 * <p>O pool é criado com {@code minimumIdle == maximumPoolSize} e as N conexões são pegas
 * simultaneamente (sem devolver nenhuma) antes de inspecionar qualquer uma — só assim é garantido
 * que o Hikari abriu N conexões físicas distintas, e não reaproveitou uma única.
 */
@ExtendWith(PostgresLocalDisponivelCondition.class)
@DisplayName("Teste de integração: plan_cache_mode via connection-init-sql do HikariCP")
class PlanCacheModeHikariIntegrationTest {

    private static final int TAMANHO_POOL = 4;

    @Test
    @DisplayName("connection-init-sql aplica plan_cache_mode=force_generic_plan em todas as conexões do pool, não só na primeira")
    void connectionInitSqlValeParaTodasAsConexoes() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(PostgresLocalDisponivelCondition.urlBase());
        config.setUsername(PostgresLocalDisponivelCondition.USUARIO);
        config.setPassword(PostgresLocalDisponivelCondition.SENHA);
        config.setMinimumIdle(TAMANHO_POOL);
        config.setMaximumPoolSize(TAMANHO_POOL);
        config.setConnectionInitSql("SET plan_cache_mode = 'force_generic_plan'");

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            List<Connection> conexoesAbertasSimultaneamente = new ArrayList<>();
            try {
                for (int i = 0; i < TAMANHO_POOL; i++) {
                    conexoesAbertasSimultaneamente.add(dataSource.getConnection());
                }

                assertEquals(TAMANHO_POOL, conexoesAbertasSimultaneamente.size(),
                        "pré-condição: precisa ter conseguido abrir as " + TAMANHO_POOL
                                + " conexões físicas simultaneamente para a prova valer");

                for (Connection conexao : conexoesAbertasSimultaneamente) {
                    assertEquals("force_generic_plan", lerPlanCacheMode(conexao),
                            "conexão física " + conexao + " não recebeu plan_cache_mode do connection-init-sql");
                }
            } finally {
                for (Connection conexao : conexoesAbertasSimultaneamente) {
                    conexao.close();
                }
            }
        }
    }

    private String lerPlanCacheMode(Connection conexao) throws Exception {
        try (Statement stmt = conexao.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW plan_cache_mode")) {
            rs.next();
            return rs.getString(1);
        }
    }
}
