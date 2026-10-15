package com.forcitis.one.platform.integration.infrastructure.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.integration.api.ExternalCallKind;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ExternalConnectionFactoryTest {

    static final String SQL_SERVER = "jdbc:sqlserver://127.0.0.1:1;databaseName=none";
    static final String POSTGRES = "jdbc:postgresql://127.0.0.1:1/none";

    final ExternalConnectionFactory factory = new ExternalConnectionFactory(new ExternalConnectionProperties(null, null));

    private HikariDataSource open(String url, ExternalCallKind kind) {
        return (HikariDataSource) factory.open("test-pool", url, "reader", "not-a-secret", kind);
    }

    @Test
    @DisplayName("D1-1 an interactive SQL Server pool: 1 s for a connection, 1.5 s for a lock, 2 s for a statement, 3 s for a network read")
    void interactiveSqlServerPool() {
        try (HikariDataSource pool = open(SQL_SERVER, ExternalCallKind.INTERACTIVE)) {
            assertThat(pool.getDataSourceProperties()).containsEntry("queryTimeout", "2").containsEntry("socketTimeout", "3000");
            assertThat(pool.getConnectionInitSql()).isEqualTo("SET LOCK_TIMEOUT 1500");
            assertThat(pool.getConnectionTimeout()).isEqualTo(1_000);
            assertThat(pool.isReadOnly()).isTrue();
            assertThat(pool.getPoolName()).isEqualTo("test-pool");
        }
    }

    @Test
    @DisplayName("D1-1 a background pool uses the longer limit, still far below the 4 minutes of a scheduler lock")
    void backgroundSqlServerPool() {
        try (HikariDataSource pool = open(SQL_SERVER, ExternalCallKind.BACKGROUND)) {
            assertThat(pool.getDataSourceProperties()).containsEntry("queryTimeout", "15").containsEntry("socketTimeout", "20000");
            assertThat(pool.getConnectionInitSql()).isEqualTo("SET LOCK_TIMEOUT 10000");
            assertThat(pool.getConnectionTimeout()).isEqualTo(5_000);
        }
    }

    @Test
    @DisplayName("D1-1 the PostgreSQL stand-in gets the equivalent statement, lock and read limits")
    void postgresPool() {
        try (HikariDataSource pool = open(POSTGRES, ExternalCallKind.INTERACTIVE)) {
            assertThat(pool.getDataSourceProperties()).containsEntry("socketTimeout", "3")
                    .containsEntry("options", "-c statement_timeout=2000 -c lock_timeout=1500");
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
    @DisplayName("D1-1-R2 cancelHappensBeforeTheSocketGivesUp: lock wait < statement < network read, for both call kinds and both databases")
    void cancelHappensBeforeTheSocketGivesUp() {
        for (ExternalCallKind kind : ExternalCallKind.values()) {
            try (HikariDataSource pool = open(SQL_SERVER, kind)) {
                long lockMillis = Long.parseLong(pool.getConnectionInitSql().substring("SET LOCK_TIMEOUT ".length()));
                long queryMillis = Long.parseLong(pool.getDataSourceProperties().getProperty("queryTimeout")) * 1000;
                long socketMillis = Long.parseLong(pool.getDataSourceProperties().getProperty("socketTimeout"));
                assertThat(lockMillis).as("%s: SQL Server lock wait", kind).isLessThan(queryMillis);
                assertThat(queryMillis).as("%s: SQL Server statement", kind).isLessThan(socketMillis);
            }
            try (HikariDataSource pool = open(POSTGRES, kind)) {
                String options = pool.getDataSourceProperties().getProperty("options");
                long statementMillis = Long.parseLong(options.replaceAll(".*statement_timeout=(\\d+).*", "$1"));
                long lockMillis = Long.parseLong(options.replaceAll(".*lock_timeout=(\\d+).*", "$1"));
                long socketMillis = Long.parseLong(pool.getDataSourceProperties().getProperty("socketTimeout")) * 1000;
                assertThat(lockMillis).as("%s: PostgreSQL lock wait", kind).isLessThan(statementMillis);
                assertThat(statementMillis).as("%s: PostgreSQL statement", kind).isLessThan(socketMillis);
            }
        }
    }

    @Test
    @DisplayName("D1-1 the interactive budget is acquire + statement within 3 s; defaults are met; any other order or total stops the start-up")
    void limitsComeFromConfigurationInTheRightOrderAndWithinTheBudget() {
        ExternalConnectionProperties defaults = bind(Map.of());
        assertThat(defaults.interactive().acquireTimeout().plus(defaults.interactive().queryTimeout())).isLessThanOrEqualTo(Duration.ofSeconds(3));
        assertThat(defaults.interactive()).isEqualTo(new ExternalConnectionProperties.Limits(Duration.ofSeconds(1), Duration.ofMillis(1500), Duration.ofSeconds(2), Duration.ofSeconds(3)));
        assertThat(defaults.background()).isEqualTo(new ExternalConnectionProperties.Limits(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(15), Duration.ofSeconds(20)));

        ExternalConnectionProperties lowered = bind(interactive("500ms", "1s", "2s", "3s"));
        assertThat(lowered.interactive().acquireTimeout()).isEqualTo(Duration.ofMillis(500));

        assertThatThrownBy(() -> bind(interactive("1s", "2s", "2s", "3s"))).hasRootCauseInstanceOf(IllegalArgumentException.class).hasStackTraceContaining("lock-timeout < query-timeout < socket-timeout");
        assertThatThrownBy(() -> bind(interactive("1s", "1s", "3s", "3s"))).hasStackTraceContaining("lock-timeout < query-timeout < socket-timeout");
        assertThatThrownBy(() -> bind(interactive("1s", "1500ms", "2s", "2s"))).hasStackTraceContaining("lock-timeout < query-timeout < socket-timeout");
        assertThatThrownBy(() -> bind(interactive("2s", "1500ms", "2s", "3s"))).hasStackTraceContaining("at most PT3S");
        assertThatThrownBy(() -> bind(interactive("1s", "1500ms", "2s", "4s"))).hasStackTraceContaining("interactive.socket-timeout");
        assertThatThrownBy(() -> bind(interactive("1s", "1500ms", "2500ms", "3s"))).hasStackTraceContaining("whole seconds");
        assertThatThrownBy(() -> bind(interactive("0s", "1500ms", "2s", "3s"))).hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(Map.of(PREFIX + "interactive.acquire-timeout", "1s"))).hasStackTraceContaining("must all be set");
        assertThatThrownBy(() -> bind(background("6s", "10s", "15s", "20s"))).hasStackTraceContaining("background.acquire-timeout");
        assertThatThrownBy(() -> bind(background("5s", "10s", "15s", "31s"))).hasStackTraceContaining("background.socket-timeout");
    }

    static final String PREFIX = "forcitis.platform.integration.external-connection.";

    private static Map<String, String> interactive(String acquire, String lock, String query, String socket) {
        return limits("interactive", acquire, lock, query, socket);
    }

    private static Map<String, String> background(String acquire, String lock, String query, String socket) {
        return limits("background", acquire, lock, query, socket);
    }

    private static Map<String, String> limits(String kind, String acquire, String lock, String query, String socket) {
        return Map.of(PREFIX + kind + ".acquire-timeout", acquire, PREFIX + kind + ".lock-timeout", lock,
                PREFIX + kind + ".query-timeout", query, PREFIX + kind + ".socket-timeout", socket);
    }

    private static ExternalConnectionProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties)).bindOrCreate(PREFIX.substring(0, PREFIX.length() - 1), ExternalConnectionProperties.class);
    }
}
