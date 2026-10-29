package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P3b, ADR-0033 guard 6 (card section 4 item 1): the organisation source hands out signed 64-bit ids, negative ones included. A
 * department, a person and a position whose source ids are Long.MIN_VALUE and -1 go through the whole way: the adapter allocates own
 * UUIDv7 through the source references, the sync files them under those own ids with every relationship translated, and the next sync
 * finds the same own ids without allocating again. Nothing on the way may assume a positive id.
 */
class NegativeSourceIdIT extends OrgSourceWorldSupport {

    static final TestDatabase postgres = TestPostgres.database(NegativeSourceIdIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final long LOWEST = Long.MIN_VALUE;
    static final long MINUS_ONE = -1L;
    static final String EMPLOYEE = "EMPLOYEE";
    static final String ORG_UNIT = "ORG_UNIT";
    static final String POSITION = "POSITION";

    @Test
    @DisplayName("R-1b P3b ADR-0033 守护 6 NegativeSourceIdIT：A8 来源 ID 为负数（含 Long.MIN_VALUE 与 -1）的部门、人员、岗位，经来源对照映射到自有 UUIDv7，完整走通「分配 → 同步建档 → 再次同步匹配到同一自有 ID」，不出现正数校验")
    void negativeSourceIdsGoTheWholeWay() throws Exception {
        unit(LOWEST, "CN1", "Negative unit", ROOT_PATH + "0009");
        unit(MINUS_ONE, "CN2", "Minus one unit", ROOT_PATH + "0010");
        post(LOWEST, "PN1", "Negative post");
        post(MINUS_ONE, "PN2", "Minus one post");
        member(MINUS_ONE, "Minus one", "EN2", MINUS_ONE, ROOT_PATH + "0010", MINUS_ONE, null, "wx-world-n2", "world-n2");
        member(LOWEST, "Lowest", "EN1", LOWEST, ROOT_PATH + "0009", LOWEST, MINUS_ONE, "wx-world-n1", "world-n1");
        relationship(MINUS_ONE, LOWEST, HEAD_TYPE);
        countAllocations();

        syncOnce();

        UUID lowestUnit = ownIdOf(ORG_UNIT, LOWEST);
        UUID minusOneUnit = ownIdOf(ORG_UNIT, MINUS_ONE);
        UUID lowestPost = ownIdOf(POSITION, LOWEST);
        UUID minusOnePost = ownIdOf(POSITION, MINUS_ONE);
        UUID lowestPerson = ownIdOf(EMPLOYEE, LOWEST);
        UUID minusOnePerson = ownIdOf(EMPLOYEE, MINUS_ONE);
        for (UUID own : new UUID[] {lowestUnit, minusOneUnit, lowestPost, minusOnePost, lowestPerson, minusOnePerson}) {
            assertThat(own.version()).as("own ids are UUIDv7").isEqualTo(7);
        }
        assertThat(inSnapshot("org_units", lowestUnit)).as("the unit with source id Long.MIN_VALUE is filed under its own id").isTrue();
        assertThat(inSnapshot("org_units", minusOneUnit)).isTrue();
        assertThat(inSnapshot("positions", lowestPost)).as("the position with source id Long.MIN_VALUE is filed under its own id").isTrue();
        assertThat(inSnapshot("employees", lowestPerson)).as("the person with source id Long.MIN_VALUE is filed under its own id").isTrue();
        assertThat(inSnapshot("employees", minusOnePerson)).isTrue();
        assertThat(snapshotValue("assignments", lowestPerson, "org_unit_id")).as("department of the lowest person").isEqualTo(lowestUnit.toString());
        assertThat(snapshotValue("assignments", lowestPerson, "position_id")).as("position of the lowest person").isEqualTo(lowestPost.toString());
        assertThat(snapshotValue("assignments", lowestPerson, "reports_to_employee_id")).as("supervisor of the lowest person").isEqualTo(minusOnePerson.toString());
        assertThat(snapshotValue("assignments", minusOnePerson, "position_id")).isEqualTo(minusOnePost.toString());
        assertThat(snapshotValue("org_units", lowestUnit, "head_employee_id")).as("head of the lowest unit").isEqualTo(minusOnePerson.toString());
        int employeeReferences = references(EMPLOYEE);
        int unitReferences = references(ORG_UNIT);
        int positionReferences = references(POSITION);
        countAllocations();

        syncOnce();

        assertThat(ownIdOf(ORG_UNIT, LOWEST)).as("the second sync finds the same own id").isEqualTo(lowestUnit);
        assertThat(ownIdOf(EMPLOYEE, LOWEST)).isEqualTo(lowestPerson);
        assertThat(ownIdOf(POSITION, LOWEST)).isEqualTo(lowestPost);
        assertThat(ownIdOf(EMPLOYEE, MINUS_ONE)).isEqualTo(minusOnePerson);
        assertThat(references(EMPLOYEE)).isEqualTo(employeeReferences);
        assertThat(references(ORG_UNIT)).isEqualTo(unitReferences);
        assertThat(references(POSITION)).isEqualTo(positionReferences);
        assertThat(allocationStatements()).as("objects that have a reference are not allocated again").isZero();
        assertThat(inSnapshot("employees", lowestPerson)).isTrue();
    }
}
