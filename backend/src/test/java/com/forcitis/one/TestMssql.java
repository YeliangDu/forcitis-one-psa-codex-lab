package com.forcitis.one;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.awaitility.Awaitility;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * The one way tests start SQL Server. A fork shares one container ({@link #shared()}, started on first use, left to Ryuk when the JVM ends) and every
 * class takes databases and logins of its own from it, named by class and purpose so no two classes meet. A class that would change server-wide state the
 * others must not see needs a container of its own; none does today.
 */
public final class TestMssql {

    /** Made up at run time, never a literal. */
    public static final String SA_PASSWORD = "Aa1!" + UUID.randomUUID();

    /** Seconds (mssql-jdbc loginTimeout) and milliseconds (socketTimeout): a container that accepts the connection but never answers fails the test instead of hanging it. */
    static final String LOGIN_TIMEOUT = "30";
    static final String SOCKET_TIMEOUT = "120000";

    private static final Set<String> DATABASES = ConcurrentHashMap.newKeySet();

    private TestMssql() {
    }

    /** A database made for one class; url() carries encrypt, trust and the timeouts above and is for the test's own connections. */
    public record TestMssqlDatabase(String name, String url) {
    }

    private static final class Holder {
        static final GenericContainer<?> SHARED = start();
    }

    /** One container per fork: lazily started once (class initialization is thread-safe), never stopped here; Ryuk removes it with the JVM. */
    public static GenericContainer<?> shared() {
        return Holder.SHARED;
    }

    /** Creates the database of this class and purpose on the shared container; asking twice for the same class and purpose fails. */
    public static TestMssqlDatabase database(Class<?> testClass, String purpose) {
        String name = name(testClass, purpose);
        if (!DATABASES.add(name)) {
            throw new IllegalStateException("database " + name + " was already requested for " + testClass.getName() + " / " + purpose);
        }
        GenericContainer<?> container = shared();
        try (Connection c = adminConnection(container, "master"); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE [" + name + "]");
        } catch (SQLException e) {
            throw new IllegalStateException("cannot create database " + name, e);
        }
        return new TestMssqlDatabase(name, url(container, name) + ";loginTimeout=" + LOGIN_TIMEOUT + ";socketTimeout=" + SOCKET_TIMEOUT);
    }

    /** A login name for this class and purpose: purpose_hash, the same hash as in the database names. */
    public static String login(Class<?> testClass, String purpose) {
        return name(testClass, purpose);
    }

    /** Without timeouts: for the application under test and the fixtures, which set their own. */
    public static String url(GenericContainer<?> container, String database) {
        return "jdbc:sqlserver://" + container.getHost() + ":" + container.getMappedPort(1433) + ";databaseName=" + database
                + ";encrypt=true;trustServerCertificate=true";
    }

    public static Connection adminConnection(GenericContainer<?> container, String database) throws SQLException {
        return DriverManager.getConnection(url(container, database) + ";loginTimeout=" + LOGIN_TIMEOUT + ";socketTimeout=" + SOCKET_TIMEOUT, "sa",
                SA_PASSWORD);
    }

    static String name(Class<?> testClass, String purpose) {
        if (!purpose.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("purpose is a plain identifier, was " + purpose);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(testClass.getName().getBytes(StandardCharsets.UTF_8));
            return purpose + "_" + HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static GenericContainer<?> start() {
        GenericContainer<?> container = new GenericContainer<>(TestImages.startingWith(TestImages.MSSQL_PREFIX))
                .withEnv("ACCEPT_EULA", "Y").withEnv("MSSQL_SA_PASSWORD", SA_PASSWORD).withExposedPorts(1433)
                .withStartupTimeout(Duration.ofMinutes(5))
                .waitingFor(Wait.forLogMessage(".*SQL Server is now ready for client connections.*", 1));
        try {
            container.start();
            // the log line comes before the login subsystem accepts the administrator
            Awaitility.await("SQL Server accepts the administrator login").atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500))
                    .pollInSameThread().ignoreExceptions().until(() -> {
                        adminConnection(container, "master").close();
                        return true;
                    });
            return container;
        } catch (RuntimeException e) {
            // every later class of the fork would only see NoClassDefFoundError: keep the cause and the server log in this first failure
            String log = container.isCreated() ? container.getLogs() : "(container not created)";
            container.stop();
            throw new IllegalStateException("SQL Server did not start; its log:\n" + log, e);
        }
    }
}
