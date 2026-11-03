package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.LoginLookup;
import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlEmployee;
import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.manager;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class LoginMissTransactionIntegrationTest extends WorkforceIntegrationSupport {
    private static final long SNAPSHOT_WRITER_LOCK = 20261014131000L;

    @Autowired
    OrgDirectory directory;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    DataSource dataSource;

    private boolean snapshotWriterLockIsFree() throws Exception {
        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            try (var statement = other.prepareStatement("select pg_try_advisory_xact_lock(?)")) {
                statement.setLong(1, SNAPSHOT_WRITER_LOCK);
                try (var result = statement.executeQuery()) {
                    result.next();
                    return result.getBoolean(1);
                }
            } finally {
                other.rollback();
            }
        }
    }

    @Test
    @DisplayName("INT-DDD-H1 首次登录登记员工与开账号不在同一事务：登录事务后段失败时员工登记不回滚，快照写锁也不再持有到登录提交")
    void registeringTheEmployeeCommitsApartFromTheLoginTransaction() {
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L)), List.of(head(2, 1), manager(2, 2)), List.of(post(100)),
                List.of(person(1, 2, 100L, null)));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);
        orgSource.singleLookupResult = List.of(person(3001, 2, 100L, 1L, "13900003001", "wx-new-hire"));
        boolean[] lockFreeInsideTheLoginTransaction = new boolean[1];

        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-new-hire")))).isPresent();
            try {
                lockFreeInsideTheLoginTransaction[0] = snapshotWriterLockIsFree();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            throw new IllegalStateException("the rest of the login fails, e.g. writing the browser identifier");
        })).hasMessageContaining("the rest of the login fails");

        assertThat(lockFreeInsideTheLoginTransaction[0]).as("the snapshot writer lock is not held until the login commits").isTrue();
        assertThat(count("employees")).as("the registration stays: it is not part of the login transaction").isEqualTo(2);
        assertThat(jdbc.sql("select count(*) from organization_workforce.employees where id = " + sqlEmployee(3001)).query(Integer.class).single()).isEqualTo(1);
    }
}
