package com.forcitis.one;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The one way tests start PostgreSQL: same sort order (built-in C.UTF-8) as the dev database, so an ORDER BY on text answers the same everywhere (DDL-11). */
public final class TestPostgres {
    /** Seconds (pgjdbc unit). A container that accepts the TCP connection but never answers fails the test in connectTimeout/loginTimeout/socketTimeout instead of hanging it: the driver has no timeout of its own. */
    static final String CONNECT_TIMEOUT = "10";
    static final String SOCKET_TIMEOUT = "120";
    static final String LOGIN_TIMEOUT = "30";
    static final String INITDB_ARGS = "--locale-provider=builtin --locale=C.UTF-8 --builtin-locale=C.UTF-8";
    /**
     * CI saw a container that was up but whose connections were cut during SSL set-up for the whole wait (121 s, a startup failure for the class), and one whose connection broke mid-run. The wait for the
     * first JDBC connection (the init script of a class, 120 s by default) is shortened and repeated: a container that does not answer within START_CONNECT_SECONDS is thrown away and a fresh one started,
     * START_ATTEMPTS times; the wait for the "ready" log line (60 s) is left as it is. An infrastructure retry; no assertion is relaxed.
     */
    static final int START_ATTEMPTS = 3;
    static final int START_CONNECT_SECONDS = 90;
    static final int LOG_LINES = 40;
    /** Every cached Spring context keeps its connection pool open on the shared instance until the fork ends. */
    static final String MAX_CONNECTIONS = "500";

    /** The application's migrations on a database that has the init-schemas.sql schemas. */
    public static final String MIGRATED = "migrated";
    /** {@link #MIGRATED} plus classpath:db/testdata, for contexts that set spring.flyway.locations to both. */
    public static final String MIGRATED_TESTDATA = "migrated_testdata";

    private static final int MAX_IDENTIFIER = 63;

    private static final Map<String, String> TEMPLATES = new HashMap<>();
    private static final Set<String> HANDED_OUT = new HashSet<>();

    private TestPostgres() {
    }

    public static PostgreSQLContainer container() {
        return new Diagnosed(TestImages.startingWith("postgres:")).withEnv("POSTGRES_INITDB_ARGS", INITDB_ARGS)
                .withUrlParam("connectTimeout", CONNECT_TIMEOUT).withUrlParam("socketTimeout", SOCKET_TIMEOUT)
                .withUrlParam("loginTimeout", LOGIN_TIMEOUT).withStartupAttempts(START_ATTEMPTS).withConnectTimeoutSeconds(START_CONNECT_SECONDS);
    }

    /** What an exhausted start says: the cause, the last lines the container wrote (a removed container has no logs left to ask for), and how loaded the Docker engine was. */
    static String describeFailure(String image, int attempts, int connectSeconds, Throwable cause, List<String> logTail, String docker) {
        List<String> tail = logTail.size() > LOG_LINES ? logTail.subList(logTail.size() - LOG_LINES, logTail.size()) : logTail;
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return "container " + image + " did not become usable in " + attempts + " attempts (connection wait " + connectSeconds + " s each): " + root + "\n--- docker: " + docker
                + "\n--- last " + tail.size() + " container log lines" + (logTail.size() > tail.size() ? " (of " + logTail.size() + ")" : "") + " ---\n"
                + String.join("\n", tail);
    }

    /** The engine's own numbers, or why they could not be read: this runs while a test class is already failing, so it never throws. */
    static String dockerSummary() {
        try {
            var info = DockerClientFactory.lazyClient().infoCmd().exec();
            return info.getContainersRunning() + " of " + info.getContainers() + " containers running, " + info.getNCPU() + " CPUs, "
                    + (info.getMemTotal() == null ? "?" : info.getMemTotal() / (1024 * 1024)) + " MiB, engine " + info.getServerVersion();
        } catch (RuntimeException e) {
            return "info unavailable (" + e + ")";
        }
    }

    /** Remembers the last lines any attempt's container wrote (80 kept, the last 40 shown), because Testcontainers removes a failed attempt's container before the next one. */
    static final class Diagnosed extends PostgreSQLContainer {
        private final Deque<String> tail = new ArrayDeque<>();
        private final String image;

        Diagnosed(String image) {
            super(image);
            this.image = image;
            withLogConsumer(frame -> remember(frame.getUtf8String()));
        }

        synchronized void remember(String text) {
            for (String line : text.split("\\R")) {
                if (!line.isBlank()) {
                    tail.addLast(line);
                    if (tail.size() > 2 * LOG_LINES) {
                        tail.removeFirst();
                    }
                }
            }
        }

        /** What this container actually waits for a first JDBC connection, in seconds. */
        int connectSeconds() {
            return getConnectTimeoutSeconds();
        }

        private Runnable starter = super::start;

        /** Replaces the real start so that a test can fail it without Docker (a test must not subclass the container: TestContainerGuardTest). */
        void startWith(Runnable replacement) {
            starter = replacement;
        }

        @Override
        public void start() {
            try {
                starter.run();
            } catch (RuntimeException e) {
                List<String> lines;
                synchronized (this) {
                    lines = List.copyOf(tail);
                }
                throw new IllegalStateException(describeFailure(image, getStartupAttempts(), connectSeconds(), e, lines, dockerSummary()), e);
            }
        }
    }

    /** One instance per test JVM, started on first use; Ryuk removes it when the JVM ends. */
    public static PostgreSQLContainer shared() {
        return Shared.INSTANCE;
    }

    /**
     * A database of the test class's own on {@link #shared()}, copied from the purpose's template (migrated once per JVM), so classes stay
     * isolated without each starting a container and migrating.
     */
    public static synchronized TestDatabase database(Class<?> testClass, String purpose) {
        String name = databaseName(purpose, testClass);
        if (!HANDED_OUT.add(name)) {
            throw new IllegalStateException(testClass.getName() + " already has its " + purpose + " database " + name);
        }
        String template = TEMPLATES.computeIfAbsent(purpose, TestPostgres::migratedTemplate);
        execute("CREATE DATABASE " + name + " TEMPLATE " + template);
        return new TestDatabase(urlOf(name), shared().getUsername(), shared().getPassword());
    }

    /** The class name made a valid unquoted identifier, cut to fit PostgreSQL's 63 bytes; a hash of the exact name keeps names that sanitise alike (case, `.` and `_`) apart. */
    static String databaseName(String purpose, Class<?> testClass) {
        return databaseName(purpose, testClass.getName());
    }

    static String databaseName(String purpose, String className) {
        String full = purpose + "_" + className;
        String hash = String.format("%08x", full.hashCode());
        String readable = full.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        int room = MAX_IDENTIFIER - hash.length() - 1;
        return (readable.length() > room ? readable.substring(0, room) : readable) + "_" + hash;
    }

    private static String migratedTemplate(String purpose) {
        List<String> locations = new ArrayList<>(List.of(flywayProperty("locations").split(",")));
        switch (purpose) {
            case MIGRATED -> {
            }
            case MIGRATED_TESTDATA -> locations.add("classpath:db/testdata");
            default -> throw new IllegalArgumentException("no template for purpose " + purpose);
        }
        String template = "template_" + purpose;
        execute("DROP DATABASE IF EXISTS " + template + " WITH (FORCE)");
        execute("CREATE DATABASE " + template);
        try (Connection connection = connect(template)) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("init-schemas.sql"));
        } catch (SQLException e) {
            throw new IllegalStateException("init-schemas.sql on " + template, e);
        }
        Flyway.configure().dataSource(urlOf(template), shared().getUsername(), shared().getPassword()).locations(locations.toArray(String[]::new))
                .defaultSchema(flywayProperty("default-schema")).schemas(flywayProperty("schemas").split(",")).createSchemas(true).load().migrate();
        return template;
    }

    private static String flywayProperty(String key) {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read application.properties", e);
        }
        String value = properties.getProperty("spring.flyway." + key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("application.properties has no spring.flyway." + key);
        }
        return value.strip();
    }

    private static void execute(String sql) {
        try (Connection connection = connect(shared().getDatabaseName()); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    private static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(urlOf(database), shared().getUsername(), shared().getPassword());
    }

    private static String urlOf(String database) {
        String url = shared().getJdbcUrl();
        String path = "/" + shared().getDatabaseName() + "?";
        if (!url.contains(path)) {
            throw new IllegalStateException("unexpected JDBC URL shape: " + url);
        }
        return url.replace(path, "/" + database + "?");
    }

    private static final class Shared {
        static final PostgreSQLContainer INSTANCE = start();

        private static PostgreSQLContainer start() {
            PostgreSQLContainer container = container().withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=" + MAX_CONNECTIONS);
            container.start();
            return container;
        }
    }
}
