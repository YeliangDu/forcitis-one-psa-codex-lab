package com.forcitis.one.platform.integration.infrastructure.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigrateOnlyIT {
    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-dba-schemas.sql")
            .withCommand("sh", "-c", "openssl req -new -x509 -days 1 -nodes -subj /CN=localhost -out /var/lib/postgresql/server.crt -keyout /var/lib/postgresql/server.key"
                    + " && chmod 600 /var/lib/postgresql/server.key && chown postgres:postgres /var/lib/postgresql/server.key /var/lib/postgresql/server.crt"
                    + " && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/var/lib/postgresql/server.crt -c ssl_key_file=/var/lib/postgresql/server.key");

    record Outcome(int exitCode, String output) {
    }

    private Outcome launch(String profiles, String url, String password) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                System.getProperty("java.class.path"), MigrateOnlyLauncher.class.getName(), "--spring.profiles.active=" + profiles,
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + password));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(120, TimeUnit.SECONDS)).as("the process ends by itself").isTrue();
        return new Outcome(process.exitValue(), output);
    }

    private String encrypted() {
        return postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "sslmode=require";
    }

    private static long migrationFileCount() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/db/migration"))) {
            return files.filter(f -> f.getFileName().toString().endsWith(".sql")).count();
        }
    }

    private long count(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             ResultSet rs = c.createStatement().executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    @DisplayName("release step: the profile migrate runs the migrations and exits with 0 before any web server, job or process engine starts")
    void migratesAndExits() throws Exception {
        Outcome outcome = launch("migrate,dev", encrypted(), postgres.getPassword());

        assertThat(outcome.exitCode()).as(outcome.output()).isZero();
        assertThat(outcome.output()).contains("MIGRATE-ONLY OK").doesNotContain("Tomcat").doesNotContain("Started ").doesNotContain("flowable");
        assertThat(count("select count(*) from one_migration.flyway_schema_history where success")).isEqualTo(migrationFileCount());
        assertThat(count("select count(*) from information_schema.tables where table_name like 'act\\_%'")).as("no process engine ran").isZero();
    }

    @Test
    @DisplayName("release step: a migration that cannot run (here: wrong password) ends the process with a non-zero exit code and no application start")
    void failureIsNonZero() throws Exception {
        Outcome outcome = launch("migrate,dev", encrypted(), "not-the-password");

        assertThat(outcome.exitCode()).isNotZero();
        assertThat(outcome.output()).contains("MIGRATE-ONLY FAILED").doesNotContain("Tomcat");
    }

    @Test
    @DisplayName("release step: the profile migrate alone is refused, because the environment profile decides the transport requirement; nothing is migrated")
    void migrateAloneIsRefused() throws Exception {
        long before = count("select count(*) from information_schema.tables where table_schema = 'one_migration'");

        Outcome outcome = launch("migrate", encrypted(), postgres.getPassword());

        assertThat(outcome.exitCode()).isNotZero();
        assertThat(outcome.output()).contains("MIGRATE-ONLY FAILED").contains("migrate,dev or migrate,prod");
        assertThat(count("select count(*) from information_schema.tables where table_schema = 'one_migration'")).isEqualTo(before);
    }

    @Test
    @DisplayName("release step: under dev the connection must be encrypted; a plain connection ends the process with non-zero and migrates nothing")
    void plainConnectionIsRefusedUnderDev() throws Exception {
        Outcome outcome = launch("migrate,dev", postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "sslmode=disable", postgres.getPassword());

        assertThat(outcome.exitCode()).isNotZero();
        assertThat(outcome.output()).contains("MIGRATE-ONLY FAILED").contains("does not encrypt");
    }
}
