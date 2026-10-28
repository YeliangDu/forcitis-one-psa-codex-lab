package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * ADR-0034 decision 1 item 6 (「实际钟点 :now 写进审计 detail」) and guard rule 5 (角色版本同理); ADR-0034-IMPL card rows 0034-G5-02 and
 * 0034-G5-03 (时钟回拨 … 审计 detail 记实际钟点) with card C4 (审计 role-version.opened / closed detail：…、实际钟点). The version's own start is
 * greatest(:now, current start) and is a column; the audit detail is the one place that keeps what the clock really read.
 * <p>
 * What card and ADR fix: the detail holds the actual clock reading, not the (later) effective time. What they leave open is the text form
 * (see PR2_ACCEPTANCE.md, 「G5-03 时刻格式」): the test therefore reads every ISO-8601 date-time with an offset (Z or +hh:mm) out of the detail,
 * compares them as instants, and names no key and no digit count. The clock reading must be one of them. Written in that form, the
 * reading is machine-readable, unambiguous across time zones and equal to what the timestamptz columns hold.
 */
@Testcontainers
class RoleVersionAuditDetailIT extends RoleVersioningSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    private static final Pattern ISO_DATE_TIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:Z|[+-]\\d{2}:\\d{2})");

    private static Definition definition(String name, String... codes) {
        return new Definition(name, true, "SELF", List.of(codes));
    }

    /** Every ISO-8601 date-time with an offset in the text, as instants; an unparsable match fails the test (the form is wrong). */
    private static List<Instant> instantsIn(String detail) {
        List<Instant> found = new ArrayList<>();
        Matcher matcher = ISO_DATE_TIME.matcher(detail);
        while (matcher.find()) {
            found.add(OffsetDateTime.parse(matcher.group(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant());
        }
        return found;
    }

    private String detailOf(UUID auditEntryId) throws SQLException {
        try (Connection audit = schemaConnection("platform_audit"); PreparedStatement select = audit.prepareStatement("select detail from audit_entries where id = ?")) {
            select.setObject(1, auditEntryId);
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).as("the audit entry %s exists", auditEntryId).isTrue();
                return rs.getString(1);
            }
        }
    }

    @Test
    @DisplayName("0034-G5-03 审计 detail 的时刻：时钟回拨后修订，新版本开始时刻晚于时钟，其开启审计 detail 含 ISO-8601（带偏移）形式的、等于注入时钟读数的时刻；时钟未回拨时 detail 的时刻等于该读数；回拨后删除，关闭审计 detail 同样含当时的钟点而不是关闭时刻")
    void theAuditDetailKeepsTheActualClockReadingAsAnIsoDateTime() throws Exception {
        SignedIn administrator = roleAdministrator();
        String code = "rv_audit_" + UUID.randomUUID().toString().substring(0, 8);
        String base = "Role versioning " + code;
        UUID role = createdRole(administrator, code, definition(base + " 1", PLAIN_CODES.get(0)));
        Instant t2 = NOW.plus(Duration.ofMinutes(20));
        clock.set(t2);
        assertThat(updateRole(administrator, role, definition(base + " 2", PLAIN_CODES.get(0))).getStatus()).isEqualTo(200);
        Instant behind = NOW.plus(Duration.ofMinutes(10));
        clock.set(behind);

        MockHttpServletResponse revised = updateRole(administrator, role, definition(base + " 3", PLAIN_CODES.get(0)));

        assertThat(revised.getStatus()).as(revised.getContentAsString()).isEqualTo(200);
        List<VersionRow> versions = versionsOf(role);
        assertThat(versions).hasSize(3);
        assertThat(versions.get(2).from()).as("the fixture: the new version starts later than the clock reads").isEqualTo(t2).isAfter(behind);
        String openedDetail = detailOf(versions.get(2).versionAuditEntryId());
        assertThat(instantsIn(openedDetail)).as("detail of the audit entry that opened version 3, clock reading %s: %s", behind, openedDetail).contains(behind);
        assertThat(instantsIn(detailOf(versions.get(1).versionAuditEntryId()))).as("version 2 was written when the clock read %s", t2).contains(t2);

        Instant behindAgain = NOW.plus(Duration.ofMinutes(5));
        clock.set(behindAgain);

        MockHttpServletResponse deleted = deleteRole(administrator, role);

        assertThat(deleted.getStatus()).as(deleted.getContentAsString()).isEqualTo(204);
        VersionRow last = versionsOf(role).getLast();
        assertThat(last.to()).as("the fixture: the version is closed later than the clock reads").isEqualTo(t2).isAfter(behindAgain);
        String closedDetail = detailOf(last.closureAuditEntryId());
        assertThat(instantsIn(closedDetail)).as("detail of the audit entry that closed version 3, clock reading %s: %s", behindAgain, closedDetail).contains(behindAgain);
    }
}
