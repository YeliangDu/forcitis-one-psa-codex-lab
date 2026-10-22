package com.forcitis.one.platform.integration.infrastructure.connection;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.forcitis.one.platform.integration.api.ExternalCallKind;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Finding;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.SqlError;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Unmet;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Verdict;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The read-only verification command (PAR-2, ADR-0037 item 4, C-14): with the profile read-only-verification the process checks the login of
 * one named read-only connection with {@link ReadOnlyBaseline}, prints one line per item (a)–(g) and a RESULT line, and ends before any
 * application context exists, so no web server, migration, job, process engine or event publication starts. Only the three environment
 * variables of that connection are read, from the process environment only; a password, secret, credential or connection setting on the
 * command line is refused. SQL Server only.
 * Exit code: 0 passed, 2 wrong usage, 3 violated (also with undeterminable items), 4 undetermined. 1 stays Spring Boot's failed start.
 */
final class ReadOnlyVerificationCommand implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    static final String PROFILE = "read-only-verification";
    static final String CONNECTION_OPTION = "connection";

    static final int EXIT_PASSED = 0;
    static final int EXIT_USAGE = 2;
    static final int EXIT_VIOLATED = 3;
    static final int EXIT_UNDETERMINED = 4;

    static final List<Character> ITEMS = List.of('a', 'b', 'c', 'd', 'e', 'f', 'g');
    static final List<String> REFUSED_OPTION_WORDS = List.of("password", "secret", "credential");
    static final String SQL_SERVER_URL = "jdbc:sqlserver:";
    static final String CONFLICTING_PROFILES = "migrate | identity-operations";
    static final List<String> QUIET_LOGGERS = List.of("com.zaxxer.hikari", "com.microsoft.sqlserver", "org.postgresql", "org.springframework.jdbc");

    enum ReadOnlyConnection {
        SEEYON_BACKGROUND("integration-seeyon-background", "A8_DB_URL", "A8_DB_USERNAME", "A8_DB_PASSWORD"),
        LEGACY("integration-legacy", "LEGACY_DB_RO_URL", "LEGACY_DB_RO_USERNAME", "LEGACY_DB_RO_PASSWORD"),
        /** The probe login of the C-14 runbook, from /etc/forcitis-one/readonly-probe.env. */
        PROBE("probe", "READONLY_PROBE_DB_URL", "READONLY_PROBE_DB_USERNAME", "READONLY_PROBE_DB_PASSWORD");

        final String connectionName;
        final String urlVariable;
        final String usernameVariable;
        final String passwordVariable;

        ReadOnlyConnection(String connectionName, String urlVariable, String usernameVariable, String passwordVariable) {
            this.connectionName = connectionName;
            this.urlVariable = urlVariable;
            this.usernameVariable = usernameVariable;
            this.passwordVariable = passwordVariable;
        }

        static Optional<ReadOnlyConnection> named(String connectionName) {
            return Arrays.stream(values()).filter(c -> c.connectionName.equals(connectionName)).findFirst();
        }

        static String names() {
            return Arrays.stream(values()).map(c -> c.connectionName).collect(Collectors.joining("|"));
        }

        static boolean isVariable(String optionName) {
            String option = relaxed(optionName);
            return Arrays.stream(values()).flatMap(c -> Stream.of(c.urlVariable, c.usernameVariable, c.passwordVariable))
                    .anyMatch(variable -> relaxed(variable).equals(option));
        }
    }

    private record Group(Reason reason, @Nullable SqlError failure) {
    }

    /** Before MigrateOnlyListener, so that with migrate active as well the command refuses before any migration runs. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        if (!environment.acceptsProfiles(Profiles.of(PROFILE))) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROFILE, Map.of("spring.main.web-application-type", "none")));
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        int exitCode;
        try {
            Optional<String> loud = silenceLogs();
            if (loud.isPresent()) {
                err.println("wrong usage: " + loud.get());
                exitCode = EXIT_USAGE;
            } else {
                exitCode = run(event.getArgs(), environment, out, err);
            }
        } catch (RuntimeException | Error unexpected) {
            err.println("read-only verification failed (" + unexpected.getClass().getSimpleName() + ")");
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    /**
     * Every logger off, the configured ones (logging.level.*) included: pool and driver lines can carry the URL, the login or the server's
     * message text. The command's own lines do not go through logging. Without Spring Boot's Logback (e.g. -Dorg.springframework.boot.logging
     * .LoggingSystem=none leaves Logback at DEBUG) the logs cannot be switched off, so the command refuses to run.
     */
    static Optional<String> silenceLogs() {
        LoggingSystem logging = LoggingSystem.get(ReadOnlyVerificationCommand.class.getClassLoader());
        if (!(logging instanceof LogbackLoggingSystem) || !(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return Optional.of("the command runs only with the application's own Logback logging, which it switches off; do not set "
                    + LoggingSystem.SYSTEM_PROPERTY);
        }
        logging.getLoggerConfigurations().forEach(configuration -> logging.setLogLevel(configuration.getName(), LogLevel.OFF));
        logging.setLogLevel(LoggingSystem.ROOT_LOGGER_NAME, LogLevel.OFF);
        QUIET_LOGGERS.forEach(name -> logging.setLogLevel(name, LogLevel.OFF));
        context.getLoggerList().forEach(logger -> logger.setLevel(Level.OFF));
        if (context.getLoggerList().stream().anyMatch(Logger::isErrorEnabled)) {
            return Optional.of("logging could not be switched off");
        }
        return Optional.empty();
    }

    static int run(String[] arguments, ConfigurableEnvironment environment, PrintStream out, PrintStream err) {
        if (environment.acceptsProfiles(Profiles.of(CONFLICTING_PROFILES))) {
            err.println("wrong usage: the profile " + PROFILE + " runs alone, not with migrate or identity-operations");
            return EXIT_USAGE;
        }
        Optional<String> wrongUsage = wrongUsage(arguments);
        if (wrongUsage.isPresent()) {
            err.println("wrong usage: " + wrongUsage.get());
            err.println("usage: java -jar forcitis-one.jar --spring.profiles.active=" + PROFILE + " --" + CONNECTION_OPTION + "=<" + ReadOnlyConnection.names()
                    + ">; the login and its password come from the environment only");
            return EXIT_USAGE;
        }
        ReadOnlyConnection connection = ReadOnlyConnection.named(connectionName(arguments)).orElseThrow();
        List<String> missing = new ArrayList<>();
        String url = variable(environment, connection.urlVariable, missing);
        String username = variable(environment, connection.usernameVariable, missing);
        String password = variable(environment, connection.passwordVariable, missing);
        if (url == null || username == null || password == null) {
            err.println("wrong usage: the connection " + connection.connectionName + " is not configured, the process environment lacks " + missing);
            return EXIT_USAGE;
        }
        if (!url.startsWith(SQL_SERVER_URL)) {
            err.println("wrong usage: " + connection.urlVariable + " is not a SQL Server JDBC URL (" + SQL_SERVER_URL + "...); the command checks SQL Server logins only");
            return EXIT_USAGE;
        }
        ExternalConnectionProperties properties = Binder.get(environment)
                .bindOrCreate("forcitis.platform.integration.external-connection", ExternalConnectionProperties.class);
        Verdict verdict = ExternalConnectionFactory.onOneConnection(properties.limitsOf(ExternalCallKind.BACKGROUND),
                connection.connectionName + "-verification", url, username, password,
                source -> ReadOnlyBaseline.check(new JdbcReadOnlyCatalog(JdbcClient.create(source))), failure -> ReadOnlyBaseline.check(unconnectable(failure)));
        report(verdict, out);
        if (verdict.violated()) {
            return EXIT_VIOLATED;
        }
        return verdict.passed() ? EXIT_PASSED : EXIT_UNDETERMINED;
    }

    /** Every question fails with the driver's own connection failure, as the JDBC catalog would. */
    private static ReadOnlyCatalog unconnectable(SQLException failure) {
        return (ReadOnlyCatalog) Proxy.newProxyInstance(ReadOnlyCatalog.class.getClassLoader(), new Class<?>[] {ReadOnlyCatalog.class},
                (proxy, method, arguments) -> {
                    throw new CannotGetJdbcConnectionException("the connection to verify could not be opened", failure);
                });
    }

    private static Optional<String> wrongUsage(String[] arguments) {
        for (String argument : arguments) {
            if (!argument.startsWith("--")) {
                return Optional.of("only --name=value options are accepted, one argument is not an option");
            }
            String name = argument.substring(2).split("=", 2)[0];
            String lower = name.toLowerCase(Locale.ROOT);
            if (REFUSED_OPTION_WORDS.stream().anyMatch(lower::contains)) {
                return Optional.of("--" + name + " is refused: passwords, secrets and credentials are read from the environment only, never from the command line");
            }
            if (ReadOnlyConnection.isVariable(name) || relaxed(name).equals("springapplicationjson")) {
                return Optional.of("--" + name + " is refused: the connection settings are read from the process environment only, never from the command line");
            }
        }
        long connections = Arrays.stream(arguments).filter(a -> a.startsWith("--" + CONNECTION_OPTION + "=")).count();
        if (connections != 1) {
            return Optional.of("exactly one --" + CONNECTION_OPTION + "=<name> is required");
        }
        if (ReadOnlyConnection.named(connectionName(arguments)).isEmpty()) {
            return Optional.of("--" + CONNECTION_OPTION + " must name one of " + ReadOnlyConnection.names());
        }
        return Optional.empty();
    }

    private static String connectionName(String[] arguments) {
        String prefix = "--" + CONNECTION_OPTION + "=";
        return Arrays.stream(arguments).filter(a -> a.startsWith(prefix)).map(a -> a.substring(prefix.length())).findFirst().orElse("");
    }

    /** The process environment only: not a -D system property, a command-line option or SPRING_APPLICATION_JSON. */
    private static @Nullable String variable(ConfigurableEnvironment environment, String name, List<String> missing) {
        if (environment.getSystemEnvironment().get(name) instanceof String value && !value.isBlank()) {
            return value;
        }
        missing.add(name);
        return null;
    }

    private static String relaxed(String name) {
        return name.replaceAll("[-_.]", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Never the problem text: it names roles and databases. Below an item line come its locator lines and, under (f), the roles of the login's
     * own users in the system databases (READONLY_VERIFY_ACCEPTANCE.md section 3a), the only place a database, schema, object, permission,
     * grantee or role name is printed.
     */
    static void report(Verdict verdict, PrintStream out) {
        Map<Character, Unmet> byItem = verdict.unmet().stream().collect(Collectors.toMap(Unmet::item, Function.identity()));
        for (char item : ITEMS) {
            Unmet unmet = byItem.get(item);
            if (unmet == null) {
                out.println("(" + item + ") PASSED");
            } else {
                out.println("(" + item + ") " + (unmet.confirmed() ? "VIOLATED" : "UNDETERMINED") + details(unmet.findings()));
                new TreeSet<>(unmet.findings().stream().flatMap(f -> f.locators().stream().map(l -> LocatorLines.violation(item, f.reason(), l))).toList())
                        .forEach(out::println);
            }
            if (item == 'f') {
                new TreeSet<>(verdict.ownUsers().stream().map(LocatorLines::ownUser).toList()).forEach(out::println);
            }
            if (item == 'g') {
                new TreeSet<>(verdict.overridden().stream().map(LocatorLines::overridden).toList()).forEach(out::println);
            }
        }
        out.println("RESULT " + (verdict.violated() ? "VIOLATED" : verdict.passed() ? "PASSED" : "UNDETERMINED"));
    }

    private static String details(List<Finding> findings) {
        Map<Group, Integer> counted = new LinkedHashMap<>();
        findings.forEach(finding -> counted.merge(new Group(finding.reason(), finding.failure()), finding.count(), Integer::sum));
        StringBuilder line = new StringBuilder();
        counted.forEach((group, count) -> line.append(" reason=").append(group.reason().name()).append(" count=").append(count)
                .append(group.failure() == null ? "" : sqlError(group.failure())));
        return line.toString();
    }

    private static String sqlError(SqlError error) {
        String state = error.state() != null && error.state().matches("[A-Za-z0-9]{1,16}") ? error.state() : "none";
        return " error=" + error.number() + " sqlstate=" + state;
    }
}
