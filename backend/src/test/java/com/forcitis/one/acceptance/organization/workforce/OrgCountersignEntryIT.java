package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.ORG_DATA_ADMIN_ROLE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #910 acceptance part (#909 F4a), note org-structure-commands.md section 5 rule 1 and rule 5 (drafter), DD amendment D on #910 (who is
 * affected): at entry of ASSIGN_LEGAL_ENTITY, REPARENT or MERGE, workforce computes each affected employee's cost legal entity on the
 * effective date before and after; any difference makes the command AWAITING_ATTRIBUTION_COUNTERSIGN with one basis row per changed
 * employee and one CHANGE request per legal entity in previous ∪ new, in the same transaction; no difference takes the normal path.
 */
class OrgCountersignEntryIT extends OrgCountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgCountersignEntryIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final LocalDate DAY = TODAY.plusDays(10);

    @Test
    @DisplayName("#910 note §5 第 1 条、修正案 D：给没有自有归属的单元指定法人（E1 → E2），继承该单元的在职员工成本法人改变：命令待法人归属会签；会签依据每人一行（之前 E1、之后 E2）；E1、E2 各一行 CHANGE 请求，请求人与起草人都是录入人，序号 1、未决；不写单元归属版本")
    void assigningALegalEntityThatChangesPeopleAwaitsTheCountersign() throws Exception {
        UUID unitX = seedUnit(topE1);
        UUID first = inheritingIn(unitX);
        UUID second = inheritingIn(unitX);

        UUID command = assignLegalEntity(unitX, DAY, e2);

        assertThat(statusOf(command)).isEqualTo(AWAITING);
        assertThat(basis(command)).containsOnlyKeys(first, second).allSatisfy((employee, change) -> assertThat(change).containsExactly(e1, e2));
        assertThat(requestedEntities(command, KIND_CHANGE)).containsExactlyInAnyOrder(e1, e2);
        assertThat(requests(command, KIND_CHANGE)).hasSize(2).allSatisfy(row -> {
            assertThat(fromStored(row.get("drafted_by"))).isEqualTo(orgAdmin.userId());
            assertThat(fromStored(row.get("requested_by"))).isEqualTo(orgAdmin.userId());
            assertThat(((Number) row.get("request_sequence_number")).intValue()).isEqualTo(1);
            assertThat(row.get("decision_kind")).isNull();
            assertThat(row.get("personnel_command_id")).as("an org row has no personnel parent (#908 DD ck_attribution_countersigns_parent)").isNull();
            assertThat(row.get("employee_id")).isNull();
        });
        assertThat(unitAttributionRowsWrittenBy(command)).as("note §5 rule 2: no versions while awaiting").isZero();
        assertThat(unitAttributions(unitX)).isEmpty();
    }

    @Test
    @DisplayName("#910 修正案 D「受影响员工」：只算那天在职、主任职在目标单元或其下没有自有归属的单元里的人；有自有法人归属的人（成本法人不变）、那天已离职的人、下级单元有自有归属的人、别的单元的人都不进会签依据")
    void onlyThePeopleWhoseCostLegalEntityChangesAreInTheBasis() throws Exception {
        UUID unitX = seedUnit(topE1);
        UUID childInheriting = seedUnit(unitX);
        UUID childOwn = attributedUnit(unitX, e3);
        UUID inX = inheritingIn(unitX);
        UUID inChild = inheritingIn(childInheriting);
        UUID ownAttribution = employedIn(unitX, REGULAR, true);
        UUID inOwnChild = inheritingIn(childOwn);
        UUID leaver = inheritingIn(unitX);
        UUID elsewhere = inheritingIn(topE1);
        actInSpecialist();
        separate(leaver, DAY.minusDays(1));
        actIn(orgAdmin);

        UUID command = assignLegalEntity(unitX, DAY, e2);

        assertThat(basis(command)).containsOnlyKeys(inX, inChild);
        assertThat(basis(command)).doesNotContainKeys(ownAttribution, inOwnChild, leaver, elsewhere);
    }

    @Test
    @DisplayName("#910 note §5 第 1 条（调整上级）：把没有自有归属的单元从 E1 的上级挂到 E2 的上级下，继承它的人成本法人 E1 → E2：待会签，依据与请求同上；挂到同一法人的另一上级下没有变化，走普通路径（待生效、无请求、无依据）")
    void reparentingAcrossLegalEntitiesAwaitsAndWithinOneDoesNot() throws Exception {
        UUID unitX = seedUnit(topE1);
        UUID person = inheritingIn(unitX);
        UUID otherE1Parent = attributedUnit(null, e1);
        UUID unitY = seedUnit(topE1);
        inheritingIn(unitY);

        UUID across = reparent(unitX, DAY, topE2);
        UUID within = reparent(unitY, DAY, otherE1Parent);

        assertThat(statusOf(across)).isEqualTo(AWAITING);
        assertThat(basis(across)).containsOnlyKeys(person).containsEntry(person, List.of(e1, e2));
        assertThat(requestedEntities(across, KIND_CHANGE)).containsExactlyInAnyOrder(e1, e2);
        assertThat(statusOf(within)).isEqualTo(PENDING);
        assertThat(requests(within, KIND_CHANGE)).isEmpty();
        assertThat(basis(within)).isEmpty();
    }

    @Test
    @DisplayName("#910 note §5 第 1 条（合并）：被并入单元（继承 E1）并入 E2 的单元：被并入单元里继承法人的人进依据（E1 → E2），有自有归属的人不进；待会签。全部人都有自有归属的合并不需会签，走普通路径")
    void aMergeAcrossLegalEntitiesAwaitsUnlessNobodyChanges() throws Exception {
        UUID merged = seedUnit(topE1);
        UUID receiving = seedUnit(topE2);
        UUID inheriting = inheritingIn(merged);
        UUID own = employedIn(merged, REGULAR, true);
        UUID mergedOwnOnly = seedUnit(topE1);
        employedIn(mergedOwnOnly, REGULAR, true);

        UUID across = merge(merged, receiving, DAY);
        UUID unchanged = merge(mergedOwnOnly, seedUnit(topE2), DAY);

        assertThat(statusOf(across)).isEqualTo(AWAITING);
        assertThat(basis(across)).containsOnlyKeys(inheriting).doesNotContainKey(own).containsEntry(inheriting, List.of(e1, e2));
        assertThat(requestedEntities(across, KIND_CHANGE)).containsExactlyInAnyOrder(e1, e2);
        assertThat(statusOf(unchanged)).isEqualTo(PENDING);
        assertThat(requests(unchanged, KIND_CHANGE)).isEmpty();
        assertThat(basis(unchanged)).isEmpty();
    }

    @Test
    @DisplayName("#910 note §5 第 1 条「无差异走普通路径」、§4.4「请求集合 = 依据里之前与之后法人的并集」：给没有人继承的单元指定法人，不需会签；给有人的单元指定成同一法人，也不需会签；请求集合是依据的之前与之后法人的并集（E3 → E2 只有 E3、E2 两行，与世界法人 E1 无关）")
    void noChangeNeedsNoCountersignAndTheRequestSetIsTheUnion() throws Exception {
        UUID empty = seedUnit(topE1);
        UUID sameEntity = seedUnit(topE1);
        inheritingIn(sameEntity);
        long before = allRequestRows();

        UUID nobody = assignLegalEntity(empty, DAY, e2);
        UUID same = assignLegalEntity(sameEntity, DAY, e1);

        assertThat(statusOf(nobody)).isEqualTo(PENDING);
        assertThat(statusOf(same)).isEqualTo(PENDING);
        assertThat(allRequestRows()).isEqualTo(before);

        UUID fromE3 = inheritingIn(seedUnit(topE3));
        UUID mergedE3 = unitOf(fromE3, TODAY);
        UUID union = merge(mergedE3, seedUnit(topE2), DAY);
        assertThat(basis(union)).containsEntry(fromE3, List.of(e3, e2));
        assertThat(requestedEntities(union, KIND_CHANGE)).as("one row per entity in previous ∪ new, nothing for E1").containsExactlyInAnyOrder(e3, e2);
    }

    @Test
    @DisplayName("#910 note §5 第 5 条（起草人）：组织数据管理员自己的主任职所在单元链法人会被改变时录入被拒（指定法人、调整上级、合并都拒，rv1041x M3），不留命令行与请求；另一位组织数据管理员可以录入同一命令")
    void theDrafterMayNotChangeTheirOwnCostLegalEntity() throws Exception {
        UUID unitX = seedUnit(topE1);
        UUID selfId = inheritingIn(unitX);
        grantByOperations(selfId, ORG_DATA_ADMIN_ROLE);
        Staff self = new Staff(selfId, signIn(selfId));
        actIn(self);
        long commands = orgCommandCount();
        long requests = allRequestRows();

        assertRefused(() -> assignLegalEntity(unitX, DAY, e2));
        assertRefused(() -> reparent(unitX, DAY, topE2));
        assertRefused(() -> merge(unitX, seedUnit(topE2), DAY));

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(allRequestRows()).isEqualTo(requests);
        actIn(orgAdmin);
        UUID command = assignLegalEntity(unitX, DAY, e2);
        assertThat(statusOf(command)).isEqualTo(AWAITING);
        assertThat(basis(command)).containsKey(selfId);
    }

    @Test
    @DisplayName("#910 note §3.1、§4.3：会签依据每条命令每人一行，只在录入时写；同一单元同一天已有待会签命令时，另一条同日命令被拒（未生效命令每单元每天一条）")
    void theBasisIsWrittenOnceAndTheDayIsOccupied() throws Exception {
        UUID unitX = seedUnit(topE1);
        UUID person = inheritingIn(unitX);
        UUID command = assignLegalEntity(unitX, DAY, e2);
        Map<UUID, List<UUID>> written = basis(command);

        assertRefused(() -> assignLegalEntity(unitX, DAY, e3));

        assertThat(basis(command)).isEqualTo(written).containsOnlyKeys(person);
        assertThat(rows("select count(*) as n from " + BASES + " where employee_id = ?", stored(person)).get(0).get("n")).isEqualTo(1L);
        assertThat(Set.copyOf(requests(command, KIND_CHANGE).stream().map(row -> row.get("legal_entity_id")).toList())).containsExactlyInAnyOrder(e1, e2);
    }
}
