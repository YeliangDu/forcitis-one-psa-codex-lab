package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container for the start-up checks of the single operator mode: the database is called forcitis_one_dev and its owner one_dev_app
 * (the names the check accepts), with the other databases and accounts the check must refuse. Passwords are made at run time.
 */
final class SingleOperatorModeEnvironment {

    static final String KEY = "forcitis.platform.identity.single-operator-mode";
    static final String DEV_DATABASE = "forcitis_one_dev";
    static final String PROD_DATABASE = "forcitis_one_prod";
    static final String DEV_USER = "one_dev_app";
    static final String PASSWORD = UUID.randomUUID().toString();
    static final List<String> OTHER_DATABASES = List.of("forcitis_one_dev2", "forcitis_one_dev_x", "FORCITIS_ONE_DEV");
    static final String FRESH_DATABASE = "forcitis_one_prod_fresh";
    static final String PROBE_DATABASE = "forcitis_one_migrate_probe";
    static final String ROLE_TARGET = "one_dev_x";
    static final List<String> OTHER_USERS = List.of("one_prod_app", "app", "xone_dev_app", "ONE_DEV_app");

    static final String[] JOBS_OFF = {"forcitis.platform.integration.archive-store.enabled=false", "forcitis.platform.approval.process-archive.enabled=false",
            "forcitis.platform.audit.archive.enabled=false", "forcitis.cost.timesheet.consumed-events.enabled=false",
            "forcitis.organization.workforce.sync-purge.enabled=false", "forcitis.platform.integration.outbound-purge.enabled=false",
            "forcitis.platform.integration.event-publication.jobs-enabled=false", "forcitis.platform.scheduling.jobs-enabled=false",
            "forcitis.platform.identity.emergency.check.enabled=false"};

    static final PostgreSQLContainer POSTGRES = TestPostgres.container().withDatabaseName(DEV_DATABASE).withUsername(DEV_USER).withPassword(PASSWORD)
            .withCommand("sh", "-c", "openssl req -new -x509 -days 1 -nodes -subj /CN=localhost -out /var/lib/postgresql/server.crt -keyout /var/lib/postgresql/server.key"
                    + " && chmod 600 /var/lib/postgresql/server.key && chown postgres:postgres /var/lib/postgresql/server.key /var/lib/postgresql/server.crt"
                    + " && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/var/lib/postgresql/server.crt -c ssl_key_file=/var/lib/postgresql/server.key");

    private static boolean prepared;

    private SingleOperatorModeEnvironment() {
    }

    /** Starts the container once per JVM and makes the databases and accounts; Ryuk removes the container when the JVM ends. */
    static synchronized void prepare() {
        if (prepared) {
            return;
        }
        POSTGRES.start();
        try (Connection admin = connect(DEV_DATABASE, DEV_USER); Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + PROD_DATABASE);
            statement.execute("CREATE DATABASE " + FRESH_DATABASE);
            statement.execute("CREATE DATABASE " + PROBE_DATABASE);
            statement.execute("CREATE ROLE " + ROLE_TARGET + " NOLOGIN");
            for (String database : OTHER_DATABASES) {
                statement.execute("CREATE DATABASE \"" + database + "\"");
            }
            for (String user : OTHER_USERS) {
                statement.execute("CREATE ROLE \"" + user + "\" LOGIN SUPERUSER PASSWORD '" + PASSWORD + "'");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("cannot make the databases and accounts of the check", e);
        }
        migrate(DEV_DATABASE);
        migrate(PROD_DATABASE);
        migrate(FRESH_DATABASE);
        runScript(PROBE_DATABASE, "init-dba-schemas.sql");
        prepared = true;
    }

    static String url(String database) {
        String url = POSTGRES.getJdbcUrl();
        String path = "/" + DEV_DATABASE + "?";
        if (!url.contains(path)) {
            throw new IllegalStateException("unexpected JDBC URL shape: " + url);
        }
        return url.replace(path, "/" + database + "?") + "&sslmode=require";
    }

    static Connection connect(String database, String user) throws SQLException {
        return DriverManager.getConnection(url(database), user, PASSWORD);
    }

    private static void initSchemas(String database) {
        runScript(database, "init-schemas.sql");
    }

    private static void runScript(String database, String script) {
        try (Connection connection = connect(database, DEV_USER)) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(script));
        } catch (SQLException e) {
            throw new IllegalStateException(script + " on " + database, e);
        }
    }

    private static void migrate(String database) {
        initSchemas(database);
        Flyway.configure().dataSource(url(database), DEV_USER, PASSWORD).locations(applicationProperty("locations").split(","))
                .defaultSchema(applicationProperty("default-schema")).schemas(applicationProperty("schemas").split(",")).createSchemas(true).load().migrate();
    }

    private static String applicationProperty(String key) {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read application.properties", e);
        }
        return properties.getProperty("spring.flyway." + key).strip();
    }

    /** Rows of a schema-qualified table, read only. */
    static long count(String database, String qualifiedTable, String where) throws SQLException {
        try (Connection connection = connect(database, DEV_USER);
             ResultSet rs = connection.createStatement().executeQuery("select count(*) from " + qualifiedTable + (where == null ? "" : " where " + where))) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static long engineTables(String database) throws SQLException {
        try (Connection connection = connect(database, DEV_USER);
             ResultSet rs = connection.createStatement().executeQuery("select count(*) from information_schema.tables where table_name like 'act\\_%'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** What a start of the application can write: grants, emergency accounts, audit entries, scheduler locks and the process engine's tables. */
    static List<Long> footprint(String database) throws SQLException {
        return List.of(count(database, "platform_identity.account_role_grants", null), count(database, "platform_identity.account_role_grants", "effective_to_at is null"),
                count(database, "platform_identity.emergency_admins", null),
                count(database, "platform_identity.user_accounts", null), count(database, "platform_audit.audit_entries", null),
                count(database, "platform_scheduling.scheduler_locks", null), engineTables(database));
    }

    static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    static boolean listening(int port) {
        try (var socket = new java.net.Socket("127.0.0.1", port)) {
            return socket.isConnected();
        } catch (IOException refused) {
            return false;
        }
    }

    /** The arguments of a resident start against a database, jobs off unless asked for; the mode key is passed only when given. */
    static List<String> residentArguments(String profiles, String database, String user, String modeValue, int port, boolean schedulerOn) {
        List<String> arguments = new ArrayList<>(List.of("--spring.profiles.active=" + profiles, "--spring.datasource.url=" + url(database),
                "--spring.datasource.username=" + user, "--spring.datasource.password=" + PASSWORD, "--flowable.database-schema-update=true",
                "--flowable.async-executor-activate=false", "--forcitis.platform.approval.process-administrator-required=false",
                "--spring.main.banner-mode=off", "--server.address=127.0.0.1", "--server.port=" + port));
        for (String property : JOBS_OFF) {
            if (!(schedulerOn && property.startsWith("forcitis.platform.scheduling.jobs-enabled"))) {
                arguments.add("--" + property);
            }
        }
        if (schedulerOn) {
            arguments.add("--forcitis.platform.scheduling.jobs-enabled=true");
        }
        if (modeValue != null) {
            arguments.add("--" + KEY + "=" + modeValue);
        }
        return arguments;
    }

    static final int MAX_PORT_ATTEMPTS = 5;
    private static final String SERVER_PORT = "--server.port=";

    /** port is the one the last attempt was started with (-1 when the arguments name none). */
    record Outcome(boolean started, int exitCode, List<String> lines, int port) {
        String output() {
            return String.join("\n", lines);
        }
    }

    /** Starts the launcher in a child JVM; returns when the application reports it started (and stops it) or the process ends; a process that does neither in time fails the test. */
    static Outcome launch(List<String> arguments) throws IOException, InterruptedException {
        return launch(arguments, SingleOperatorModeEnvironment::launchOnce);
    }

    /**
     * A port taken between choosing it and listening on it (another process on the machine) makes the start fail with "already in use", which says
     * nothing about the application: only that failure, and only when the process did not start, is repeated with a new port, at most
     * MAX_PORT_ATTEMPTS times. Every other outcome is returned as it is.
     */
    static Outcome launch(List<String> arguments, Launch launch) throws IOException, InterruptedException {
        List<String> current = new ArrayList<>(arguments);
        Outcome outcome = null;
        for (int attempt = 1; attempt <= MAX_PORT_ATTEMPTS; attempt++) {
            outcome = launch.run(current);
            int index = portArgument(current);
            if (outcome.started() || index < 0 || !outcome.output().contains("Port " + outcome.port() + " was already in use")) {
                return outcome;
            }
            current.set(index, SERVER_PORT + freePort());
        }
        return outcome;
    }

    @FunctionalInterface
    interface Launch {
        Outcome run(List<String> arguments) throws IOException, InterruptedException;
    }

    private static int portArgument(List<String> arguments) {
        for (int i = 0; i < arguments.size(); i++) {
            if (arguments.get(i).startsWith(SERVER_PORT) && !arguments.get(i).equals(SERVER_PORT + "0")) {
                return i;
            }
        }
        return -1;
    }

    private static Outcome launchOnce(List<String> arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Duser.timezone=UTC", "-cp",
                System.getProperty("java.class.path"), SingleOperatorModeLauncher.class.getName()));
        command.addAll(arguments);
        Process process = new ProcessBuilder(command).directory(new java.io.File(System.getProperty("java.io.tmpdir"))).redirectErrorStream(true).start();
        List<String> lines = java.util.Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Boolean> started = new CompletableFuture<>();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    lines.add(line);
                    if (line.contains("Started " + SingleOperatorModeLauncher.class.getSimpleName())) {
                        started.complete(true);
                        return;
                    }
                }
                started.complete(false);
            } catch (IOException e) {
                started.completeExceptionally(e);
            }
        });
        boolean up;
        try {
            up = started.get(180, TimeUnit.SECONDS);
        } catch (TimeoutException | ExecutionException e) {
            process.destroyForcibly();
            throw new AssertionError("the process neither started nor ended in time: " + String.join("\n", lines), e);
        }
        if (up) {
            process.destroy();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("the process ends").isTrue();
        reader.join(10_000);
        int index = portArgument(arguments);
        return new Outcome(up, up ? 0 : process.exitValue(), List.copyOf(lines), index < 0 ? -1 : Integer.parseInt(arguments.get(index).substring(SERVER_PORT.length())));
    }
}
