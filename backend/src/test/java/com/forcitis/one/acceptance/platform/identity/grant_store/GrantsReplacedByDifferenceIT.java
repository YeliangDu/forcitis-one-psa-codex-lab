package com.forcitis.one.acceptance.platform.identity.grant_store;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #732 part A: 0034-G7-01 (replace by difference), 0034-G5-01 / G5-02 (zero-length interval, clock behind the open instant) and the channels and
 * causes of ADR-0034 decision 2 items 3 and 4, all through the HTTP path and the operations commands on the grant store.
 */
class GrantsReplacedByDifferenceIT extends GrantStoreSupport {

    static final TestDatabase postgres = TestPostgres.database(GrantsReplacedByDifferenceIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final Pattern ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:Z|[+-]\\d{2}:\\d{2})");

    private static List<Instant> instantsIn(String text) {
        Matcher matcher = ISO.matcher(text);
        List<Instant> found = new ArrayList<>();
        while (matcher.find()) {
            found.add(OffsetDateTime.parse(matcher.group()).toInstant());
        }
        return found;
    }

    @Test
    @DisplayName("0034-G7-01 ADR-0034 第 2 项第 5 条：{X,Y} 改为 {Y,Z}：只关闭 X（原因 MANUAL、收回人为操作人、审计条目 ID 写回、关闭于注入时钟）、只开启 Z（渠道 MANUAL、授予人为操作人、开启于注入时钟）；Y 与 employee 的授予行 ID 和开启时刻不变；事件带 openedGrantIds 与 closedGrantIds")
    void onlyTheDifferenceIsClosedAndOpened() throws Exception {
        SignedIn administrator = administrator();
        SignedIn target = person();
        String x = customRole(administrator, "SELF", PLAIN_CODES.get(0));
        String y = customRole(administrator, "SELF", PLAIN_CODES.get(1));
        String z = customRole(administrator, "SELF", PLAIN_CODES.get(2));
        replacedRoles(administrator, target.userId(), List.of("employee", x, y));
        Grant employeeBefore = grants(target.userId(), "employee").getFirst();
        Grant xBefore = grants(target.userId(), x).getFirst();
        Grant yBefore = grants(target.userId(), y).getFirst();
        long total = grantRows();
        Instant later = NOW.plus(Duration.ofMinutes(5));
        clock.set(later);

        replacedRoles(administrator, target.userId(), List.of("employee", y, z));

        assertThat(grantRows()).as("one new row for Z, nothing else").isEqualTo(total + 1);
        assertThat(grants(target.userId(), y)).as("Y: same row, same start").containsExactly(yBefore);
        assertThat(grants(target.userId(), "employee")).containsExactly(employeeBefore);
        Grant xAfter = grants(target.userId(), x).getFirst();
        assertThat(xAfter.id()).isEqualTo(xBefore.id());
        assertThat(xAfter.from()).isEqualTo(xBefore.from());
        assertThat(xAfter.to()).isEqualTo(later);
        assertThat(xAfter.cause()).isEqualTo("MANUAL");
        assertThat(xAfter.revokedBy()).isEqualTo(administrator.userId());
        assertThat(xAfter.separationCommandId()).isNull();
        assertThat(audit(xAfter.revocationAuditEntryId()).action()).isEqualTo(REVOKED_ACTION);
        List<Grant> zRows = grants(target.userId(), z);
        assertThat(zRows).singleElement().satisfies(opened -> {
            assertThat(opened.open()).isTrue();
            assertThat(opened.from()).isEqualTo(later);
            assertThat(opened.channel()).isEqualTo("MANUAL");
            assertThat(opened.grantedBy()).isEqualTo(administrator.userId());
            assertThat(opened.grantAuditEntryId()).isNotNull();
        });
        List<String> events = publishedEvents("AccountRolesReplaced", xAfter.id().toString());
        assertThat(events.stream().filter(json -> ((List<String>) JsonPath.read(json, "$.closedGrantIds")).contains(xAfter.id().toString())).toList())
                .singleElement().satisfies(json -> {
            assertThat((List<String>) JsonPath.read(json, "$.openedGrantIds")).containsExactly(zRows.getFirst().id().toString());
            assertThat((List<String>) JsonPath.read(json, "$.closedGrantIds")).containsExactly(xAfter.id().toString());
        });
    }

    @Test
    @DisplayName("0034-G7-01 ADR-0034 第 2 项第 5 条：提交的集合与当前相同，什么都不动：不新增行、不关闭行")
    void anUnchangedSetWritesNothing() throws Exception {
        SignedIn administrator = administrator();
        SignedIn target = person();
        String x = customRole(administrator, "SELF", PLAIN_CODES.get(0));
        replacedRoles(administrator, target.userId(), List.of("employee", x));
        List<Grant> before = grants(target.userId());
        long total = grantRows();
        clock.set(NOW.plus(Duration.ofMinutes(3)));

        replacedRoles(administrator, target.userId(), List.of("employee", x));

        assertThat(grants(target.userId())).isEqualTo(before);
        assertThat(grantRows()).isEqualTo(total);
    }

    @Test
    @DisplayName("0034-G2-03 ADR-0034 第 2 项第 2 条：同一人再次获得同一角色新增一行，开始时刻取 greatest(时钟, 已有行最大失效时刻)；旧行不动，时钟较慢也不撞排他约束")
    void aRegrantStartsNoEarlierThanTheLastClose() throws Exception {
        SignedIn administrator = administrator();
        SignedIn target = person();
        String x = customRole(administrator, "SELF", PLAIN_CODES.get(0));
        replacedRoles(administrator, target.userId(), List.of("employee", x));
        Instant closeAt = NOW.plus(Duration.ofMinutes(20));
        clock.set(closeAt);
        replacedRoles(administrator, target.userId(), List.of("employee"));
        Grant closed = grants(target.userId(), x).getFirst();
        assertThat(closed.to()).isEqualTo(closeAt);
        clock.set(NOW.plus(Duration.ofMinutes(10)));

        replacedRoles(administrator, target.userId(), List.of("employee", x));

        List<Grant> rows = grants(target.userId(), x);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isEqualTo(closed);
        assertThat(rows.get(1).open()).isTrue();
        assertThat(rows.get(1).from()).as("not before the earlier row ended").isEqualTo(closeAt);
    }

    @Test
    @DisplayName("0034-G5-01 ADR-0034 第 4 项第 3 条：同一刻开启又收回得到零长区间：收回成功、会话吊销；该行不出现在任何时刻的还原中")
    void anOpenThenCloseAtOneInstantIsEmpty() throws Exception {
        SignedIn administrator = administrator();
        SignedIn target = person();
        String x = customRole(administrator, "SELF", PLAIN_CODES.get(0));
        replacedRoles(administrator, target.userId(), List.of("employee", x));
        SignedIn fresh = signIn(target.employeeId());
        assertThat(profileStatus(fresh)).isEqualTo(200);

        replacedRoles(administrator, target.userId(), List.of("employee"));

        Grant row = grants(target.userId(), x).getFirst();
        assertThat(row.from()).isEqualTo(NOW);
        assertThat(row.to()).isEqualTo(NOW);
        assertThat(profileStatus(fresh)).as("the session of the person is revoked by the close").isEqualTo(401);
        for (Instant moment : List.of(NOW.minusMillis(1), NOW, NOW.plusMillis(1), NOW.plusSeconds(60))) {
            assertThat(grantsAsOf(target.userId(), moment)).extracting(Grant::roleCode).doesNotContain(x);
        }
    }

    @Test
    @DisplayName("0034-G5-02 ADR-0034 第 2 项第 2 条：时钟回拨到开启时刻之前再收回：关闭时刻取开启时刻，成功，会话吊销，审计 detail 记实际钟点")
    void aCloseBehindTheOpenInstantStartsAtTheOpening() throws Exception {
        SignedIn administrator = administrator();
        SignedIn target = person();
        String x = customRole(administrator, "SELF", PLAIN_CODES.get(0));
        Instant opened = NOW.plus(Duration.ofMinutes(10));
        clock.set(opened);
        replacedRoles(administrator, target.userId(), List.of("employee", x));
        SignedIn fresh = signIn(target.employeeId());
        clock.set(NOW);

        replacedRoles(administrator, target.userId(), List.of("employee"));

        Grant row = grants(target.userId(), x).getFirst();
        assertThat(row.from()).isEqualTo(opened);
        assertThat(row.to()).as("greatest(clock, start)").isEqualTo(opened);
        assertThat(profileStatus(fresh)).isEqualTo(401);
        String detail = audit(row.revocationAuditEntryId()).detail();
        assertThat(instantsIn(detail)).as(detail).contains(NOW);
    }

    @Test
    @DisplayName("ADR-0034 第 2 项第 3、4 条：建档默认角色为 DEFAULT_ROLE 渠道、无授予人；运维 grant-role 为 OPERATIONS 渠道；运维 revoke-role 以 OPERATIONS 为原因关闭并写回审计条目 ID")
    void channelsAndCausesFollowTheEntrance() throws Exception {
        SignedIn person = person();
        Grant defaultRole = grants(person.userId(), "employee").getFirst();
        assertThat(defaultRole.channel()).isEqualTo("DEFAULT_ROLE");
        assertThat(defaultRole.grantedBy()).isNull();
        assertThat(defaultRole.grantAuditEntryId()).isNotNull();
        assertThat(grants(person.userId())).as("only the default role at first").hasSize(1);

        grantByOperations(person.employeeId(), "business_admin");
        Grant granted = grants(person.userId(), "business_admin").getFirst();
        assertThat(granted.channel()).isEqualTo("OPERATIONS");
        assertThat(granted.grantedBy()).isNull();
        assertThat(granted.open()).isTrue();

        Instant later = NOW.plus(Duration.ofMinutes(2));
        clock.set(later);
        assertThat(operationsRevoke(person.employeeId(), "business_admin")).isZero();

        Grant revoked = grants(person.userId(), "business_admin").getFirst();
        assertThat(revoked.id()).isEqualTo(granted.id());
        assertThat(revoked.cause()).isEqualTo("OPERATIONS");
        assertThat(revoked.to()).isEqualTo(later);
        assertThat(audit(revoked.revocationAuditEntryId()).id()).isEqualTo(revoked.revocationAuditEntryId());
        assertThat(grants(person.userId(), "employee").getFirst().open()).isTrue();
    }
}
