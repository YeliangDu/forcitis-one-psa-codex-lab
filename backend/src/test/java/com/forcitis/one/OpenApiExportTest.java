package com.forcitis.one;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.nio.file.Files;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;

/** Exports the build input, with stable key order; contract assertions below still run before consumers start. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
class OpenApiExportTest {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(OpenApiExportTest.class, TestPostgres.MIGRATED_TESTDATA).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlers;

    @Test
    void exportsTheOpenApiDocumentForTheFrontendClient() throws Exception {
        String body = mvc.perform(get("/v3/api-docs").with(user(TestAccounts.account(1).value().toString()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode document = json.readTree(body);
        assertThat(document.path("paths").size()).as("springdoc found no paths").isGreaterThan(0);
        Path target = Path.of("..", "frontend", "packages", "api", "openapi.json");
        Files.createDirectories(target.getParent());
        JsonMapper sorted = json.rebuild().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT).build();
        Files.writeString(target, sorted.writeValueAsString(sorted.treeToValue(document, Object.class)) + "\n");
    }

    @Test
    void pathsUseTheVersionSegmentAndResponseComponentsAreRequiredUnlessNullable() throws Exception {
        JsonNode document = json.readTree(mvc.perform(get("/v3/api-docs").with(user(TestAccounts.account(1).value().toString()))).andReturn().getResponse().getContentAsString());
        document.path("paths").propertyNames().forEach(path -> assertThat(path).startsWith("/api/v1/"));
        JsonNode orgUnit = document.path("components").path("schemas").path("OrgUnitResponse").path("required");
        assertThat(json.convertValue(orgUnit, String[].class)).contains("orgUnitId", "name", "status")
                .doesNotContain("parentOrgUnitId", "headEmployeeId", "shortName");
        assertThat(document.path("components").path("schemas").path("DecideTaskRequest").path("required").toString()).doesNotContain("reason");
    }

    /** Ids of this system (R-1a) are string / uuid on the wire; the rest are named here until R-1b gives them their own ids. */
    static final Set<String> OWN_ID_FIELDS = Set.of("AttributeLegalEntityRequest.legalEntityId", "AuditEntryResponse.entryId",
            "AuthorizationRequestResponse.requestId", "AuthorizationRequestResponse.roleId", "AwaitingConfirmationResponse.requestId", "HandledTaskResponse.recordId", "HistoryEntry.recordId",
            "LegacyNumberResponse.documentId", "LegacyNumberResponse.legalEntityId", "LegacyNumberResponse.migrationBatchId", "LegalEntityResponse.legalEntityId", "Period.legalEntityId",
            "EmployeeRosterResponse.employeeId", "EmployeeDetailResponse.employeeId", "RosterAssignmentResponse.orgUnitId", "RosterAssignmentResponse.positionId",
            "DetailAssignmentResponse.orgUnitId", "PrimaryAssignmentResponse.orgUnitId", "PrimaryAssignmentResponse.positionId", "DetailAssignmentResponse.positionId", "DetailAssignmentResponse.reportsToEmployeeId", "PositionResponse.positionId",
            "RoleResponse.roleId", "ProblemDetail.correlationId", "ChangeFinanceHeadRequest.financeHeadEmployeeId", "LegalEntityResponse.financeHeadEmployeeId",
            "PermissionAdjustmentResponse.permissionAdjustmentId", "PermissionAdjustmentResponse.userId", "PermissionAdjustmentResponse.roleId",
            "PermissionAdjustmentResponse.orgUnitId", "PermissionAdjustmentResponse.businessOwnerEmployeeId", "UpholdPermissionAdjustmentRequest.businessOwnerEmployeeId",
            "MarkPermissionAdjustmentAdjustedRequest.authorizationRequestId", "MarkPermissionAdjustmentAdjustedRequest.auditEntryId",
            "RegisterSourceReferenceRequest.ownId", "SourceReferenceResponse.ownId", "SourceReferenceResponse.sourceReferenceId",
            "AuditEntryResponse.actorId", "AuditEntryResponse.onBehalfOfId", "AuditEntryResponse.subjectId", "AuthorizationRequestResponse.subjectUserId", "CurrentUserResponse.userId",
            "LoginResponse.userId", "UserAccountResponse.userId", "AccountListItemResponse.userId", "AccountGrantResponse.grantedBy", "AccountGrantResponse.revokedBy", "HandledTaskResponse.actorId", "HistoryEntry.actorId", "PendingTemplate.authorId",
            "parameter userId", "parameter actorId", "parameter onBehalfOfId", "parameter subjectId",
            "parameter legalEntityId", "parameter ownId", "parameter permissionAdjustmentId", "parameter requestId", "parameter roleId", "parameter sourceReferenceId", "parameter documentId /api/v1/platform/legacy-numbers",
            "OrgUnitResponse.orgUnitId", "OrgUnitResponse.headEmployeeId", "OrgUnitResponse.parentOrgUnitId", "OrgUnitResponse.regionalReceivablesManagerId",
            "OrgUnitDetailResponse.ancestorOrgUnitIds", "OrgUnitDetailResponse.orgUnitAndBelowIds", "CurrentUserResponse.orgUnitId",
            "CustomOrgUnitRequest.orgUnitId", "CustomOrgUnitResponse.orgUnitId",
            "LegalEntityAttributionResponse.subjectId", "ReassignTaskRequest.assigneeId", "TransferTaskRequest.assigneeId",
            "DecisionResponse.instanceId", "HandledTaskResponse.taskId", "HandledTaskResponse.instanceId", "HandledTaskResponse.documentId", "HistoryEntry.taskId",
            "TaskDetailResponse.taskId", "TaskDetailResponse.instanceId", "TaskDetailResponse.documentId", "TaskOverviewResponse.taskId", "TaskOverviewResponse.documentId",
            "InboxItemResponse.documentId", "parameter taskId", "StoredFileResponse.fileId", "FileStatusResponse.fileId", "parameter fileId",
            "parameter orgUnitId", "parameter employeeId", "parameter positionId",
            "PublishStandardHoursRequest.legalEntityId", "PublishedStandardHoursResponse.standardHoursVersionId", "VoidedStandardHoursResponse.standardHoursVersionId",
            "StandardHoursVersionResponse.standardHoursVersionId", "StandardHoursVersionResponse.legalEntityId", "StandardHoursVersionResponse.confirmedBy", "parameter standardHoursVersionId",
            "EnterPersonnelCommandRequest.employeeId", "EnterPersonnelCommandRequest.targetOrgUnitId", "EnterPersonnelCommandRequest.targetPositionId", "EnterPersonnelCommandRequest.targetLegalEntityId", "EnterPersonnelCommandRequest.targetAssignmentId", "EnterPersonnelCommandRequest.targetEmployerLegalEntityId", "EnterPersonnelCommandRequest.predecessorRelationshipId", "EnterPersonnelCommandRequest.employmentRelationshipId",
            "EnterPersonnelCommandRequest.targetReportsToEmployeeId", "EnterOrgStructureCommandRequest.targetOrgUnitId", "EnterOrgStructureCommandRequest.targetPositionId", "EnterOrgStructureCommandRequest.targetParentOrgUnitId",
            "EnterOrgStructureCommandRequest.targetHeadEmployeeId", "EnterOrgStructureCommandRequest.targetReceivablesManagerEmployeeId", "EnterOrgStructureCommandRequest.mergeIntoOrgUnitId",
            "EnterOrgStructureCommandRequest.targetLegalEntityId", "OrgStructureCommandResponse.orgStructureCommandId",
            "PersonnelCommandResponse.personnelCommandId", "parameter personnelCommandId",
            "parameter separationHoldId",
            "PersonnelReadResponse.personnelCommandId", "PersonnelReadResponse.employeeId", "PersonnelReadResponse.createdBy", "PersonnelReadResponse.withdrawnBy", "PersonnelReadResponse.separationHoldId", "CountersignReadResponse.attributionCountersignId", "CountersignReadResponse.legalEntityId", "StructureReadResponse.orgStructureCommandId", "StructureReadResponse.targetOrgUnitId", "StructureReadResponse.targetPositionId", "HoldReadResponse.separationHoldId", "HoldReadResponse.personnelCommandId", "HoldReadResponse.employeeId", "HoldReadResponse.orgUnitId",
            "BasisLineResponse.employeeId", "BasisLineResponse.newLegalEntityId", "BasisLineResponse.previousLegalEntityId", "CommandResponse.newLegalEntityId", "CommandResponse.orgStructureCommandId", "CommandResponse.orgUnitId", "CommandResponse.personnelCommandId", "CommandResponse.previousLegalEntityId", "MergeLineResponse.employeeId", "MergeLineResponse.newLegalEntityId", "MergeLineResponse.orgUnitId", "MergeLineResponse.previousLegalEntityId", "PersonResponse.employeeId", "RequestResponse.attributionCountersignId", "RequestResponse.legalEntityId", "SummaryResponse.attributionCountersignId", "SummaryResponse.employeeId", "SummaryResponse.legalEntityId", "SummaryResponse.newLegalEntityId", "SummaryResponse.personnelCommandId", "SummaryResponse.previousLegalEntityId", "parameter attributionCountersignId", "parameter mergeIntoOrgUnitId", "parameter orgStructureCommandId",
            "AuthorizationRequestResponse.requestedBy", "ProfileChangeRequestResponse.requestId", "ProfileChangeRequestResponse.employeeId", "ProfileChangeRequestResponse.requestedBy",
            "ProfileChangeRequestResponse.decidedBy", "AwaitingSecondPersonResponse.requestId", "PendingTemplate.confirmedBy", "PendingTemplate.financeConfirmedBy",
            "TaskDetailResponse.initiator", "TaskDetailResponse.assignee", "TaskOverviewResponse.assignee");

    /** Employee ids whose names do not end in Id or By (D-10): named in full so the guard looks at them. */
    static final Set<String> NAMED_OWN_ID_FIELDS = Set.of("TaskDetailResponse.initiator", "TaskDetailResponse.assignee",
            "TaskOverviewResponse.assignee");

    /**
     * Ids that are not ours, by full name. InboxItemResponse.taskId carries the old system's task key for the old system's items; it stays a plain
     * string until the inbox contract is neutralised (ADR-0039 item 6, RULING-P4-D6) and is deleted here then.
     */
    static final Set<String> OTHER_ID_NAMES = Set.of("InboxItemResponse.taskId");

    /** Identifiers that belong to an external system and stay what that system gives: a plain string on the wire. */
    static final Set<String> EXTERNAL_ID_NAMES = Set.of("sourceId", "wecomUserId");

    @Test
    void idFieldsOfThisSystemAreStringUuidAndEveryOtherIdFieldIsClassified() throws Exception {
        JsonNode document = json.readTree(mvc.perform(get("/v3/api-docs").with(user(TestAccounts.account(1).value().toString()))).andReturn().getResponse().getContentAsString());
        java.util.Map<String, JsonNode> idFields = new java.util.TreeMap<>();
        document.path("components").path("schemas").properties().forEach(schema -> schema.getValue().path("properties").properties().forEach(property -> {
            if (isIdName(property.getKey())) {
                idFields.put(schema.getKey() + "." + property.getKey(), property.getValue().has("items") ? property.getValue().path("items") : property.getValue());
            }
        }));
        NAMED_OWN_ID_FIELDS.forEach(full -> {
            JsonNode property = document.path("components").path("schemas").path(full.split("\\.")[0]).path("properties").path(full.split("\\.")[1]);
            assertThat(property.isMissingNode()).as(full + " exists in the document").isFalse();
            idFields.put(full, property);
        });
        document.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation -> operation.getValue().path("parameters").forEach(parameter -> {
            String name = parameter.path("name").asString();
            if (isIdName(name)) {
                idFields.put("parameter " + name + (name.equals("documentId") ? " " + path.getKey() : ""), parameter.path("schema"));
            }
        })));
        assertThat(idFields).as("the document has id fields").isNotEmpty();

        java.util.List<String> problems = new java.util.ArrayList<>();
        idFields.forEach((key, schema) -> {
            if (OWN_ID_FIELDS.contains(key)) {
                boolean stringType = schema.path("type").isArray() ? schema.path("type").toString().contains("\"string\"") : schema.path("type").asString().equals("string");
                if (!stringType || !schema.path("format").asString().equals("uuid")) {
                    problems.add(key + " is an id of this system and must be string / uuid, found " + schema.path("type") + " / " + schema.path("format"));
                }
                if (schema.has("example")) {
                    try {
                        com.forcitis.one.kernel.Uuids.parseCanonical(schema.path("example").asString());
                    } catch (IllegalArgumentException notCanonical) {
                        problems.add(key + " has an example that is not a lower-case canonical UUID");
                    }
                }
            } else if (EXTERNAL_ID_NAMES.contains(fieldName(key))) {
                JsonNode type = schema.path("type");
                boolean plainString = type.isArray() ? type.toString().equals("[\"string\",\"null\"]") : type.asString().equals("string");
                if (!plainString || schema.has("format")) {
                    problems.add(key + " is an id of an external system and is a plain string on the wire, found " + schema.path("type") + " / " + schema.path("format"));
                }
            } else if (!OTHER_ID_NAMES.contains(key)) {
                problems.add(key + " looks like an id: list it in OWN_ID_FIELDS (string / uuid), in EXTERNAL_ID_NAMES (external system) or, with a reason and an end date, in OTHER_ID_NAMES");
            }
        });
        assertThat(problems).isEmpty();
    }

    private static String fieldName(String key) {
        return key.replaceFirst("^parameter ", "").replaceFirst("^.*\\.", "").replaceFirst(" /.*$", "");
    }

    private static boolean isIdName(String name) {
        return name.equals("id") || name.endsWith("Id") || name.endsWith("Ids") || name.endsWith("By") || name.equals("subjectKey");
    }

    /** springdoc names a component after the simple class name, so two classes of one name in different packages would silently share one component. */
    @Test
    void componentNamesAreUniquePerJavaType() {
        Map<String, Set<String>> typesByComponent = new TreeMap<>();
        ModelConverters converters = new ModelConverters(true);
        converters.addConverter(new ModelConverter() {
            @Override
            public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
                Schema<?> schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
                String component = schema == null ? null : schema.get$ref() != null ? schema.get$ref().replace(COMPONENT_PREFIX, "") : schema.getName();
                if (component != null) {
                    typesByComponent.computeIfAbsent(component, name -> new TreeSet<>()).add(Json.mapper().constructType(type.getType()).getRawClass().getName());
                }
                return schema;
            }
        });
        exposedTypes().forEach(type -> converters.readAll(new AnnotatedType(type)));

        assertThat(typesByComponent).as("the walk found components").containsKeys("SourceReferenceResponse", "NumberingRuleResponse", "SourceObjectKind");
        assertThat(typesByComponent.entrySet().stream().filter(entry -> entry.getValue().size() > 1).map(entry -> entry.getKey() + " names " + entry.getValue()).toList())
                .as("one component name stands for one Java type: give one of the classes @Schema(name = ...)").isEmpty();
        assertThat(typesByComponent.keySet()).as("OpenApiProblems writes these components itself; a Java type of the same name would be overwritten")
                .doesNotContainAnyElementsOf(OPENAPI_PROBLEMS_COMPONENTS);
    }

    private static final Set<String> OPENAPI_PROBLEMS_COMPONENTS = Set.of("ErrorCode", "ProblemDetail");

    private static final String COMPONENT_PREFIX = "#/components/schemas/";

    private Set<Type> exposedTypes() {
        Set<Type> types = new LinkedHashSet<>();
        handlers.getHandlerMethods().values().forEach(handler -> {
            Type returned = handler.getMethod().getGenericReturnType();
            types.add(returned instanceof ParameterizedType wrapper && wrapper.getRawType() == ResponseEntity.class ? wrapper.getActualTypeArguments()[0] : returned);
            for (MethodParameter parameter : handler.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(RequestBody.class) || parameter.hasParameterAnnotation(RequestParam.class)
                        || parameter.hasParameterAnnotation(PathVariable.class)) {
                    types.add(parameter.getGenericParameterType());
                }
            }
        });
        types.removeIf(type -> type == void.class || type == Void.class);
        return types;
    }
}
