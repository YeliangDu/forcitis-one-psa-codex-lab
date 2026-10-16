package com.forcitis.one.platform.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdRecords;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class ScalarIdModuleTest {
    static final JsonMapper JSON = JsonMapper.builder().addModule(new ScalarIdModule()).build();

    record Payload(UserId actor, EmployeeId employee, List<OrgUnitId> units, LegalEntityId legalEntityId, ApprovalTaskId taskId, Reason reason) {
    }

    private static Object sample(Class<?> valueType) {
        if (valueType == UUID.class) {
            return TestIds.uuid(7);
        }
        if (valueType == long.class) {
            return Long.MIN_VALUE + 42;
        }
        if (valueType == String.class) {
            return "sample-7";
        }
        throw new IllegalArgumentException("add a sample value for " + valueType);
    }

    @Test
    @DisplayName("G-C5 a typed id is written as the text of its value, in a payload, in a list and as a nested field")
    void idsAreWrittenAsText() throws Exception {
        Payload payload = new Payload(TestAccounts.account(7), TestAccounts.employee(8), List.of(TestOrgIds.orgUnit(1), TestOrgIds.orgUnit(2)),
                new LegalEntityId(TestIds.uuid(5)), com.forcitis.one.TestApprovalIds.task("task-9"), new Reason("because"));

        assertThat(JSON.readTree(JSON.writeValueAsString(payload))).isEqualTo(JSON.readTree("""
                {"actor":"%s","employee":"%s","units":["%s","%s"],"legalEntityId":"%s","taskId":"%s","reason":{"value":"because"}}
                """.formatted(TestAccounts.account(7).value(), TestAccounts.employee(8).value(), TestOrgIds.orgUnit(1).value(),
                TestOrgIds.orgUnit(2).value(), TestIds.uuid(5), com.forcitis.one.TestApprovalIds.uuid("task-9"))));
    }

    @Test
    @DisplayName("G-C5 what was written reads back to equal ids, and a long id is also read from a bare number")
    void idsReadBack() throws Exception {
        Payload payload = new Payload(TestAccounts.account(7), TestAccounts.employee(8), List.of(TestOrgIds.orgUnit(1)), new LegalEntityId(TestIds.uuid(5)),
                com.forcitis.one.TestApprovalIds.task("task-9"), new Reason("because"));

        assertThat(JSON.readValue(JSON.writeValueAsString(payload), Payload.class)).isEqualTo(payload);
        assertThat(JSON.readValue("\"" + TestAccounts.employee(7).value() + "\"", EmployeeId.class)).isEqualTo(TestAccounts.employee(7));
        assertThat(JSON.readValue("7", SourceObjectId.class)).isEqualTo(new SourceObjectId(7));
        assertThat(JSON.readValue("\"7\"", SourceObjectId.class)).isEqualTo(new SourceObjectId(7));
    }

    @Test
    @DisplayName("G-C5 every typed id of the production code takes the scalar form and reads back to the same id")
    void everyTypedIdRoundTrips() throws Exception {
        List<Class<?>> idTypes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.forcitis.one").stream().map(JavaClass::reflect).filter(IdRecords::isIdRecord).toList();
        assertThat(idTypes).as("typed ids found").hasSizeGreaterThan(25);

        for (Class<?> type : idTypes) {
            Class<?> valueType = type.getRecordComponents()[0].getType();
            Object id = type.getDeclaredConstructor(valueType).newInstance(sample(valueType));

            String written = JSON.writeValueAsString(id);

            assertThat(written).as(type.getSimpleName()).isEqualTo("\"" + id + "\"");
            assertThat(JSON.readValue(written, type)).as(type.getSimpleName()).isEqualTo(id);
        }
    }

    @Test
    @DisplayName("G-C5 the old object form, a malformed id and an id that the type refuses are not read")
    void badIdsAreRefused() {
        assertThatThrownBy(() -> JSON.readValue("{\"value\":7}", UserId.class)).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> JSON.readValue("\"seven\"", UserId.class)).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> JSON.readValue("\"0\"", UserId.class)).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> JSON.readValue("\"7\"", EmployeeId.class)).as("an employee id is no longer a number").isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> JSON.readValue("\"not-a-uuid\"", LegalEntityId.class)).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> JSON.readValue("\"" + UUID.nameUUIDFromBytes(new byte[] {1}).toString().toUpperCase() + "\"", LegalEntityId.class))
                .as("only the canonical lower-case spelling").isInstanceOf(JacksonException.class);
    }
}
