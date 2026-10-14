package com.forcitis.one.platform.approval.infrastructure.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.ApprovalIntegrationSupport;
import com.forcitis.one.platform.approval.domain.LegacyInbox;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * I-1 and the column-level grant (SECURITY_INVARIANTS I-1, REIMAGINED_ARCHITECTURE §3.6): the legacy connection is a separate,
 * qualified, read-only DataSource that cannot disturb the application's own; the application refuses to start when the account
 * can read anything but the inbox view (so it can read no token table); an unreachable old database degrades to
 * legacyAvailable = false instead of failing the inbox.
 */
class LegacyInboxAccessIT extends ApprovalIntegrationSupport {

    @Autowired
    @Qualifier("legacy-approval")
    DataSource legacy;

    @Autowired
    DataSource primary;

    @Autowired
    LegacyInbox legacyInbox;

    static final String VIEW = "legacy_fixture.v_psa_one_legacy_todo";

    @Test
    @DisplayName("the legacy DataSource is qualified legacy-approval, read-only, and the application's own DataSource stays the default")
    void legacyDataSourceIsSeparateAndReadOnly() throws Exception {
        assertThat(legacy).isNotSameAs(primary);
        assertThat(legacy).isInstanceOfSatisfying(HikariDataSource.class, pool -> {
            assertThat(pool.getPoolName()).isEqualTo("legacy-approval");
            assertThat(pool.isReadOnly()).isTrue();
            assertThat(pool.getUsername()).isEqualTo("legacy_ro");
        });
        assertThat(primary).isInstanceOfSatisfying(HikariDataSource.class, pool -> assertThat(pool.getPoolName()).isNotEqualTo("legacy-approval"));
        try (var connection = legacy.getConnection()) {
            assertThat(connection.isReadOnly()).isTrue();
        }
    }

    @Test
    @DisplayName("I-1 the read-only account may SELECT the inbox view and nothing else; writing and reading other tables fail")
    void accountCanOnlyReadTheView() {
        JdbcClient asLegacy = JdbcClient.create(legacy);
        assertThat(asLegacy.sql("select count(*) from " + VIEW).query(Long.class).single()).isGreaterThanOrEqualTo(0L);
        assertThatThrownBy(() -> asLegacy.sql("select count(*) from legacy_fixture.todo_rows").query(Long.class).single())
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> asLegacy.sql("delete from " + VIEW).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> asLegacy.sql("select count(*) from platform_approval.approval_requests").query(Long.class).single())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("I-1 start-up proof: the guard accepts the view-only account and refuses an account with wider grants")
    void guardAcceptsViewOnlyAndRefusesWiderGrants() {
        assertThatCode(() -> new LegacyAccessGuard(JdbcClient.create(legacy), VIEW).verify()).doesNotThrowAnyException();
        // the application's own account owns many tables: such an account must never be configured as the legacy account
        assertThatThrownBy(() -> new LegacyAccessGuard(JdbcClient.create(primary), VIEW).verify())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("I-1");
    }

    @Test
    @DisplayName("C1a-14 an unreachable old database yields legacyAvailable = false, never an exception")
    void unreachableDatabaseDegrades() {
        DriverManagerDataSource unreachable = new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/none", "x", "y");
        LegacyInbox.LegacyTodos result = new JdbcLegacyInbox(JdbcClient.create(unreachable), VIEW, null).readTodos(new UserId(1));
        assertThat(result.available()).isFalse();
        assertThat(result.todos()).isEmpty();
    }

    @Test
    @DisplayName("without a configured url the legacy part is reported unavailable (nothing is guessed)")
    void notConfiguredIsUnavailable() {
        assertThat(new LegacyInboxConfiguration().legacyInbox(new LegacyProperties(null, null, null, null, null),
                new org.springframework.beans.factory.support.DefaultListableBeanFactory().getBeanProvider(DataSource.class))
                .readTodos(new UserId(1)).available()).isFalse();
        assertThat(legacyInbox.readTodos(new UserId(newId())).available()).isTrue();
    }
}
