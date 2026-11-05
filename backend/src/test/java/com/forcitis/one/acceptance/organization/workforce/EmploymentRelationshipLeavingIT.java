package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1371 acceptance part, done-when 9 (rest) of the employment-relationship design (ADR-0086) (section 6.2, rows leaving and isLeaving /
 * excludedEmployees): a super administrator with two relationships, one with a HELD SEPARATE and the other on unpaid leave (or with a PENDING
 * UNPAID_LEAVE_START), counts as gone and is not a usable administrator, while one who is leaving on one relationship only still is. The
 * "usable administrator" read is the one of the release path of a separation hold, which counts the remaining available administrators from the
 * leaving set ({@code excludedEmployees}, ADR-0068 item 2.2): a release that would leave nobody available is refused, one that leaves the
 * person available succeeds. Both go through the existing public paths ({@code availabilityOn} and the release), no internal type is used.
 */
class EmploymentRelationshipLeavingIT extends EmploymentRelationshipSupport {

    static final TestDatabase postgres = TestPostgres.database(EmploymentRelationshipLeavingIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** An administrator employed by the world's entity and by another employer, so that he has two relationships; the second is the one to end. */
    private record Dual(Staff person, UUID own, UUID extra) {
    }

    private Dual dualAdministrator() throws Exception {
        Staff person = administrator();
        UUID employer = otherEmployer();
        actIn(hr);
        hireAdditionalEmployer(person.employeeId(), employer, TODAY, null);
        UUID own = relationshipOf(person.employeeId(), legalEntity);
        UUID extra = relationshipOf(person.employeeId(), employer);
        assertThat(extra).as("design 5: the second relationship exists").isNotNull().isNotEqualTo(own);
        return new Dual(person, own, extra);
    }

    private void assertReleaseRefusedForWantOfAnAvailableAdministrator(Scenario scenario, String why) throws Exception {
        Throwable refused = catchThrowable(() -> releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT));

        assertThat(refused).as(why).isInstanceOf(RuntimeException.class);
        assertThat(isPermissionRefusal(refused)).as(why + ": the releaser has the permission, the refusal is about the count: " + refused).isFalse();
        assertThat(holdStatus(scenario.held().hold())).as(why + ": the hold stays").isEqualTo(HELD);
        assertThat(employmentOn(scenario.leaver().employeeId(), TODAY)).as(why + ": and the leaver is still employed").isEqualTo(EMPLOYED);
    }

    @Test
    @DisplayName("#1371 完成标准 9（设计 6.2）：一段关系有被暂停的离职、另一段在停薪留职的超管算即将离职、不是可用超管：放行他人的暂停因无人可用被拒")
    void aHeldSeparationAndAnUnpaidLeaveCountAsLeaving() throws Exception {
        Staff leaver = administrator();
        Dual dual = dualAdministrator();
        Staff releaser = auditor();
        UUID leave = startUnpaidLeave(dual.person().employeeId(), dual.own(), TODAY);
        assertThat(commandStatus(leave)).as("unpaid leave on one of two relationships takes effect on entry").isEqualTo(EFFECTIVE);
        UUID end = endRelationship(dual.person().employeeId(), dual.extra(), TODAY.plusDays(9));
        assertThat(commandStatus(end)).as("the separation of an administrator's relationship is held").isEqualTo(HELD);
        Scenario scenario = new Scenario(heldSeparation(leaver), leaver, releaser, dual.person());

        assertThat(availability(dual.person().employeeId(), TODAY).leaving())
                .as("design 6.2: every in-service relationship is gone (one held for separation, one on unpaid leave) and one is leaving").isTrue();
        assertThat(availability(dual.person().employeeId(), TODAY).onUnpaidLeave()).as("design 6.2: but not all of them are on unpaid leave").isFalse();
        assertReleaseRefusedForWantOfAnAvailableAdministrator(scenario, "design 6.2: the dual administrator is not a usable administrator, so nobody would remain");
    }

    @Test
    @DisplayName("#1371 完成标准 9（设计 6.2）：一段关系有被暂停的离职、另一段有待生效的停薪留职开始的超管同样算即将离职、不是可用超管")
    void aHeldSeparationAndAPendingUnpaidLeaveStartCountAsLeaving() throws Exception {
        Staff leaver = administrator();
        Dual dual = dualAdministrator();
        Staff releaser = auditor();
        UUID start = startUnpaidLeave(dual.person().employeeId(), dual.own(), TODAY.plusDays(4));
        assertThat(commandStatus(start)).as("an unpaid-leave start in the future waits for its day").isEqualTo(PENDING);
        UUID end = endRelationship(dual.person().employeeId(), dual.extra(), TODAY.plusDays(9));
        assertThat(commandStatus(end)).as("the separation of an administrator's relationship is held").isEqualTo(HELD);
        Scenario scenario = new Scenario(heldSeparation(leaver), leaver, releaser, dual.person());

        assertThat(availability(dual.person().employeeId(), TODAY).leaving())
                .as("design 6.2: a pending unpaid-leave start is leaving, so is the held separation of the other relationship").isTrue();
        assertReleaseRefusedForWantOfAnAvailableAdministrator(scenario, "design 6.2: the dual administrator is not a usable administrator, so nobody would remain");
    }

    @Test
    @DisplayName("#1371 完成标准 9（设计 6.2）：只在一段关系上离职的超管不在离职集合里、仍是可用超管：放行他人的暂停成功")
    void anAdministratorLeavingOnOneRelationshipOnlyIsStillUsable() throws Exception {
        Staff leaver = administrator();
        Dual dual = dualAdministrator();
        Staff releaser = auditor();
        UUID end = endRelationship(dual.person().employeeId(), dual.extra(), TODAY.plusDays(9));
        assertThat(commandStatus(end)).as("the separation of an administrator's relationship is held").isEqualTo(HELD);
        Held held = heldSeparation(leaver);

        assertThat(availability(dual.person().employeeId(), TODAY).leaving()).as("design 6.2: the other relationship still works, so the person is not leaving").isFalse();
        releaseAs(releaser, held.hold(), REASON_TEXT);

        assertThat(holdStatus(held.hold())).as("design 6.2: the person leaving on one relationship only is still an available administrator, so the release goes through").isEqualTo(RELEASED);
        assertThat(employmentOn(leaver.employeeId(), TODAY)).as("and the leaver is separated").isEqualTo(SEPARATED);
    }
}
