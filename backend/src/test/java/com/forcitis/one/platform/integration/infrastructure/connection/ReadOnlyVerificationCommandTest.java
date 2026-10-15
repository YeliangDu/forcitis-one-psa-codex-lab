package com.forcitis.one.platform.integration.infrastructure.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.env.MockEnvironment;

/** Output and usage of the read-only verification command; the packaged command against SQL Server is acceptance.platform.integration.ReadOnlyVerificationCommandIT. */
class ReadOnlyVerificationCommandTest {

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final ByteArrayOutputStream err = new ByteArrayOutputStream();
    final MockEnvironment environment = new MockEnvironment();

    private int run(String... arguments) {
        return ReadOnlyVerificationCommand.run(arguments, environment, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String output() {
        return out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("PAR-2 one line per item with reason codes and counts, the SQL error of a failed query, and the result; never the problem text with its names")
    void reportTellsCategoriesOnly() {
        FakeCatalog catalog = new FakeCatalog() {
            @Override
            public List<DatabaseLevelPermission> databaseLevelPermissions() {
                throw new CannotGetJdbcConnectionException("pool", new SQLTransientConnectionException("timed out", "S0001", 0,
                        new SQLException("denied on " + FakeCatalog.LEAKED, "S0001", 229)));
            }
        };
        catalog.roles.add("role_" + FakeCatalog.LEAKED);
        catalog.roles.add("db_datareader");
        catalog.reachableDatabases.add("otherworld");

        ReadOnlyVerificationCommand.report(ReadOnlyBaseline.check(catalog), new PrintStream(out, true, StandardCharsets.UTF_8));

        assertThat(out.toString(StandardCharsets.UTF_8).lines()).containsExactly(
                "(a) PASSED",
                "(b) UNDETERMINED reason=CATALOG_QUERY_FAILED count=1 error=229 sqlstate=S0001",
                "(c) VIOLATED reason=DATABASE_ROLE_MEMBERSHIP count=2",
                "(d) PASSED",
                "(e) PASSED",
                "(f) VIOLATED reason=OTHER_DATABASE_REACHABLE count=1",
                "(g) PASSED",
                "RESULT VIOLATED");
        assertThat(output()).doesNotContain(FakeCatalog.LEAKED).doesNotContain("otherworld").doesNotContain("db_datareader");
    }

    @Test
    @DisplayName("PAR-2 a pass prints seven PASSED lines and RESULT PASSED")
    void passReport() {
        ReadOnlyVerificationCommand.report(ReadOnlyBaseline.check(new FakeCatalog()), new PrintStream(out, true, StandardCharsets.UTF_8));

        assertThat(out.toString(StandardCharsets.UTF_8).lines()).containsExactly("(a) PASSED", "(b) PASSED", "(c) PASSED", "(d) PASSED", "(e) PASSED",
                "(f) PASSED", "(g) PASSED", "RESULT PASSED");
    }

    @Test
    @DisplayName("PAR-2 a password, secret or credential option in any case is wrong usage, named but its value never echoed")
    void secretsOnTheCommandLineAreRefused() {
        String value = "v" + UUID.randomUUID();
        for (String option : new String[] {"--Password", "--db.SECRET", "--a8-credential", "--spring.datasource.password"}) {
            out.reset();
            err.reset();

            assertThat(run("--connection=probe", option + "=" + value)).as(option).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
            assertThat(output()).as(option).doesNotContain(value).doesNotContain("RESULT");
        }
    }

    @Test
    @DisplayName("PAR-2 a missing, repeated or unknown connection name and an argument that is not an option are wrong usage")
    void connectionNameIsRequired() {
        assertThat(run()).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        assertThat(run("--connection=no-such-connection")).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        assertThat(run("--connection=probe", "--connection=legacy-approval")).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        String stray = "s" + UUID.randomUUID();
        assertThat(run("--connection=probe", stray)).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        assertThat(output()).doesNotContain(stray).doesNotContain("RESULT");
    }

    @Test
    @DisplayName("PAR-2 rule 6: a connection whose environment variables are missing is wrong usage naming the variables, not checked")
    void missingVariablesAreNamed() {
        environment.setProperty("READONLY_PROBE_DB_URL", "jdbc:sqlserver://127.0.0.1:1;databaseName=x");

        assertThat(run("--connection=probe")).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        assertThat(output()).contains("READONLY_PROBE_DB_USERNAME").contains("READONLY_PROBE_DB_PASSWORD").doesNotContain("RESULT");
    }

    @Test
    @DisplayName("PAR-2 a URL that is neither SQL Server nor PostgreSQL is wrong usage, and the URL is not printed")
    void unsupportedUrlIsNotPrinted() {
        String password = "p" + UUID.randomUUID();
        environment.setProperty("A8_DB_URL", "jdbc:mysql://host/db?password=" + password);
        environment.setProperty("A8_DB_USERNAME", "reader");
        environment.setProperty("A8_DB_PASSWORD", password);

        assertThat(run("--connection=integration-seeyon-background")).isEqualTo(ReadOnlyVerificationCommand.EXIT_USAGE);
        assertThat(output()).contains("A8_DB_URL").doesNotContain(password).doesNotContain("mysql");
    }
}
