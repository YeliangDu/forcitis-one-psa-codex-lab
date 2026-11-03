package com.forcitis.one.organization.workforce.legal;

import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
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

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.organization.workforce.events.LegalEntityCodeCorrected;
import com.forcitis.one.organization.workforce.events.LegalEntityDeactivated;
import com.forcitis.one.organization.workforce.events.LegalEntityRegistered;
import com.forcitis.one.organization.workforce.events.LegalEntityRenamed;
import java.util.UUID;
import org.springframework.test.context.event.ApplicationEvents;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.LegalEntityDirectory;
import com.forcitis.one.organization.workforce.application.SnapshotSeeder;
import com.forcitis.one.organization.workforce.application.WorkforceIntegrationSupport;
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
    static final UUID UNKNOWN = TestIds.uuid(404);
    static final RequestPostProcessor MAINTAINER = user(TestAccounts.account(9001).value().toString()).authorities(() -> Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE);
    static final RequestPostProcessor READER = user(TestAccounts.account(9002).value().toString()).authorities(() -> Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ);

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.forcitis.one.organization.workforce.api.LegalEntityNumberUsage numberUsage;

    @Autowired
    MockMvc mvc;

    @Autowired
    LegalEntityDirectory directory;

    @Autowired
    ApplicationEvents events;

    @BeforeEach
    void noLegalEntities() {
        org.mockito.Mockito.when(numberUsage.hasIssuedNumbers(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        com.forcitis.one.organization.workforce.application.VersionTables.truncate(jdbc, "truncate table organization_workforce.legal_entities restart identity cascade");
    }

    private String version(UUID id) {
        return "\"" + jdbc.sql("select coalesce(max(version), 1) from organization_workforce.legal_entities where id = :id").param("id", id)
                .query(Long.class).optional().orElse(1L) + "\"";
    }

    private UUID create(String code, String name) throws Exception {
        String body = mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.legalEntityId"));
    }

    @Test
    @DisplayName("an administrator creates a legal entity: id from the database, code and name as given, active, audited with the actor")
    void createLegalEntity() throws Exception {
        mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FST\",\"name\":\"Synthetic Legal Entity\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.legalEntityId").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
                .andExpect(jsonPath("$.code").value("FST"))
                .andExpect(jsonPath("$.name").value("Synthetic Legal Entity"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(events.stream(LegalEntityRegistered.class)).singleElement().satisfies(e -> {
            assertThat(e.code()).isEqualTo(new LegalEntityCode("FST"));
            assertThat(e.envelope().actorType()).isEqualTo(ActorType.USER);
            assertThat(e.envelope().actorId()).isEqualTo(TestAccounts.account(9001).value());
            assertThat(e.envelope().aggregateId()).isEqualTo(e.legalEntityId().value());
            assertThat(e.envelope().legalEntityId()).isEqualTo(e.legalEntityId());
        });
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
    @DisplayName("L-1 an id is read in its one canonical form: upper case, short forms and the nil UUID are refused with 400, in the path and in the body")
    void idsAreReadCanonically() throws Exception {
        UUID one = create("FST", "One");

        mvc.perform(get(LEGAL_ENTITIES + "/" + one).with(READER)).andExpect(status().isOk());
        mvc.perform(get(LEGAL_ENTITIES + "/" + one.toString().toUpperCase()).with(READER)).andExpect(status().isBadRequest());
        mvc.perform(get(LEGAL_ENTITIES + "/1-1-1-1-1").with(READER)).andExpect(status().isBadRequest());
        mvc.perform(get(LEGAL_ENTITIES + "/00000000-0000-0000-0000-000000000000").with(READER)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/organization/org-unit-legal-entities/" + TestOrgIds.orgUnit(2).value()).header("If-Match", "\"0\"").with(csrf()).with(MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalEntityId\":\"" + one.toString().toUpperCase() + "\",\"effectiveFrom\":\"2026-10-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the database itself refuses a code that is not 2 to 10 upper-case letters")
    void databaseChecksTheCodeFormat() {
        assertThatThrownBy(() -> jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (uuidv7(), 'fst', 'x', 'ACTIVE')").update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("name and status can be changed, and the code corrected while no number was issued; unknown id 404, bad status 422; each change is announced")
    void updateLegalEntity() throws Exception {
        UUID one = create("FST", "One");

        mvc.perform(put(LEGAL_ENTITIES + "/" + one).header("If-Match", version(one)).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"One renamed\",\"status\":\"INACTIVE\",\"code\":\"XXX\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("XXX"))
                .andExpect(jsonPath("$.name").value("One renamed"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(put(LEGAL_ENTITIES + "/" + UNKNOWN).header("If-Match", version(UNKNOWN)).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"status\":\"ACTIVE\"}")).andExpect(status().isNotFound());
        mvc.perform(put(LEGAL_ENTITIES + "/" + one).header("If-Match", version(one)).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"status\":\"GONE\"}")).andExpect(status().isUnprocessableContent());
        mvc.perform(put(LEGAL_ENTITIES + "/" + one).header("If-Match", version(one)).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"status\":\"ACTIVE\"}")).andExpect(status().isUnprocessableContent());

        assertThat(events.stream(LegalEntityCodeCorrected.class)).singleElement()
                .satisfies(e -> assertThat(e.previousCode()).isEqualTo(new LegalEntityCode("FST")));
        assertThat(events.stream(LegalEntityRenamed.class)).hasSize(1);
        assertThat(events.stream(LegalEntityDeactivated.class)).hasSize(1);
        assertThat(jdbc.sql("select event_count from organization_workforce.legal_entities where id = :id").param("id", one).query(Long.class).single())
                .as("registered 1, then code corrected + renamed + deactivated").isEqualTo(4L);
        assertThat(jdbc.sql("select version from organization_workforce.legal_entities where id = :id").param("id", one).query(Long.class).single())
                .as("one save: the lock version moves by one however many events it carried").isEqualTo(2L);
    }

    @Test
    @DisplayName("M5 the code is fixed once a number carrying it was issued: 409, nothing changes; a code nobody numbered yet stays correctable")
    void codeIsLockedByIssuedNumbers() throws Exception {
        UUID one = create("FST", "One");
        UUID two = create("QQQ", "Two");
        org.mockito.Mockito.when(numberUsage.hasIssuedNumbers(new LegalEntityId(one))).thenReturn(true);

        mvc.perform(put(LEGAL_ENTITIES + "/" + one).header("If-Match", version(one)).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FSTX\",\"name\":\"One\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_LOCKED"));
        assertThat(directory.findCode(new LegalEntityId(one))).contains(new LegalEntityCode("FST"));
        assertThat(events.stream(LegalEntityCodeCorrected.class)).isEmpty();

        mvc.perform(put(LEGAL_ENTITIES + "/" + one).header("If-Match", version(one)).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FST\",\"name\":\"One renamed\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("FST"));
        mvc.perform(put(LEGAL_ENTITIES + "/" + two).header("If-Match", version(two)).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"QQQX\",\"name\":\"Two\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("QQQX"));
        mvc.perform(put(LEGAL_ENTITIES + "/" + two).header("If-Match", version(two)).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FST\",\"name\":\"Two\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_TAKEN"));
        mvc.perform(put(LEGAL_ENTITIES + "/" + two).header("If-Match", version(two)).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"bad\",\"name\":\"Two\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("the list shows every legal entity by code; reading needs org-unit:read, changing needs legal-entity:update")
    void listAndPermissions() throws Exception {
        create("QQQ", "Second");
        create("AAA", "First");

        mvc.perform(get(LEGAL_ENTITIES).with(READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].code").value("AAA"))
                .andExpect(jsonPath("$.items[1].code").value("QQQ"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        mvc.perform(get(LEGAL_ENTITIES)).andExpect(status().isUnauthorized());
        mvc.perform(get(LEGAL_ENTITIES).with(MAINTAINER)).andExpect(status().isForbidden());
        mvc.perform(post(LEGAL_ENTITIES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"NEW\",\"name\":\"n\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(READER).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"NEW\",\"name\":\"n\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(LEGAL_ENTITIES + "/" + UNKNOWN).with(csrf()).with(READER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"n\",\"status\":\"ACTIVE\"}")).andExpect(status().isForbidden());
        assertThat(count()).isEqualTo(2);
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    @DisplayName("the directory gives numbering the code of a legal entity, also a deactivated one, and nothing for an unknown id")
    void directoryFindsTheCode() throws Exception {
        UUID one = create("FST", "One");
        mvc.perform(put(LEGAL_ENTITIES + "/" + one).header("If-Match", version(one)).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"One\",\"status\":\"INACTIVE\"}")).andExpect(status().isOk());

        assertThat(directory.findCode(new LegalEntityId(one))).contains(new LegalEntityCode("FST"));
        assertThat(directory.findCode(new LegalEntityId(UNKNOWN))).isEmpty();
        var inTransaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        java.util.Optional<LegalEntityCode> deactivated = inTransaction.execute(status -> directory.lockActiveCode(new LegalEntityId(one)));
        java.util.Optional<LegalEntityCode> unknown = inTransaction.execute(status -> directory.lockActiveCode(new LegalEntityId(UNKNOWN)));
        assertThat(deactivated).isEmpty();
        assertThat(unknown).isEmpty();
        assertThatThrownBy(() -> directory.lockActiveCode(new LegalEntityId(one))).as("the lock needs the caller's transaction")
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("the organisation sync never creates, changes or removes legal entities")
    void syncLeavesLegalEntitiesAlone() throws Exception {
        UUID one = create("FST", "One");
        orgSource.snapshot = tree(person(1, 2, 100L, null));

        SnapshotSeeder.seed(jdbc, orgSource.snapshot);
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);

        assertThat(count()).isEqualTo(1);
        assertThat(directory.findCode(new LegalEntityId(one))).contains(new LegalEntityCode("FST"));
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
