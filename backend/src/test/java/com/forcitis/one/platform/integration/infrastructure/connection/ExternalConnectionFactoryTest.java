package com.forcitis.one.platform.integration.infrastructure.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.integration.api.ExternalCallKind;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ExternalConnectionFactoryTest {

    static final String SQL_SERVER = "jdbc:sqlserver://127.0.0.1:1;databaseName=none";
    static final String POSTGRES = "jdbc:postgresql://127.0.0.1:1/none";

    final ExternalConnectionFactory factory = new ExternalConnectionFactory(new ExternalConnectionProperties(Duration.ofSeconds(3), Duration.ofSeconds(20)));

    private HikariDataSource open(String url, ExternalCallKind kind) {
        return (HikariDataSource) factory.open("test-pool", url, "reader", "not-a-secret", kind);
    }

    @Test
    @DisplayName("D1-1 an interactive SQL Server pool waits at most 3 s for a statement, a network read, a lock and a connection")
    void interactiveSqlServerPool() {
        try (HikariDataSource pool = open(SQL_SERVER, ExternalCallKind.INTERACTIVE)) {
            assertThat(pool.getDataSourceProperties()).containsEntry("queryTimeout", "3").containsEntry("socketTimeout", "3000");
            assertThat(pool.getConnectionInitSql()).isEqualTo("SET LOCK_TIMEOUT 3000");
            assertThat(pool.getConnectionTimeout()).isEqualTo(3_000);
            assertThat(pool.isReadOnly()).isTrue();
            assertThat(pool.getPoolName()).isEqualTo("test-pool");
        }
    }

    @Test
    @DisplayName("D1-1 a background pool uses the longer limit, still far below the 4 minutes of a scheduler lock")
    void backgroundSqlServerPool() {
        try (HikariDataSource pool = open(SQL_SERVER, ExternalCallKind.BACKGROUND)) {
            assertThat(pool.getDataSourceProperties()).containsEntry("queryTimeout", "20").containsEntry("socketTimeout", "20000");
            assertThat(pool.getConnectionInitSql()).isEqualTo("SET LOCK_TIMEOUT 20000");
        }
    }

    @Test
    @DisplayName("D1-1 the PostgreSQL stand-in gets the equivalent statement, lock and read limits")
    void postgresPool() {
        try (HikariDataSource pool = open(POSTGRES, ExternalCallKind.INTERACTIVE)) {
            assertThat(pool.getDataSourceProperties()).containsEntry("socketTimeout", "3")
                    .containsEntry("options", "-c statement_timeout=3000 -c lock_timeout=3000");
            assertThat(pool.getConnectionInitSql()).isNull();
        }
    }

    @Test
    @DisplayName("D1-1 a database the factory has no limits for is refused rather than opened unbounded")
    void unknownDatabaseIsRefused() {
        assertThatThrownBy(() -> open("jdbc:mysql://127.0.0.1:1/none", ExternalCallKind.INTERACTIVE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> open(null, ExternalCallKind.INTERACTIVE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("D1-1 closing the factory closes every pool it opened")
    void closeClosesAllPools() {
        HikariDataSource first = open(SQL_SERVER, ExternalCallKind.INTERACTIVE);
        HikariDataSource second = open(SQL_SERVER, ExternalCallKind.BACKGROUND);

        factory.close();

        assertThat(first.isClosed()).isTrue();
        assertThat(second.isClosed()).isTrue();
    }

    @Test
    @DisplayName("D1-1 defaults are 3 s interactive and 20 s background; configuration may lower them but not pass 3 s and 30 s")
    void limitsComeFromConfigurationWithinTheCeilings() {
        ExternalConnectionProperties defaults = bind(java.util.Map.of());
        assertThat(defaults.interactiveTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(defaults.backgroundTimeout()).isEqualTo(Duration.ofSeconds(20));

        ExternalConnectionProperties lowered = bind(java.util.Map.of(
                "forcitis.platform.integration.external-connection.interactive-timeout", "1s",
                "forcitis.platform.integration.external-connection.background-timeout", "30s"));
        assertThat(lowered.interactiveTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(lowered.backgroundTimeout()).isEqualTo(Duration.ofSeconds(30));

        assertThatThrownBy(() -> bind(java.util.Map.of("forcitis.platform.integration.external-connection.interactive-timeout", "4s")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(java.util.Map.of("forcitis.platform.integration.external-connection.background-timeout", "31s")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(java.util.Map.of("forcitis.platform.integration.external-connection.interactive-timeout", "0s")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private static ExternalConnectionProperties bind(java.util.Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties)).bindOrCreate("forcitis.platform.integration.external-connection", ExternalConnectionProperties.class);
    }
}
