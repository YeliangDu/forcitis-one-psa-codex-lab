package com.forcitis.one.platform.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("#938 RoleRepository.lockRolesOf holds the role rows until the transaction ends (ADR-0034 decision 2 item 5: lock first, then compare)")
class RoleRowLockIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(RoleRowLockIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    RoleRepository roles;

    @Autowired
    PlatformTransactionManager transactions;

    /** Whether another connection gets the role row in the given mode at once; it waits 300 ms and gives up otherwise. */
    private boolean otherConnectionGets(String mode, UUID roleId) throws SQLException {
        try (Connection other = schemaConnection("platform_identity"); Statement statement = other.createStatement()) {
            statement.execute("set lock_timeout = '300ms'");
            statement.execute("select 1 from roles where role_id = '" + roleId + "' for " + mode);
            return true;
        } catch (SQLException blocked) {
            assertThat(blocked.getSQLState()).as("only a lock timeout means the row is held").isEqualTo("55P03");
            return false;
        }
    }

    private boolean[] lockedBy(boolean forWrite) {
        Role role = roles.findByCode(Role.SUPER_ADMIN).orElseThrow();
        return new TransactionTemplate(transactions).execute(status -> {
            roles.lockRolesOf(List.of(role.roleVersionId()), forWrite);
            try {
                return new boolean[] {otherConnectionGets("share", role.roleId().value()), otherConnectionGets("update", role.roleId().value())};
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    @DisplayName("for an account request the rows are shared: readers pass, a role writer waits")
    void aSharedLockBlocksWritersOnly() {
        assertThat(lockedBy(false)).containsExactly(true, false);
    }

    @Test
    @DisplayName("for a role change the rows are taken for update: readers and writers both wait")
    void anUpdateLockBlocksEveryone() {
        assertThat(lockedBy(true)).containsExactly(false, false);
    }
}
