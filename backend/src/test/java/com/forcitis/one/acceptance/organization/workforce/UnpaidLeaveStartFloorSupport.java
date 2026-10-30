package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.integration.api.WecomCard;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ground of the #943 tests (ADR-0068 sections 2.2, 2.3 and 3; user decision of 2026-10-30 「加，同离职一样」, and 「已录的未来停薪算已走」): starting
 * unpaid leave for a super_admin or security_auditor holder is floor-protected like a separation. The personnel command path has no HTTP surface, so
 * the refusal is read as the business refusal the web layer turns into HTTP 422 with the agreed code. The WeCom gateway is the outbound double of
 * every acceptance test, so the queued delivery (subject kind, subject id, purpose) is read from the card handed to it.
 */
abstract class UnpaidLeaveStartFloorSupport extends FutureHoldSupport {

    protected static final String NOT_ALLOWED_CODE = "ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_ALLOWED";
    protected static final String ALERT_SUBJECT_KIND = "PERSONNEL_COMMAND";
    protected static final String ALERT_PURPOSE = "UNPAID_LEAVE_START_NOTICE";
    /** An entered, not yet started leave. */
    protected static final LocalDate LATER = TODAY.plusDays(5);

    protected UUID startLeave(Staff who, LocalDate effective) throws Exception {
        actIn(hr);
        return enter(who.employeeId(), UNPAID_LEAVE_START, effective);
    }

    protected Throwable tryStartLeave(Staff who, LocalDate effective) throws Exception {
        actIn(hr);
        return catchThrowable(() -> enter(who.employeeId(), UNPAID_LEAVE_START, effective));
    }

    /** A start the floor must let through (the fixture, or a case that is accepted): it takes effect today or is entered for later. */
    protected UUID startAccepted(Staff who, LocalDate effective) throws Exception {
        UUID command = startLeave(who, effective);
        assertThat(commandStatus(command)).isEqualTo(effective.isAfter(TODAY) ? PENDING : EFFECTIVE);
        return command;
    }

    protected void disableAccount(Staff who) throws Exception {
        try (Connection identity = schemaConnection("platform_identity")) {
            update(identity, "update user_accounts set status = 'DISABLED' where employee_id = ?", stored(who.employeeId()));
        }
    }

    // ---- what a refusal must leave behind: nothing ----

    protected record Snapshot(long commands, long personCommands, int employeeVersions, int entered, long deliveries, int cards) {
    }

    protected Snapshot snapshot(Staff who) throws Exception {
        long all = (long) rows("select count(*) as n from " + COMMANDS).get(0).get("n");
        long deliveries;
        try (Connection integration = schemaConnection("platform_integration")) {
            deliveries = (long) query(integration, "select count(*) as n from outbound_deliveries").get(0).get("n");
        }
        return new Snapshot(all, commandCount(who.employeeId()), allVersions(EMPLOYEE_VERSIONS, who.employeeId()).size(), auditCount(AUDIT_ENTERED), deliveries,
                cards().size());
    }

    private static DomainException domainRefusal(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DomainException domain) {
                return domain;
            }
        }
        return null;
    }

    protected static void assertFloorRefusal(Throwable refused) {
        assertThat(refused).as("starting this leave would leave no available super administrator: refused").isNotNull();
        DomainException domain = domainRefusal(refused);
        assertThat(domain).as("a business refusal, not something else: " + refused).isNotNull();
        assertThat(domain.kind()).isEqualTo(ErrorKind.UNPROCESSABLE);
        assertThat(domain.kind().status()).isEqualTo(422);
        assertThat(domain.errorCode().code()).isEqualTo(NOT_ALLOWED_CODE);
    }

    /** Refused with 422 and the agreed code, and no command row, employee version, command audit entry, delivery or card came out of it. */
    protected void assertRefusedAndNothingWritten(Staff who, LocalDate effective) throws Exception {
        forgetCards();
        Snapshot before = snapshot(who);
        int mark = logMark();

        Throwable refused = tryStartLeave(who, effective);

        assertFloorRefusal(refused);
        assertThat(snapshot(who)).as("nothing written").isEqualTo(before);
        assertThat(alertsSince(mark)).as("a refusal raises no alert").isEmpty();
        assertThat(onUnpaidLeaveOn(who.employeeId(), effective)).isFalse();
    }

    // ---- the alert ----

    protected WecomCard theAlertOf(UUID command) {
        List<WecomCard> sent = cardsFor(command);
        assertThat(sent).as("one card for the one start (cards: " + sent + ")").hasSize(1);
        WecomCard card = sent.getFirst();
        assertThat(card.subject().kind().name()).isEqualTo(ALERT_SUBJECT_KIND);
        assertThat(card.subject().purpose().name()).isEqualTo(ALERT_PURPOSE);
        assertThat(card.subject().id()).isEqualTo(command);
        return card;
    }

    /** The card names no person: neither an employee id nor a login/WeCom id of anyone taking part. */
    protected static void assertNoPersonalData(WecomCard card, Staff... people) {
        String text = card.title() + " " + card.description() + " " + card.targetUrl();
        for (Staff person : people) {
            assertThat(text).doesNotContain(person.employeeId().toString());
            assertThat(text).doesNotContain(wecomUserIdOf(person.employeeId()));
        }
        assertThat(Pattern.compile("(?i)1[3-9]\\d{9}").matcher(text).find()).isFalse();
    }

    protected void assertNoAlert(UUID command, int mark) {
        assertThat(cardsFor(command)).as("no card").isEmpty();
        assertThat(cards().stream().filter(card -> card.subject().purpose().name().equals(ALERT_PURPOSE))).as("no alert card at all").isEmpty();
        assertThat(alertsSince(mark)).as("no ALERT log line").isEmpty();
    }

    protected static void assertAlertLine(List<String> alerts) {
        assertThat(alerts).as("operations: the ALERT log line").anySatisfy(line -> assertThat(line).startsWith("ALERT organization.workforce"));
    }
}
