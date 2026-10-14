package com.forcitis.one.organization.workforce.legal;

import static com.forcitis.one.organization.workforce.domain.Fixtures.member;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.LegalEntityDirectory;
import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.application.WorkforceIntegrationSupport;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.Permission;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class LegalEntityIntegrationTest extends WorkforceIntegrationSupport {
    static final String LEGAL_ENTITIES = "/api/v1/organization/legal-entities";
    static final RequestPostProcessor MAINTAINER = user("9001").authorities(() -> Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE);
    static final RequestPostProcessor READER = user("9002").authorities(() -> Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ);

    @Autowired
    MockMvc mvc;

    @Autowired
    LegalEntityDirectory directory;

    @Autowired
    SyncOrgSnapshot sync;

    @BeforeEach
    void noLegalEntities() {
        jdbc.sql("truncate table organization_workforce.legal_entities restart identity").update();
    }

    private void create(String code, String name) throws Exception {
        mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("an administrator creates a legal entity: id from the database, code and name as given, active, audited with the actor")
    void createLegalEntity() throws Exception {
        mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FST\",\"name\":\"Synthetic Legal Entity\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.legalEntityId").value("1"))
                .andExpect(jsonPath("$.code").value("FST"))
                .andExpect(jsonPath("$.name").value("Synthetic Legal Entity"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(audit).record(argThat((AuditEntry e) -> e.action().equals("organization.workforce.legal-entity.created")
                && e.actorId().value() == 9001 && e.subjectKey().equals("legal-entity:1")));
    }

    @Test
    @DisplayName("the legal entity code is unique (409) and must be 2 to 10 upper-case letters (422)")
    void codeRules() throws Exception {
        create("FST", "One");

        mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"FST\",\"name\":\"Another\"}")).andExpect(status().isConflict());
        for (String bad : List.of("fst", "F", "FSTFSTFSTFS", "FS1", "F-T", "")) {
            mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"" + bad + "\",\"name\":\"Bad\"}")).andExpect(status().isUnprocessableContent());
        }
        mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No code\"}")).andExpect(status().isUnprocessableContent());
        mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"OKAY\",\"name\":\" \"}")).andExpect(status().isUnprocessableContent());
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("the database itself refuses a code that is not 2 to 10 upper-case letters")
    void databaseChecksTheCodeFormat() {
        assertThatThrownBy(() -> jdbc.sql("insert into organization_workforce.legal_entities (code, name, status) values ('fst', 'x', 'ACTIVE')").update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("name and status can be changed; the code never; an unknown id is 404; a bad status is 422; the change is audited")
    void updateLegalEntity() throws Exception {
        create("FST", "One");

        mvc.perform(put(LEGAL_ENTITIES + "/1").with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"One renamed\",\"status\":\"INACTIVE\",\"code\":\"XXX\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FST"))
                .andExpect(jsonPath("$.name").value("One renamed"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(put(LEGAL_ENTITIES + "/404").with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"status\":\"ACTIVE\"}")).andExpect(status().isNotFound());
        mvc.perform(put(LEGAL_ENTITIES + "/1").with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"status\":\"GONE\"}")).andExpect(status().isUnprocessableContent());
        mvc.perform(put(LEGAL_ENTITIES + "/1").with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"status\":\"ACTIVE\"}")).andExpect(status().isUnprocessableContent());

        verify(audit).record(argThat((AuditEntry e) -> e.action().equals("organization.workforce.legal-entity.updated")
                && e.subjectKey().equals("legal-entity:1")));
    }

    @Test
    @DisplayName("the list shows every legal entity by code; reading needs org-unit:read, changing needs legal-entity:update")
    void listAndPermissions() throws Exception {
        create("QQQ", "Second");
        create("AAA", "First");

        mvc.perform(get(LEGAL_ENTITIES).with(READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("AAA"))
                .andExpect(jsonPath("$[1].code").value("QQQ"));

        mvc.perform(get(LEGAL_ENTITIES)).andExpect(status().isUnauthorized());
        mvc.perform(get(LEGAL_ENTITIES).with(MAINTAINER)).andExpect(status().isForbidden());
        mvc.perform(post(LEGAL_ENTITIES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"NEW\",\"name\":\"n\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(READER).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"NEW\",\"name\":\"n\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(LEGAL_ENTITIES + "/1").with(csrf()).with(READER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"n\",\"status\":\"ACTIVE\"}")).andExpect(status().isForbidden());
        assertThat(count()).isEqualTo(2);
    }

    @Test
    @DisplayName("the directory gives numbering the code of a legal entity, also a deactivated one, and nothing for an unknown id")
    void directoryFindsTheCode() throws Exception {
        create("FST", "One");
        mvc.perform(put(LEGAL_ENTITIES + "/1").with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"One\",\"status\":\"INACTIVE\"}")).andExpect(status().isOk());

        assertThat(directory.findCode(new LegalEntityId(1))).contains(new LegalEntityCode("FST"));
        assertThat(directory.findCode(new LegalEntityId(2))).isEmpty();
        assertThat(directory.findActiveCode(new LegalEntityId(1))).isEmpty();
        assertThat(directory.findActiveCode(new LegalEntityId(2))).isEmpty();
    }

    @Test
    @DisplayName("the organisation sync never creates, changes or removes legal entities")
    void syncLeavesLegalEntitiesAlone() throws Exception {
        create("FST", "One");
        orgSource.snapshot = tree(member(1, 2, 100L, null));

        sync.execute(SyncTrigger.SCHEDULED, false);
        sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(count()).isEqualTo(1);
        assertThat(directory.findCode(new LegalEntityId(1))).contains(new LegalEntityCode("FST"));
    }

    @Test
    @DisplayName("the code value object accepts 2 to 10 upper-case letters only")
    void codeValueObject() {
        assertThat(new LegalEntityCode("FS").value()).isEqualTo("FS");
        assertThat(new LegalEntityCode("ABCDEFGHIJ").value()).isEqualTo("ABCDEFGHIJ");
        for (String bad : List.of("F", "ABCDEFGHIJK", "fst", "F1T", "F T", "")) {
            assertThatThrownBy(() -> new LegalEntityCode(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private long count() {
        return jdbc.sql("select count(*) from organization_workforce.legal_entities").query(Long.class).single();
    }
}
