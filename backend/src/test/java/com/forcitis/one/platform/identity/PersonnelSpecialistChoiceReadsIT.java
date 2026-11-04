package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.DataScopeSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1306 (user 2026-11-04 「给人事专员加读组织单元和法人」; ADR-0068 item 1.6): a signed-in personnel specialist can read the three pickers a personnel command
 * form needs (org units, positions, legal entities) and still cannot enter an org-structure command or update a legal entity. The role is granted by the
 * operations command and the account signs in, so the declaration-to-session binding is what is exercised.
 */
class PersonnelSpecialistChoiceReadsIT extends DataScopeSupport {

    static final TestDatabase postgres = TestPostgres.database(PersonnelSpecialistChoiceReadsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @BeforeAll
    void theWorld() throws Exception {
        unit("PS-X", null);
        position("PS-P");
        person("hr", "PS-X", "PS-P");
        person("nobody", "PS-X", "PS-P");
        seedOrganisation();
        grantByOperations("hr", "personnel_specialist");
    }

    private MockHttpServletResponse read(String key, String url) throws Exception {
        return mvc.perform(get(url).with(as(session(key)))).andReturn().getResponse();
    }

    @Test
    @DisplayName("#1306 完成标准 1：人事专员读组织单元、岗位选项、法人选项都是 200")
    void theSpecialistReadsTheThreePickers() throws Exception {
        assertThat(read("hr", "/api/v1/organization/org-units").getStatus()).as("org units").isEqualTo(200);
        assertThat(read("hr", "/api/v1/organization/positions").getStatus()).as("positions").isEqualTo(200);
        assertThat(read("hr", "/api/v1/organization/legal-entities").getStatus()).as("legal entities").isEqualTo(200);
    }

    @Test
    @DisplayName("#1306 完成标准 1：人事专员仍不能录入组织结构命令，也不能更新法人（都是 403）")
    void theSpecialistStillCannotWrite() throws Exception {
        MockHttpServletResponse structure = mvc.perform(post("/api/v1/organization/org-structure-commands").with(as(session("hr"))).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse();
        MockHttpServletResponse legalEntity = mvc.perform(post("/api/v1/organization/legal-entities").with(as(session("hr"))).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse();

        assertThat(structure.getStatus()).as("org-structure command: " + structure.getContentAsString()).isEqualTo(403);
        assertThat(legalEntity.getStatus()).as("legal entity update: " + legalEntity.getContentAsString()).isEqualTo(403);
    }

    @Test
    @DisplayName("#1306 不得做：选项读取不对所有登录用户开放，没有角色的账号读组织单元和法人是 403")
    void anAccountWithoutTheRoleIsRefused() throws Exception {
        assertThat(read("nobody", "/api/v1/organization/org-units").getStatus()).isEqualTo(403);
        assertThat(read("nobody", "/api/v1/organization/legal-entities").getStatus()).isEqualTo(403);
    }
}
