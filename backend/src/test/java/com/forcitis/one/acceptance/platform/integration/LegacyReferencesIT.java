package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.organization.workforce.application.RefreshShortNames;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P3b, ADR-0033 section 2 items 6 and 9, ADR-0039 item 5.2, NEXT_BATCH_PLAN C8 (card section 3.2 items 5 to 7): the old system names
 * people and departments by its own ids; they are translated through the (LEGACY_SYSTEM, EMPLOYEE / ORG_UNIT) source references in
 * platform.integration. Until P6 imports those references none exist, so the old to-dos and the short names are "unavailable": the merged
 * inbox says so and alerts instead of showing 0 old items, and a short name without a reference is neither written nor cleared, with an
 * alert. With a reference the old item and the short name come through. The old system's rows here carry the very number the employee
 * or department had before P3b, so a reader that still takes the own id as the old system's id is caught.
 */
class LegacyReferencesIT extends OrgSourceWorldSupport {

    static final TestDatabase postgres = TestPostgres.database(LegacyReferencesIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String INBOX = "/api/v1/platform/approval-tasks";
    static final String EMPLOYEE = "EMPLOYEE";
    static final String ORG_UNIT = "ORG_UNIT";

    @Autowired
    RefreshShortNames refreshShortNames;

    private String shortNameOf(UUID unit) throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce");
             PreparedStatement select = workforce.prepareStatement("select short_name from org_units where id::text = ?")) {
            select.setString(1, text(unit));
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).as("unit %s is in the snapshot", unit).isTrue();
                return rs.getString(1);
            }
        }
    }

    private void shortName(UUID unit, String shortName) throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce");
             PreparedStatement update = workforce.prepareStatement("update org_units set short_name = ? where id::text = ?")) {
            update.setString(1, shortName);
            update.setString(2, text(unit));
            update.executeUpdate();
        }
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 2 项第 6 条 / C8：没有老系统对照的部门简称不写入、不清除现值，并告警；有对照的部门照常取得简称")
    void aShortNameWithoutReferenceIsNeitherWrittenNorCleared() throws Exception {
        long legacyUnit = 6_001;
        UUID mapped = adoptId(allocate(LEGACY_SYSTEM, ORG_UNIT, legacyUnit));
        orgUnit(mapped, null, null);
        UUID unmapped = newOrgUnitId();
        orgUnit(unmapped, null, null);
        shortName(unmapped, "Old short name");
        legacyShortName(legacyUnit, "New short name");
        legacyShortName(serialOf(unmapped), null);
        int mark = logMark();

        refreshShortNames.execute();

        assertThat(shortNameOf(mapped)).as("the department with a legacy reference").isEqualTo("New short name");
        assertThat(shortNameOf(unmapped)).as("the department without a legacy reference keeps its short name").isEqualTo("Old short name");
        assertThat(alertsSince(mark)).as("alerts for the short name without a legacy reference").isNotEmpty();
    }
}
