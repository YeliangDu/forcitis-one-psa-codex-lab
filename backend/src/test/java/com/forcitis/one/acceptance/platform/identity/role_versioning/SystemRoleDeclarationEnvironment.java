package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container for the start-up reconciliation of the system role declaration (ADR-0034 decision 1 item 7): each database is
 * built by the application's own migrations only (no role data, card 7.2), and the application is started on it in a child JVM, as
 * SingleOperatorModeEnvironment does. Passwords are made at run time.
 */
final class SystemRoleDeclarationEnvironment {

    static final String USER = "one_role_declaration";
    static final String PASSWORD = UUID.randomUUID().toString();
    static final String FIRST_DATABASE = "forcitis_one_roles_first";

    static final String[] JOBS_OFF = {"forcitis.platform.integration.archive-store.enabled=false", "forcitis.platform.approval.process-archive.enabled=false",
            "forcitis.platform.audit.archive.enabled=false", "forcitis.cost.timesheet.consumed-events.enabled=false",
            "forcitis.organization.workforce.sync-purge.enabled=false", "forcitis.platform.integration.outbound-purge.enabled=false",
            "forcitis.platform.integration.event-publication.jobs-enabled=false", "forcitis.platform.scheduling.jobs-enabled=false",
            "forcitis.platform.identity.sso.enabled=false", "forcitis.platform.identity.emergency.check.enabled=false"};

    /** The dev profile refuses a database connection that is not encrypted, so the server gets a throw-away certificate (as SingleOperatorModeEnvironment). */
    static final PostgreSQLContainer POSTGRES = TestPostgres.container().withDatabaseName(FIRST_DATABASE).withUsername(USER).withPassword(PASSWORD)
            .withCommand("sh", "-c", "openssl req -new -x509 -days 1 -nodes -subj /CN=localhost -out /var/lib/postgresql/server.crt -keyout /var/lib/postgresql/server.key"
                    + " && chmod 600 /var/lib/postgresql/server.key && chown postgres:postgres /var/lib/postgresql/server.key /var/lib/postgresql/server.crt"
                    + " && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/var/lib/postgresql/server.crt -c ssl_key_file=/var/lib/postgresql/server.key");

    private static boolean started;

    private SystemRoleDeclarationEnvironment() {
    }

    record Outcome(boolean started, int exitCode, List<String> lines) {
        String output() {
            return String.join("\n", lines);
        }

        List<String> linesWith(String text) {
            return lines.stream().filter(line -> line.contains(text)).toList();
        }
    }

    /** A database of its own, migrated by the application's migrations and nothing else. */
    static synchronized String migratedDatabase(String name) {
        if (!started) {
            POSTGRES.start();
            started = true;
        }
        if (!name.equals(FIRST_DATABASE)) {
            try (Connection admin = connect(FIRST_DATABASE); Statement statement = admin.createStatement()) {
                statement.execute("CREATE DATABASE " + name);
            } catch (SQLException e) {
                throw new IllegalStateException("cannot make database " + name, e);
            }
        }
        try (Connection connection = connect(name)) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("init-schemas.sql"));
        } catch (SQLException e) {
            throw new IllegalStateException("init-schemas.sql on " + name, e);
        }
        Flyway.configure().dataSource(url(name), USER, PASSWORD).locations(flywayProperty("locations").split(","))
                .defaultSchema(flywayProperty("default-schema")).schemas(flywayProperty("schemas").split(",")).createSchemas(true).load().migrate();
        return name;
    }

    private static String flywayProperty(String key) {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read application.properties", e);
        }
        return properties.getProperty("spring.flyway." + key).strip();
    }

    static String url(String database) {
        String url = POSTGRES.getJdbcUrl();
        String path = "/" + FIRST_DATABASE + "?";
        if (!url.contains(path)) {
            throw new IllegalStateException("unexpected JDBC URL shape: " + url);
        }
        return url.replace(path, "/" + database + "?") + "&sslmode=require";
    }

    static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(url(database), USER, PASSWORD);
    }

    static List<List<Object>> rows(String database, String sql, Object... parameters) throws SQLException {
        List<List<Object>> rows = new ArrayList<>();
        try (Connection connection = connect(database); PreparedStatement select = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                select.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = select.executeQuery()) {
                int columns = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int c = 1; c <= columns; c++) {
                        Object value = rs.getObject(c);
                        row.add(value instanceof java.sql.Array array ? List.of((Object[]) array.getArray()) : value);
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    static void execute(String database, String sql, Object... parameters) throws SQLException {
        try (Connection connection = connect(database); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.execute();
        }
    }

    static List<String> arguments(String database) {
        List<String> arguments = new ArrayList<>(List.of("--spring.profiles.active=dev", "--spring.datasource.url=" + url(database),
                "--spring.datasource.username=" + USER, "--spring.datasource.password=" + PASSWORD, "--flowable.database-schema-update=true",
                "--flowable.async-executor-activate=false", "--forcitis.platform.approval.process-administrator-required=false",
                "--spring.main.banner-mode=off", "--server.address=127.0.0.1", "--server.port=0"));
        for (String property : JOBS_OFF) {
            arguments.add("--" + property);
        }
        return arguments;
    }

    /** Starts the launcher in a child JVM; returns when the application reports it started (and stops it) or the process ends. */
    static Outcome launch(String database) throws IOException, InterruptedException {
        return launch(database, SystemRoleDeclarationLauncher.class);
    }

    /** The same with another launcher class; it must print the same probe lines, as RaisedDeclarationLauncher does. */
    static Outcome launch(String database, Class<?> launcher) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Duser.timezone=UTC", "-cp",
                System.getProperty("java.class.path"), launcher.getName()));
        command.addAll(arguments(database));
        Process process = new ProcessBuilder(command).directory(new java.io.File(System.getProperty("java.io.tmpdir"))).redirectErrorStream(true).start();
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Boolean> up = new CompletableFuture<>();
        CompletableFuture<Boolean> runnerReported = new CompletableFuture<>();
        String runnerProbe = SystemRoleDeclarationLauncher.PROBE + " " + SystemRoleDeclarationLauncher.RUNNER;
        Thread reader = Thread.ofVirtual().start(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    lines.add(line);
                    if (line.contains("Started " + launcher.getSimpleName())) {
                        up.complete(true);
                    }
                    if (line.contains(runnerProbe)) {
                        runnerReported.complete(true);
                    }
                }
                up.complete(false);
                runnerReported.complete(false);
            } catch (IOException e) {
                up.completeExceptionally(e);
                runnerReported.completeExceptionally(e);
            }
        });
        boolean startedUp;
        try {
            startedUp = up.get(240, TimeUnit.SECONDS);
            if (startedUp) {
                runnerReported.get(60, TimeUnit.SECONDS);
            }
        } catch (TimeoutException | ExecutionException e) {
            process.destroyForcibly();
            throw new AssertionError("the process neither started nor ended in time: " + String.join("\n", lines), e);
        }
        if (startedUp) {
            process.destroy();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("the process ends").isTrue();
        reader.join(10_000);
        return new Outcome(startedUp, startedUp ? 0 : process.exitValue(), List.copyOf(lines));
    }
}
