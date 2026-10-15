package com.forcitis.one.platform.integration.infrastructure.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.fixture.doubles.MutableClock;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyAccounts.Login;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyAccounts.Pool;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * ADR-0037 items 4.1, 4.2 and 4.4 on one login: refused at the first open, re-checked before a sync and at least daily; a violation stops it
 * for good, three undeterminable re-checks in a row as well (intent 2026-10-15-120940).
 */
@ExtendWith(OutputCaptureExtension.class)
class ReadOnlyAccountsTest {

    static final Instant START = Instant.parse("2026-10-15T01:00:00Z");
    static final Login LOGIN = new Login("jdbc:sqlserver://legacy;databaseName=legacyworld", "names_ro");

    final MutableClock clock = new MutableClock(START, ZoneOffset.UTC);
    final FakeCatalog catalog = new FakeCatalog();
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    volatile RuntimeException catalogFailure;
    volatile Error catalogError;
    final ReadOnlyAccounts accounts = new ReadOnlyAccounts(clock, source -> {
        if (catalogFailure != null) {
            throw catalogFailure;
        }
        if (catalogError != null) {
            throw catalogError;
        }
        return catalog;
    }, meters);
    final List<String> closed = new ArrayList<>();

    @AfterEach
    void stopTicking() {
        accounts.close();
    }

    Pool pool(String name) {
        return new Pool(null, () -> closed.add(name));
    }

    Supplier<Pool> checkPool(String name) {
        return () -> pool(name + "-check");
    }

    void grantWrite() {
        catalog.databaseLevelPermissions.remove(new DatabaseLevelPermission("D", "INSERT"));
    }

    @Test
    @DisplayName("ADR37-1 a login off the baseline is refused when its first pool opens; the pool and the check pool are closed, the message names the item")
    void firstOpenRefuses() {
        grantWrite();

        assertThatThrownBy(() -> accounts.attach("integration-legacy", LOGIN, pool("integration-legacy"), checkPool("integration-legacy")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("integration-legacy").hasMessageContaining("(b)");
        assertThat(closed).containsExactlyInAnyOrder("integration-legacy", "integration-legacy-check");
    }

    @Test
    @DisplayName("ADR37-1 a login that cannot be checked is refused at the first open, without the database message (ADR-0037 item 3.5)")
    void undeterminableRefuses() {
        catalog.unreachable = true;

        assertThatThrownBy(() -> accounts.attach("legacy-approval", LOGIN, pool("legacy-approval"), checkPool("legacy-approval")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be determined")
                .hasMessageNotContaining(FakeCatalog.LEAKED).hasNoCause();
    }

    @Test
    @DisplayName("ADR37-1 the second pool of a checked login is not checked again at open; both pools share one login")
    void secondPoolSharesTheLogin() {
        accounts.attach("integration-seeyon-interactive", LOGIN, pool("interactive"), checkPool("interactive"));
        int afterFirst = catalog.queries.get();

        accounts.attach("integration-seeyon-background", LOGIN, pool("background"), checkPool("background"));

        assertThat(catalog.queries.get()).isEqualTo(afterFirst);
        assertThat(closed).isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 a failed re-check before a sync stops every pool of the login with an ALERT naming the item; afterwards nothing is asked any more")
    void failedRecheckStopsTheLogin(CapturedOutput output) {
        accounts.attach("integration-seeyon-interactive", LOGIN, pool("interactive"), checkPool("interactive"));
        accounts.attach("integration-seeyon-background", LOGIN, pool("background"), checkPool("background"));
        accounts.recheck("integration-seeyon-background");
        grantWrite();

        assertThatThrownBy(() -> accounts.recheck("integration-seeyon-background")).isInstanceOf(ExternalSystemUnavailableException.class)
                .hasMessageContaining("(b)");
        assertThat(closed).containsExactlyInAnyOrder("interactive", "background", "interactive-check");
        assertThat(output.getAll()).containsPattern("ALERT[^\\n]*integration-seeyon-background[^\\n]*\\(b\\)");

        int stoppedAt = catalog.queries.get();
        assertThatThrownBy(() -> accounts.recheck("integration-seeyon-interactive")).isInstanceOf(ExternalSystemUnavailableException.class)
                .hasMessageContaining("stopped");
        accounts.recheckDue();
        clock.set(START.plus(Duration.ofDays(3)));
        accounts.recheckDue();
        assertThat(catalog.queries.get()).as("a stopped login is not checked, and so not read, again").isEqualTo(stoppedAt);
    }

    @Test
    @DisplayName("ADR37-1 a further pool of a stopped login is refused and closed")
    void stoppedLoginRefusesNewPools() {
        accounts.attach("integration-legacy", LOGIN, pool("first"), checkPool("first"));
        grantWrite();
        assertThatThrownBy(() -> accounts.recheck("integration-legacy")).isInstanceOf(ExternalSystemUnavailableException.class);

        assertThatThrownBy(() -> accounts.attach("integration-legacy-again", LOGIN, pool("again"), checkPool("again")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("(b)");
        assertThat(closed).contains("again");
    }

    @Test
    @DisplayName("ADR37-1 at least daily: a login read only on request is checked again once its last check is 23 hours old, not before")
    void dailyRecheck(CapturedOutput output) {
        accounts.attach("legacy-approval", LOGIN, pool("legacy-approval"), checkPool("legacy-approval"));
        int checked = catalog.queries.get();

        clock.set(START.plus(Duration.ofHours(22)));
        accounts.recheckDue();
        assertThat(catalog.queries.get()).as("checked 22 h ago: not due").isEqualTo(checked);

        clock.set(START.plus(ReadOnlyAccounts.MAXIMUM_AGE));
        accounts.recheckDue();
        assertThat(catalog.queries.get()).as("checked 23 h ago: due").isGreaterThan(checked);

        grantWrite();
        clock.set(START.plus(ReadOnlyAccounts.MAXIMUM_AGE.multipliedBy(2)));
        accounts.recheckDue();
        assertThat(closed).contains("legacy-approval");
        assertThat(output.getAll()).containsPattern("ALERT[^\\n]*legacy-approval[^\\n]*\\(b\\)");
    }

    @Test
    @DisplayName("ADR37-1 the tick and the maximum age together stay below one day between two checks")
    void checkedWithinADay() {
        assertThat(ReadOnlyAccounts.MAXIMUM_AGE.plus(ReadOnlyAccounts.TICK)).isLessThan(Duration.ofDays(1));
    }

    @Test
    @DisplayName("ADR37-1 M-1 a re-check that cannot be determined makes only this read unavailable with an ALERT, once and twice; the pools stay open")
    void undeterminedRecheckFailsOnlyThisRead(CapturedOutput output) {
        accounts.attach("integration-legacy", LOGIN, pool("names"), checkPool("names"));
        catalog.unreachable = true;

        for (int attempt = 1; attempt <= 2; attempt++) {
            assertThatThrownBy(() -> accounts.recheck("integration-legacy")).isInstanceOf(ExternalSystemUnavailableException.class)
                    .hasMessageContaining("could not be determined").hasMessageNotContaining("stopped").hasMessageNotContaining(FakeCatalog.LEAKED);
        }

        assertThat(closed).as("two undeterminable re-checks do not stop the login").isEmpty();
        assertThat(output.getAll()).containsPattern("ALERT[^\\n]*integration-legacy[^\\n]*1 of 3").containsPattern("ALERT[^\\n]*2 of 3")
                .doesNotContain(FakeCatalog.LEAKED);
        catalog.unreachable = false;
        accounts.recheck("integration-legacy");
    }

    @Test
    @DisplayName("ADR37-1 M-1 the third undeterminable re-check in a row stops every pool of the login for good")
    void thirdUndeterminedRecheckStopsTheLogin(CapturedOutput output) {
        accounts.attach("integration-legacy", LOGIN, pool("names"), checkPool("names"));
        catalog.unreachable = true;
        for (int attempt = 1; attempt <= 2; attempt++) {
            assertThatThrownBy(() -> accounts.recheck("integration-legacy")).isInstanceOf(ExternalSystemUnavailableException.class);
        }

        assertThatThrownBy(() -> accounts.recheck("integration-legacy")).isInstanceOf(ExternalSystemUnavailableException.class)
                .hasMessageContaining("stopped").hasMessageContaining("3 times in a row");

        assertThat(closed).containsExactlyInAnyOrder("names", "names-check");
        assertThat(output.getAll()).containsPattern("ALERT[^\\n]*integration-legacy stopped");
        catalog.unreachable = false;
        assertThatThrownBy(() -> accounts.recheck("integration-legacy")).as("a stopped login stays stopped once the catalog answers again")
                .isInstanceOf(ExternalSystemUnavailableException.class).hasMessageContaining("stopped");
    }

    @Test
    @DisplayName("ADR37-1 M-1 a determined pass between undeterminable re-checks clears the count: two, a pass, two more do not stop the login")
    void aPassClearsTheCount() {
        accounts.attach("integration-legacy", LOGIN, pool("names"), checkPool("names"));

        for (int round = 0; round < 2; round++) {
            catalog.unreachable = true;
            for (int attempt = 1; attempt <= 2; attempt++) {
                assertThatThrownBy(() -> accounts.recheck("integration-legacy")).isInstanceOf(ExternalSystemUnavailableException.class)
                        .hasMessageNotContaining("stopped");
            }
            catalog.unreachable = false;
            accounts.recheck("integration-legacy");
        }

        assertThat(closed).isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 M-1 a confirmed violation stops the login at once, even after undeterminable re-checks")
    void violationAfterUndeterminedStopsAtOnce() {
        accounts.attach("integration-legacy", LOGIN, pool("names"), checkPool("names"));
        catalog.unreachable = true;
        assertThatThrownBy(() -> accounts.recheck("integration-legacy")).isInstanceOf(ExternalSystemUnavailableException.class);
        catalog.unreachable = false;
        grantWrite();

        assertThatThrownBy(() -> accounts.recheck("integration-legacy")).isInstanceOf(ExternalSystemUnavailableException.class)
                .hasMessageContaining("stopped").hasMessageContaining("(b)");
        assertThat(closed).containsExactlyInAnyOrder("names", "names-check");
    }

    @Test
    @DisplayName("ADR37-1 M-1 the daily check counts undeterminable checks the same way: the third in a row stops the login")
    void dailyUndeterminedChecksStopOnTheThird() {
        accounts.attach("legacy-approval", LOGIN, pool("legacy-approval"), checkPool("legacy-approval"));
        catalog.unreachable = true;

        for (int day = 1; day <= 3; day++) {
            clock.set(START.plus(ReadOnlyAccounts.MAXIMUM_AGE.multipliedBy(day)));
            accounts.recheckDue();
            assertThat(closed).as("after %d undeterminable daily checks", day).hasSize(day < 3 ? 0 : 2);
        }
    }

    @Test
    @DisplayName("ADR37-1 L-1 a check that throws unexpectedly, an Error included, stops the login with an ALERT instead of letting it read on")
    void unexpectedFailureStopsTheLogin(CapturedOutput output) {
        accounts.attach("integration-legacy", LOGIN, pool("names"), checkPool("names"));
        accounts.attach("legacy-approval", new Login("jdbc:sqlserver://legacy;databaseName=legacyworld", "todo_ro"), pool("todo"),
                checkPool("todo"));
        catalogFailure = new IllegalStateException("broken " + FakeCatalog.LEAKED);

        assertThatThrownBy(() -> accounts.recheck("integration-legacy")).isInstanceOf(ExternalSystemUnavailableException.class)
                .hasMessageContaining("stopped").hasMessageContaining("IllegalStateException").hasMessageNotContaining(FakeCatalog.LEAKED);
        assertThat(closed).containsExactlyInAnyOrder("names", "names-check");

        catalogFailure = null;
        catalogError = new LinkageError("broken " + FakeCatalog.LEAKED);
        clock.set(START.plus(ReadOnlyAccounts.MAXIMUM_AGE));
        accounts.recheckDue();

        assertThat(closed).contains("todo", "todo-check");
        assertThat(output.getAll()).containsPattern("ALERT[^\\n]*legacy-approval stopped[^\\n]*LinkageError").doesNotContain(FakeCatalog.LEAKED);
    }

    @Test
    @DisplayName("ADR37-1 L-1 a check that throws unexpectedly at the first open refuses the start-up and closes the pools, without the message")
    void unexpectedFailureAtFirstOpenRefuses() {
        catalogFailure = new IllegalStateException("broken " + FakeCatalog.LEAKED);

        assertThatThrownBy(() -> accounts.attach("integration-legacy", LOGIN, pool("names"), checkPool("names")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("refused").hasMessageNotContaining(FakeCatalog.LEAKED).hasNoCause();
        assertThat(closed).containsExactlyInAnyOrder("names", "names-check");
    }

    @Test
    @DisplayName("ADR37-1 L-1 a gauge per login tells the seconds since its last check, so a stalled daily check can raise an alert")
    void lastCheckAgeIsMeasured() {
        accounts.attach("legacy-approval", LOGIN, pool("legacy-approval"), checkPool("legacy-approval"));
        Gauge age = meters.find("forcitis.platform.integration.read.only.check.age.seconds").tag("connection", "legacy-approval").gauge();
        assertThat(age).isNotNull();
        assertThat(age.value()).isZero();

        clock.set(START.plus(Duration.ofHours(5)));
        assertThat(age.value()).isEqualTo(Duration.ofHours(5).toSeconds());

        clock.set(START.plus(ReadOnlyAccounts.MAXIMUM_AGE));
        accounts.recheckDue();
        assertThat(age.value()).isZero();
    }

    @Test
    @DisplayName("ADR37-1 a re-check of a connection that was never opened is a programming error, not a pass")
    void unknownConnection() {
        assertThatThrownBy(() -> accounts.recheck("nothing-opened")).isInstanceOf(IllegalStateException.class);
    }
}
