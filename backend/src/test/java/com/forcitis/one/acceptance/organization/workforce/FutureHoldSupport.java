package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.integration.api.WecomCard;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;

/**
 * Ground of the #931 tests: held separations whose effective date is after the business today, the due-date job (reached by name, see
 * {@link PersonnelCommandNames#DUE_JOB_TYPE}), and the WeCom cards handed to the gateway. The gateway is the outbound double of every
 * acceptance test (an external system, not the path under test); the hold, the release, the job, the tables and the events are all real.
 */
abstract class FutureHoldSupport extends SeparationHoldSupport {

    /** The original effective date of the future-dated separations: after every date the tests move the clock to before the job runs. */
    protected static final LocalDate DUE = TODAY.plusDays(10);

    @Autowired
    private ApplicationContext beans;

    // ---- entering ----

    /** A held SEPARATE of the administrator entered by the 人事专员 that takes effect on {@code effective} (last working day = the day before). */
    protected Held heldSeparationOn(Staff leaver, LocalDate effective) throws Exception {
        actIn(hr);
        UUID command = separate(leaver.employeeId(), effective.minusDays(1));
        assertThat(commandStatus(command)).as("the separation of the administrator is held").isEqualTo(HELD);
        assertThat(command(command).get("effective_date")).as("the effective date is the day after the last working day").isEqualTo(effective);
        return new Held(leaver.employeeId(), command, holdIdOf(command));
    }

    /** Like {@link #heldWithRemaining}, with a separation that takes effect on {@code effective}; released it leaves 1 + extra administrators. */
    protected Scenario heldOnWithRemaining(int extra, LocalDate effective) throws Exception {
        Staff leaver = administrator();
        Staff stays = administrator();
        Staff releaser = auditor();
        Held held = heldSeparationOn(leaver, effective);
        for (int i = 0; i < extra; i++) {
            administrator();
        }
        return new Scenario(held, leaver, releaser, stays);
    }

    /** The releaser in a fresh session: needed after the clock moved, sessions of the earlier time are not relied on. */
    protected Staff signedInAgain(Staff who) throws Exception {
        return new Staff(who.employeeId(), signIn(who.employeeId()));
    }

    // ---- the due-date job ----

    /** Runs the due-date job once, with nobody signed in (it is a system run). A missing bean or method fails the test, it is never skipped. */
    protected void runDueJob() {
        SecurityContextHolder.clearContext();
        List<String> similar = new ArrayList<>();
        for (String name : beans.getBeanDefinitionNames()) {
            Class<?> type = beans.getType(name);
            if (type == null) {
                continue;
            }
            Class<?> user = ClassUtils.getUserClass(type);
            if (!user.getSimpleName().equals(DUE_JOB_TYPE)) {
                if (user.getName().startsWith("com.forcitis.one.organization.workforce") && user.getSimpleName().matches(".*(Job|Due).*")) {
                    similar.add(user.getName());
                }
                continue;
            }
            Method run;
            try {
                run = user.getMethod(DUE_JOB_METHOD);
            } catch (NoSuchMethodException missing) {
                throw new AssertionError("ADR-0068 item 1.8: " + user.getName() + " has no public no-argument method " + DUE_JOB_METHOD + "() (agreed in PersonnelCommandNames)");
            }
            try {
                run.invoke(beans.getBean(name));
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(failure.getCause());
            } catch (IllegalAccessException broken) {
                throw new AssertionError("cannot call " + run, broken);
            }
            return;
        }
        throw new AssertionError("ADR-0068 item 1.8: no bean of a type named " + DUE_JOB_TYPE + " (the due-date job; 暂名 in PersonnelCommandNames); workforce jobs seen: " + similar);
    }

    // ---- reading ----

    /** The ids of the current rows of a version table of the employee: equal before and after means nothing was rewritten. */
    protected List<Object> currentVersionIds(String table, UUID employee) throws Exception {
        List<Object> ids = new ArrayList<>();
        String column = table.equals(EMPLOYEE_VERSIONS) ? "employee_version_id" : "assignment_version_id";
        for (Map<String, Object> row : currentVersions(table, employee)) {
            ids.add(row.get(column));
        }
        return ids;
    }

    /** W2 design section 3.4 (W-17): the open assignment closes at the separation day; current rows follow each other without a gap. */
    protected static void assertAssignmentEndsOn(List<Map<String, Object>> current, LocalDate separationDay) {
        assertThat(current).isNotEmpty();
        for (int i = 1; i < current.size(); i++) {
            assertThat(current.get(i - 1).get("effective_to_date")).as("row " + (i - 1) + " ends where row " + i + " starts").isEqualTo(current.get(i).get("effective_from_date"));
        }
        assertThat(current.getLast().get("effective_to_date")).as("the last assignment row ends on the separation day").isEqualTo(separationDay);
    }

    /** The separation is written at its date: employed the day before, separated from the day; the timeline has no gap and ends open. */
    protected void assertSeparatedFrom(UUID employee, LocalDate day) throws Exception {
        List<Map<String, Object>> current = currentVersions(EMPLOYEE_VERSIONS, employee);
        assertContiguous(current);
        Map<String, Object> separated = inForceOn(current, day);
        assertThat(separated).as("a current employee version covers " + day).isNotNull();
        assertThat(separated.get("employment_status")).isEqualTo(SEPARATED);
        assertThat(separated.get("effective_from_date")).as("written at the original date, not at the day of the release").isEqualTo(day);
        assertThat(inForceOn(current, day.minusDays(1)).get("employment_status")).isEqualTo(EMPLOYED);
        assertAssignmentEndsOn(currentVersions(ASSIGNMENT_VERSIONS, employee), day);
    }

    // ---- the cards ----

    /** Forgets the cards handed to the gateway so far (earlier fixture steps of the same test). */
    protected void forgetCards() {
        Mockito.clearInvocations(wecomGateway);
    }

    protected List<WecomCard> cards() {
        ArgumentCaptor<WecomCard> captured = ArgumentCaptor.forClass(WecomCard.class);
        Mockito.verify(wecomGateway, Mockito.atLeast(0)).send(captured.capture());
        return captured.getAllValues();
    }

    /** The WeCom user ids on the card, sorted; duplicates are kept, so that "once, not twice" can be asserted. */
    protected static List<String> recipientsOf(WecomCard card) {
        List<String> ids = new ArrayList<>();
        card.wecomUserIds().forEach(id -> ids.add(id.value()));
        ids.sort(String::compareTo);
        return ids;
    }

    protected static Set<String> wecomIdsOf(Staff... people) {
        Set<String> ids = new TreeSet<>();
        for (Staff person : people) {
            ids.add(wecomUserIdOf(person.employeeId()));
        }
        return ids;
    }
}
