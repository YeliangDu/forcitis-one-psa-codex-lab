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
    static final List<String> OTHER_USERS = List.of("one_prod_app", "app", "xone_dev_app", "ONE_DEV_app");

    static final String[] JOBS_OFF = {"forcitis.platform.integration.archive-store.enabled=false", "forcitis.platform.approval.process-archive.enabled=false",
            "forcitis.platform.audit.archive.enabled=false", "forcitis.cost.timesheet.consumed-events.enabled=false",
            "forcitis.organization.workforce.sync-purge.enabled=false", "forcitis.platform.integration.outbound-purge.enabled=false",
            "forcitis.platform.integration.event-publication.jobs-enabled=false", "forcitis.platform.scheduling.jobs-enabled=false",
            "forcitis.platform.identity.sso.enabled=false", "forcitis.platform.identity.emergency.check.enabled=false"};

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

    private static void migrate(String database) {
        try (Connection connection = connect(database, DEV_USER)) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("init-schemas.sql"));
        } catch (SQLException e) {
            throw new IllegalStateException("init-schemas.sql on " + database, e);
        }
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

    /** Rows of a table, read on a connection whose search path is the module schema, so the test names no schema of another module in SQL. */
    static long count(String database, String schema, String table, String where) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url(database) + "&currentSchema=" + schema, DEV_USER, PASSWORD);
             ResultSet rs = connection.createStatement().executeQuery("select count(*) from " + table + (where == null ? "" : " where " + where))) {
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
        return List.of(count(database, "platform_identity", "account_roles", null), count(database, "platform_identity", "emergency_admins", null),
                count(database, "platform_identity", "user_accounts", null), count(database, "platform_audit", "audit_entries", null),
                count(database, "platform_scheduling", "scheduler_locks", null), engineTables(database));
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
                "--spring.main.banner-mode=off", "--server.port=" + port));
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

    record Outcome(boolean started, int exitCode, List<String> lines) {
        String output() {
            return String.join("\n", lines);
        }
    }

    /** Starts the launcher in a child JVM; returns when the application reports it started (and stops it) or the process ends. */
    static Outcome launch(List<String> arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Duser.timezone=UTC", "-cp",
                System.getProperty("java.class.path"), SingleOperatorModeLauncher.class.getName()));
        command.addAll(arguments);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        List<String> lines = new ArrayList<>();
        boolean started = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (line.contains("Started OneApplication")) {
                    started = true;
                    break;
                }
                assertThat(System.nanoTime()).as("the process neither started nor ended: %s", String.join("\n", lines)).isLessThan(deadline);
            }
        }
        if (started) {
            process.destroy();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("the process ends").isTrue();
        return new Outcome(started, started ? 0 : process.exitValue(), lines);
    }
}
