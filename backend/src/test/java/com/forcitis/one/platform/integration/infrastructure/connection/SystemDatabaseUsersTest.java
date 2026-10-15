package com.forcitis.one.platform.integration.infrastructure.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.DatabaseLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Finding;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ReachableDatabase;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.SqlError;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Unmet;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Verdict;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.Membership;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.OwnUserPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.OwnUserRole;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

/** C-14 (f) taken into the baseline (user 2026-10-15 「纳入基线」, one-coordinator narrowed, one-ops RDS check); live: ReadOnlyVerificationCommandIT. */
class SystemDatabaseUsersTest {

    final FakeCatalog catalog = new FakeCatalog().rdsOwnUsers();

    private Unmet f() {
        Verdict verdict = ReadOnlyBaseline.check(catalog);
        assertThat(verdict.unmet()).extracting(Unmet::item).containsExactly('f');
        return verdict.unmet().getFirst();
    }

    @Test
    @DisplayName("C-14 (f) 纳入基线：RDS 给每个登录的 msdb（三个 SQLAgent 角色）与 rdscore（无角色、只有 CONNECT）本人用户通过，角色信息行逐个列出")
    void theOwnUsersRdsCreatesPass() {
        Verdict verdict = ReadOnlyBaseline.check(catalog);

        assertThat(verdict.passed()).as(verdict.described().toString()).isTrue();
        assertThat(verdict.ownUsers()).containsExactlyInAnyOrder(new OwnUserRole("msdb", "SQLAgentOperatorRole"), new OwnUserRole("msdb", "SQLAgentReaderRole"),
                new OwnUserRole("msdb", "SQLAgentUserRole"), new OwnUserRole("rdscore", null));
    }

    @Test
    @DisplayName("C-14 (f) 纳入基线：msdb 本人用户只属三个 SQLAgent 角色的子集（一个、或一个都没有）时通过")
    void aSubsetOfTheAgentRolesPasses() {
        catalog.ownUser("msdb", "SQLAgentReaderRole");
        assertThat(ReadOnlyBaseline.check(catalog).passed()).isTrue();

        catalog.ownUser("msdb");
        assertThat(ReadOnlyBaseline.check(catalog).passed()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"db_datareader", "db_owner", "DatabaseMailUserRole", "sqlagentuserrole", "SQLAgentUserRole2"})
    @DisplayName("C-14 (f) 纳入基线：msdb 本人用户多一个三个 SQLAgent 角色以外的角色（按名称精确匹配，大小写不同也算）违规，定位 msdb，信息行列出该角色")
    void anyOtherMsdbRoleIsAViolation(String extra) {
        catalog.ownUser("msdb", "SQLAgentUserRole", extra);

        Unmet f = f();

        assertThat(f.confirmed()).isTrue();
        assertThat(f.findings()).extracting(Finding::reason, Finding::count, Finding::locators)
                .containsExactly(tuple(Reason.SYSTEM_DATABASE_OWN_USER, 1, List.of(new DatabaseLocator("msdb"))));
    }

    @Test
    @DisplayName("C-14 (f) 纳入基线：rdscore 本人用户属于任一角色违规，定位 rdscore")
    void anRdscoreRoleIsAViolation() {
        catalog.ownUser("rdscore", "db_datareader");

        assertThat(f().findings()).extracting(Finding::reason, Finding::locators)
                .containsExactly(tuple(Reason.SYSTEM_DATABASE_OWN_USER, List.of(new DatabaseLocator("rdscore"))));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"DATABASE G VIEW DEFINITION", "DATABASE W CONNECT", "DATABASE D SELECT", "OBJECT_OR_COLUMN G SELECT"})
    @DisplayName("C-14 (f) 纳入基线：rdscore 本人用户除库级 GRANT CONNECT 外另有任何授权行违规，定位 rdscore")
    void anRdscoreGrantBeyondConnectIsAViolation(String row) {
        String[] parts = row.split(" ", 3);
        catalog.ownUserPermissions.get("rdscore").add(new OwnUserPermission(parts[0], parts[1], parts[2]));

        assertThat(f().findings()).extracting(Finding::reason, Finding::locators)
                .containsExactly(tuple(Reason.SYSTEM_DATABASE_OWN_USER, List.of(new DatabaseLocator("rdscore"))));
    }

    @Test
    @DisplayName("C-14 (f) 纳入基线：master、tempdb、model 有本人用户（无论角色）仍违规，每库一个定位，计数为库数")
    void anOwnUserInAnyOtherSystemDatabaseIsAViolation() {
        catalog.reachableDatabases.add("model");
        catalog.ownUser("master").ownUser("tempdb", "db_datareader").ownUser("model");

        Unmet f = f();

        assertThat(f.findings()).extracting(Finding::reason, Finding::count).containsExactly(tuple(Reason.SYSTEM_DATABASE_OWN_USER, 3));
        assertThat(f.findings().getFirst().locators()).containsExactly(new DatabaseLocator("master"), new DatabaseLocator("model"), new DatabaseLocator("tempdb"));
    }

    @Test
    @DisplayName("C-14 (f) 纳入基线：msdb 与 rdscore 按编号与名称认，名称相同而编号不对的库不得用这两个允许形状")
    void msdbAndRdscoreAreKnownByIdAndName() {
        catalog.reachableDatabaseRows = List.of(new ReachableDatabase(6, "legacyworld"), new ReachableDatabase(3, "msdb"), new ReachableDatabase(4, "rdscore"));
        catalog.ownUser("msdb").ownUser("rdscore");

        assertThat(f().findings().getFirst().locators()).containsExactly(new DatabaseLocator("msdb"), new DatabaseLocator("rdscore"));
    }

    @Test
    @DisplayName("C-14 (f) 纳入基线附加条件：本登录拥有 Agent 作业时违规，原因 OWNS_AGENT_JOB、计数为作业个数、无定位；problem 与 toString 不含作业名")
    void anOwnedAgentJobIsAViolationWithoutLocator() {
        catalog.ownedAgentJobs = 3;

        Unmet f = f();

        assertThat(f.confirmed()).isTrue();
        assertThat(f.findings()).extracting(Finding::reason, Finding::count, Finding::locators).containsExactly(tuple(Reason.OWNS_AGENT_JOB, 3, List.of()));
        assertThat(f.problem()).isEqualTo("the login owns 3 SQL Agent job(s)");
    }

    @Test
    @DisplayName("C-14 (f) 纳入基线附加条件：Agent 作业读不到（如 229）判定不了，带 SQL 错误号与 SQLState")
    void unreadableAgentJobsAreUndetermined() {
        FakeCatalog refused = new FakeCatalog() {
            @Override
            public long ownedAgentJobs() {
                throw new CannotGetJdbcConnectionException("msdb", new SQLException("denied", "S0001", 229));
            }
        }.rdsOwnUsers();

        Unmet f = ReadOnlyBaseline.check(refused).unmet().getFirst();

        assertThat(f.item()).isEqualTo('f');
        assertThat(f.confirmed()).isFalse();
        assertThat(f.findings()).extracting(Finding::reason, Finding::failure).containsExactly(tuple(Reason.CATALOG_QUERY_FAILED, new SqlError(229, "S0001")));
    }

    @Test
    @DisplayName("C-14 (f) 纳入基线：rdscore 本人用户的授权读不到时判定不了，不按通过处理")
    void unreadableRdscoreGrantsAreUndetermined() {
        FakeCatalog refused = new FakeCatalog() {
            @Override
            public List<OwnUserPermission> ownUserPermissions(String systemDatabase) {
                throw new CannotGetJdbcConnectionException("rdscore", new SQLException("denied", "S0001", 229));
            }
        }.rdsOwnUsers();

        Unmet f = ReadOnlyBaseline.check(refused).unmet().getFirst();

        assertThat(f.confirmed()).isFalse();
        assertThat(f.findings()).extracting(Finding::reason).containsExactly(Reason.CATALOG_QUERY_FAILED);
    }

    @Test
    @DisplayName("C-14 (f) 纳入基线：msdb 本人用户有一个本登录叫不出名称的角色时判定不了，不按通过处理，也不出该角色的信息行")
    void anUnnamedMsdbRoleIsUndetermined() {
        catalog.ownUsers.get("msdb").add(new Membership(true, null));

        Verdict verdict = ReadOnlyBaseline.check(catalog);

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.violated()).isFalse();
        assertThat(verdict.unmet().getFirst().findings()).extracting(Finding::reason).containsExactly(Reason.NO_ANSWER);
        assertThat(verdict.ownUsers()).hasSize(4);
    }

    @Test
    @DisplayName("C-14 (f) 纳入基线：Verdict、发现与信息行记录的 toString 不含库名与角色名")
    void toStringNamesNoRoleOrDatabase() {
        catalog.ownUser("msdb", "role_secret_marker");

        Verdict verdict = ReadOnlyBaseline.check(catalog);

        assertThat(verdict.toString()).doesNotContain("secret_marker").doesNotContain("msdb").contains("SYSTEM_DATABASE_OWN_USER");
        assertThat(new Membership(true, "role_secret_marker").toString()).doesNotContain("secret_marker");
        assertThat(new OwnUserRole("msdb", "role_secret_marker").toString()).doesNotContain("secret_marker").doesNotContain("msdb");
    }
}
