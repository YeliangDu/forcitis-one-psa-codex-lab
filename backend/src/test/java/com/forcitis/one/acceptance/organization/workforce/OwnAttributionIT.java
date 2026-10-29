package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P2 (ADR-0033 section 3 item 3 「本人」判定清单: 法人归属自改禁令; ADR-0027 section 2 item 2): nobody changes the legal entity of their
 * own employee entry, their own department or a department above it. The caller is an account; it is set against the subject as the
 * employee behind it and that employee's department chain. With the account id a UUID and the employee id a number, a comparison on the
 * account's side would never match and would let everyone change their own attribution.
 */
class OwnAttributionIT extends AccountsAcceptanceSupport {

    static final String EMPLOYEES = "/api/v1/organization/employee-legal-entities/";
    static final String ORG_UNITS = "/api/v1/organization/org-unit-legal-entities/";

    static final TestDatabase postgres = TestPostgres.database(OwnAttributionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private MockHttpServletResponse attribute(String path, SignedIn caller, UUID legalEntity) throws Exception {
        return mvc.perform(put(path).header("If-Match", "\"0\"").with(csrf()).with(session(caller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalEntityId\":\"" + legalEntity + "\",\"effectiveFrom\":\"" + TODAY + "\"}"))
                .andReturn().getResponse();
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 3 条 / ADR-0027 第 2 项第 2 条：不能改自己的法人归属（操作人即本人，按员工 ID 与其部门链比较）：本人条目、本部门、上级部门都 403；他人条目照常可改")
    void nobodyChangesTheirOwnAttribution() throws Exception {
        UUID top = newOrgUnitId();
        UUID own = newOrgUnitId();
        orgUnit(top, null, null);
        orgUnit(own, top, null);
        UUID maintainer = newEmployeeId();
        UUID colleague = newEmployeeId();
        employee(maintainer, own, null);
        employee(colleague, top, null);
        grantByOperations(maintainer, "org_data_admin");
        grantByOperations(colleague, "org_data_admin");
        SignedIn maintainerSession = signIn(maintainer);
        SignedIn colleagueSession = signIn(colleague);
        UUID maintainerAccount = maintainerSession.userId();
        assertThat(employeeOfAccount(maintainerAccount)).isEqualTo(maintainer);
        UUID legalEntity = createLegalEntity(colleagueSession, "OWA", "Synthetic own attribution");

        MockHttpServletResponse ownEntry = attribute(EMPLOYEES + text(maintainer), maintainerSession, legalEntity);
        MockHttpServletResponse ownUnit = attribute(ORG_UNITS + text(own), maintainerSession, legalEntity);
        MockHttpServletResponse unitAbove = attribute(ORG_UNITS + text(top), maintainerSession, legalEntity);

        assertThat(ownEntry.getStatus()).as(ownEntry.getContentAsString()).isEqualTo(403);
        assertThat((String) JsonPath.read(ownEntry.getContentAsString(), "$.errorCode")).isEqualTo("ORGANIZATION_WORKFORCE_OWN_ATTRIBUTION_FORBIDDEN");
        assertThat(ownUnit.getStatus()).as("own department: " + ownUnit.getContentAsString()).isEqualTo(403);
        assertThat(unitAbove.getStatus()).as("department above: " + unitAbove.getContentAsString()).isEqualTo(403);
        assertThat(auditEntriesBy(maintainerAccount, "organization.workforce.legal-entity-attribution.refused")).hasSizeGreaterThanOrEqualTo(3);

        MockHttpServletResponse someoneElse = attribute(EMPLOYEES + text(maintainer), colleagueSession, legalEntity);

        assertThat(someoneElse.getStatus()).as(someoneElse.getContentAsString()).isEqualTo(200);
    }
}
