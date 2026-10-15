package com.forcitis.one.platform.integration.infrastructure.connection;

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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
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
 * variables of that connection are read; a password, secret or credential on the command line is refused.
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

    /** Pool names of the running service (metric tag connection, ALERT lines) and the environment variables each reads, as in one.env. */
    enum ReadOnlyConnection {
        SEEYON_BACKGROUND("integration-seeyon-background", "A8_DB_URL", "A8_DB_USERNAME", "A8_DB_PASSWORD"),
        LEGACY("integration-legacy", "LEGACY_DB_RO_URL", "LEGACY_DB_RO_USERNAME", "LEGACY_DB_RO_PASSWORD"),
        LEGACY_APPROVAL("legacy-approval", "FORCITIS_PLATFORM_APPROVAL_LEGACY_URL", "FORCITIS_PLATFORM_APPROVAL_LEGACY_USERNAME",
                "FORCITIS_PLATFORM_APPROVAL_LEGACY_PASSWORD"),
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
    }

    private record Group(Reason reason, @Nullable SqlError failure) {
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        if (!environment.acceptsProfiles(Profiles.of(PROFILE))) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROFILE, Map.of("spring.main.web-application-type", "none")));
        silenceDriverAndPoolLogs();
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        int exitCode;
        try {
            exitCode = run(event.getArgs(), environment, out, err);
        } catch (RuntimeException | Error unexpected) {
            err.println("read-only verification failed (" + unexpected.getClass().getSimpleName() + ")");
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    /** Pool and driver log lines can carry the server's message text; the command prints only the error number and SQLState. */
    private static void silenceDriverAndPoolLogs() {
        LoggingSystem logging = LoggingSystem.get(ReadOnlyVerificationCommand.class.getClassLoader());
        for (String logger : List.of("com.zaxxer.hikari", "com.microsoft.sqlserver", "org.postgresql", "org.springframework.jdbc")) {
            logging.setLogLevel(logger, LogLevel.OFF);
        }
    }

    static int run(String[] arguments, ConfigurableEnvironment environment, PrintStream out, PrintStream err) {
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
        if (!missing.isEmpty()) {
            err.println("wrong usage: the connection " + connection.connectionName + " is not configured, the environment lacks " + missing);
            return EXIT_USAGE;
        }
        ExternalConnectionProperties properties = Binder.get(environment)
                .bindOrCreate("forcitis.platform.integration.external-connection", ExternalConnectionProperties.class);
        Verdict verdict;
        try {
            verdict = ExternalConnectionFactory.onOneConnection(properties.limitsOf(ExternalCallKind.BACKGROUND), connection.connectionName + "-verification",
                    url, username, password, source -> ReadOnlyBaseline.check(new JdbcReadOnlyCatalog(JdbcClient.create(source))),
                    failure -> ReadOnlyBaseline.check(unconnectable(failure)));
        } catch (IllegalArgumentException unsupported) {
            err.println("wrong usage: " + connection.urlVariable + " is neither a SQL Server nor a PostgreSQL JDBC URL");
            return EXIT_USAGE;
        }
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

    private static String variable(ConfigurableEnvironment environment, String name, List<String> missing) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
        return value;
    }

    /** Item lines and the result line only: reason codes, counts, error numbers and SQLStates; never the problem text, which names roles and databases. */
    static void report(Verdict verdict, PrintStream out) {
        Map<Character, Unmet> byItem = verdict.unmet().stream().collect(Collectors.toMap(Unmet::item, Function.identity()));
        for (char item : ITEMS) {
            Unmet unmet = byItem.get(item);
            if (unmet == null) {
                out.println("(" + item + ") PASSED");
            } else {
                out.println("(" + item + ") " + (unmet.confirmed() ? "VIOLATED" : "UNDETERMINED") + details(unmet.findings()));
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
