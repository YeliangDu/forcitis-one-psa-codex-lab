package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * DATA-REPAIR-workforce PR-8b (WF-11, length limits of one-data's ruling of 2026-10-16): a text column takes exactly its limit and refuses
 * one character more, with the constraint's name and SQLSTATE 23514; a correction reason of 201 characters is refused at the public
 * entry before anything is written, 200 goes through. The limits are the declared widths of the organisation source views (500 for names
 * and codes, 100 for the e-mail address, 255 for the short name) and 200 for the reason, as the release and forced-logout reasons.
 * Expected red until the constraints and the entry check exist.
 */
class WorkforceLengthLimitsIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(WorkforceLengthLimitsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String CHECK_VIOLATION = "23514";
    static final String EMPLOYEES_PATH = "/api/v1/organization/employee-legal-entities/";

    UUID unit;

    @BeforeEach
    void aDepartment() throws SQLException {
        unit = newOrgUnitId();
        orgUnit(unit, null, null);
    }

    private void execute(String sql, Object... args) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement statement = workforce.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    private static void refusedBy(ThrowingStatement statement, String constraint) {
        SQLException refused = catchThrowableOfType(SQLException.class, statement::run);
        assertThat((Throwable) refused).as("the statement is refused by %s", constraint).isNotNull();
        assertThat(refused.getSQLState()).as(refused.getMessage()).isEqualTo(CHECK_VIOLATION);
        assertThat(refused.getMessage()).contains("\"" + constraint + "\"");
    }

    @FunctionalInterface
    interface ThrowingStatement {
        void run() throws SQLException;
    }

    /** constraint, insert with the checked value as the only ?, limit. */
    static Stream<Arguments> limitedColumns() {
        return Stream.of(
                Arguments.of("ck_positions_name", "insert into positions (id, name, status) values (uuidv7(), ?, 'ACTIVE')", 500),
                Arguments.of("ck_positions_code", "insert into positions (id, code, name, status) values (uuidv7(), ?, 'Synthetic position', 'ACTIVE')", 500),
                Arguments.of("ck_org_units_name", "insert into org_units (id, name, status) values (uuidv7(), ?, 'ACTIVE')", 500),
                Arguments.of("ck_org_units_code", "insert into org_units (id, code, name, status) values (uuidv7(), ?, 'Synthetic unit', 'ACTIVE')", 500),
                Arguments.of("ck_org_units_short_name",
                        "insert into org_units (id, short_name, name, status) values (uuidv7(), ?, 'Synthetic unit', 'ACTIVE')", 255),
                Arguments.of("ck_employees_employee_code",
                        "insert into employees (id, employee_code, display_name, employment_status) values (uuidv7(), ?, 'Synthetic person', 'EMPLOYED')", 500),
                Arguments.of("ck_employees_display_name",
                        "insert into employees (id, employee_code, display_name, employment_status) values (uuidv7(), 'WF11-' || uuidv7()::text, ?, 'EMPLOYED')", 500),
                Arguments.of("ck_employees_email_address", """
                        insert into employees (id, employee_code, display_name, email, employment_status)
                        values (uuidv7(), 'WF11-' || uuidv7()::text, 'Synthetic person', ?, 'EMPLOYED')""", 100));
    }

    @ParameterizedTest(name = "DATA-REPAIR-workforce WF-11：{0} 恰为上限 {2} 个字符通过，上限加 1 被拒")
    @MethodSource("limitedColumns")
    void wf11TextColumnTakesExactlyItsLimit(String constraint, String insert, int limit) throws SQLException {
        execute(insert, "A".repeat(limit));
        refusedBy(() -> execute(insert, "A".repeat(limit + 1)), constraint);
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-11：ck_org_unit_versions_name 与 ck_org_unit_versions_code 恰为 500 个字符通过，501 个被拒")
    void wf11OrgUnitVersionNameAndCodeLimit() throws SQLException {
        String name = """
                insert into org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, effective_to_date, name, status, is_delivery_unit)
                values (uuidv7(), ?, ?, ?, ?, 'ACTIVE', false)""";
        String code = """
                insert into org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, effective_to_date, code, name, status, is_delivery_unit)
                values (uuidv7(), ?, ?, ?, ?, 'Synthetic unit', 'ACTIVE', false)""";
        execute(name, stored(unit), TODAY.minusDays(40), TODAY.minusDays(35), "A".repeat(500));
        refusedBy(() -> execute(name, stored(unit), TODAY.minusDays(30), TODAY.minusDays(25), "A".repeat(501)), "ck_org_unit_versions_name");
        execute(code, stored(unit), TODAY.minusDays(20), TODAY.minusDays(15), "A".repeat(500));
        refusedBy(() -> execute(code, stored(unit), TODAY.minusDays(10), TODAY.minusDays(5), "A".repeat(501)), "ck_org_unit_versions_code");
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-11：更正表的 correction_reason 恰为 200 个字符通过，201 个被 ck_legal_entity_attribution_corrections_reason 拒绝")
    void wf11CorrectionReasonColumnLimit() throws SQLException {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        execute("insert into legal_entity_attributions (attribution_id, org_unit_id, employee_id) values (uuidv7(), null, ?)", stored(person));
        String insert = """
                insert into legal_entity_attribution_corrections (correction_id, employee_id, period_effective_from_date,
                    previous_effective_to_date, new_effective_to_date, correction_reason, corrected_by_user_id)
                values (uuidv7(), ?, ?, ?, ?, ?, uuidv7())""";
        execute(insert, stored(person), TODAY.minusDays(60), TODAY.minusDays(20), TODAY.minusDays(19), "A".repeat(200));
        refusedBy(() -> execute(insert, stored(person), TODAY.minusDays(60), TODAY.minusDays(19), TODAY.minusDays(18), "A".repeat(201)),
                "ck_legal_entity_attribution_corrections_reason");
    }

    private long correctionsOf(UUID employee) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement count = workforce.prepareStatement(
                "select count(*) from legal_entity_attribution_corrections where employee_id = ?")) {
            count.setObject(1, stored(employee));
            try (ResultSet rs = count.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private MockHttpServletResponse correct(SignedIn caller, UUID employee, String etag, String reason) throws Exception {
        return mvc.perform(post(EMPLOYEES_PATH + text(employee) + ":correct").header("If-Match", etag).with(csrf()).with(session(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"periodFrom\":\"" + TODAY.minusDays(30) + "\",\"effectiveTo\":\"" + TODAY.minusDays(20) + "\",\"reason\":\"" + reason + "\"}"))
                .andReturn().getResponse();
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-11：更正原因 201 个字符经公开接口被拒（422 ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED，更正表不写），200 个字符通过")
    void wf11CorrectionReasonOverTheEntryLimitIsRefusedBeforeAnyWrite() throws Exception {
        UUID otherUnit = newOrgUnitId();
        orgUnit(otherUnit, null, null);
        UUID maintainer = newEmployeeId();
        UUID subject = newEmployeeId();
        employee(maintainer, unit, null);
        employee(subject, otherUnit, null);
        grantByOperations(maintainer, "org_data_admin");
        SignedIn session = signIn(maintainer);
        UUID first = createLegalEntity(session, "LNA", "Synthetic length A");
        UUID second = createLegalEntity(session, "LNB", "Synthetic length B");

        attributeEmployee(session, subject, first);
        MockHttpServletResponse later = mvc.perform(put(EMPLOYEES_PATH + text(subject)).header("If-Match", "\"1\"").with(csrf()).with(session(session))
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalEntityId\":\"" + second + "\",\"effectiveFrom\":\"" + TODAY.minusDays(10) + "\"}"))
                .andReturn().getResponse();
        assertThat(later.getStatus()).as("the second attribution closes the first period: " + later.getContentAsString()).isEqualTo(200);
        String etag = later.getHeader("ETag");

        MockHttpServletResponse tooLong = correct(session, subject, etag, "A".repeat(201));
        assertThat(tooLong.getStatus()).as(tooLong.getContentAsString()).isEqualTo(422);
        assertThat((String) JsonPath.read(tooLong.getContentAsString(), "$.errorCode")).isEqualTo("ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED");
        assertThat(correctionsOf(subject)).as("a refused reason writes nothing").isZero();

        MockHttpServletResponse accepted = correct(session, subject, etag, "A".repeat(200));
        assertThat(accepted.getStatus()).as(accepted.getContentAsString()).isEqualTo(200);
        assertThat(correctionsOf(subject)).isEqualTo(1);
    }
}
