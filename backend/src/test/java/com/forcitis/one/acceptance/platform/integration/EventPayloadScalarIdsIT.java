package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.SessionRoles;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * G-C5 (consistency review of #129, assigned to R-1b): the serialized form of a domain event, as the event publication registry keeps it
 * for redelivery, carries every id as a scalar. Today an id is a record and is written as {"value": ...}. The events are caused over HTTP
 * only; the registry is read in its own schema (platform_integration), unqualified, through a connection whose search path is that schema.
 * The ids of a person (user, employee) are checked in the same way by AccountDisabled and AccountRolesReplaced, which carry a user id
 * whatever the type behind it is called. From R-1b P2 on the user id is a UUID of its own and the account row names its employee
 * (RULING-P2-1: the account case rewritten with UUIDs).
 */
@Testcontainers
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
class EventPayloadScalarIdsIT {

    static final String LEGAL_ENTITIES = "/api/v1/organization/legal-entities";
    static final String ROLES = "/api/v1/platform/roles";
    static final String ACCOUNTS = "/api/v1/platform/user-accounts/";
    /** Field names that hold an id, a reference to a person, or a list of them. */
    static final Pattern ID_FIELD = Pattern.compile("(?i)(.*Ids?|userId|employee|initiator|projectManager|claimedBy|releasedBy|assignee|candidates|holders)");
    static final JsonMapper JSON = JsonMapper.builder().build();

    static final RequestPostProcessor LEGAL_ENTITY_MAINTAINER = person(1, Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE,
            Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ);
    static final RequestPostProcessor ROLE_ADMINISTRATOR = person(3, Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE,
            Permission.Codes.PLATFORM_IDENTITY_ROLE_READ, Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql")
            .withUrlParam("currentSchema", "platform_approval");

    @MockitoBean
    WecomGateway wecomGateway;

    @Autowired
    MockMvc mvc;

    @Autowired
    DataSource dataSource;

    // The session principal of a test caller in the form the build's account id has: a number before R-1b P2, a UUID from P2 on.
    static RequestPostProcessor person(int number, String... permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + SessionRoles.ORDINARY));
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        Class<?> idType = UserId.class.isRecord() ? UserId.class.getRecordComponents()[0].getType() : long.class;
        String principal = idType == UUID.class ? String.format("01900000-0000-7000-8000-%012d", number) : String.valueOf(70000 + number);
        return SecurityMockMvcRequestPostProcessors.user(principal).authorities(authorities);
    }

    private Connection schemaConnection(String schema) throws SQLException {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        Connection connection = DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        }
        return connection;
    }

    /**
     * The role administrator is the recorded_by of the MANUAL role version (ck_role_versions_manual, fk_role_versions_recorded_by to
     * user_accounts), so its account must exist before a role is created (card ADR-0034-IMPL sections 3.3 and 7.2).
     */
    @BeforeEach
    void theRoleAdministratorHasAnAccount() throws Exception {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement account = identity.prepareStatement(
                "insert into user_accounts (user_id, employee_id) values (?, ?) on conflict do nothing")) {
            account.setObject(1, UUID.fromString(String.format("01900000-0000-7000-8000-%012d", 3)));
            account.setObject(2, UUID.randomUUID());
            account.executeUpdate();
        }
    }

    /** Every registry row (open or already archived) of the event type whose envelope names the aggregate, parsed. */
    private List<JsonNode> registered(String eventType, String aggregateId) throws Exception {
        List<JsonNode> found = new ArrayList<>();
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                try (PreparedStatement rows = registry.prepareStatement("select serialized_event from " + table + " where event_type like ?")) {
                    rows.setString(1, "%." + eventType);
                    try (ResultSet result = rows.executeQuery()) {
                        while (result.next()) {
                            JsonNode event = JSON.readTree(result.getString(1));
                            if (aggregateId.equals(event.path("envelope").path("aggregateId").asString(null))) {
                                found.add(event);
                            }
                        }
                    }
                }
            }
        }
        assertThat(found).as("registry rows of %s for aggregate %s", eventType, aggregateId).isNotEmpty();
        return found;
    }

    /** Collects the path of every id field that holds an object or an array containing one, and counts the id fields inspected. */
    static void idFieldsMustBeScalar(String path, JsonNode node, List<String> wrapped, List<String> inspected) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String child = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
                JsonNode value = field.getValue();
                if (ID_FIELD.matcher(field.getKey()).matches() && !value.isNull()) {
                    inspected.add(child);
                    boolean scalar = value.isString() || value.isNumber();
                    boolean scalars = value.isArray() && value.valueStream().allMatch(item -> item.isString() || item.isNumber());
                    if (!scalar && !scalars) {
                        wrapped.add(child + " = " + value);
                    }
                }
                idFieldsMustBeScalar(child, value, wrapped, inspected);
            }
        } else if (node.isArray()) {
            node.forEach(item -> idFieldsMustBeScalar(path, item, wrapped, inspected));
        }
    }

    private void assertIdsAreScalar(List<JsonNode> events, Predicate<String> mustBeInspected) {
        for (JsonNode event : events) {
            List<String> wrapped = new ArrayList<>();
            List<String> inspected = new ArrayList<>();
            idFieldsMustBeScalar("", event, wrapped, inspected);
            assertThat(inspected).as("id fields found in %s", event).anyMatch(mustBeInspected);
            assertThat(wrapped).as("id fields that are not a string or a number in %s", event).isEmpty();
        }
    }

    @Test
    @DisplayName("R-1b P1 / G-C5 领域事件负载中的 ID 为标量：LegalEntityRegistered 的 legalEntityId 与信封中的法人 ID 是字符串，不是 {\"value\": …}")
    void legalEntityRegisteredCarriesScalarIds() throws Exception {
        MockHttpServletResponse created = mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(LEGAL_ENTITY_MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"SCA\",\"name\":\"Synthetic scalar ids\"}")).andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        String legalEntityId = JsonPath.read(created.getContentAsString(), "$.legalEntityId");

        List<JsonNode> events = registered("LegalEntityRegistered", legalEntityId);

        assertIdsAreScalar(events, field -> field.equals("legalEntityId"));
        assertThat(events.getFirst().path("legalEntityId").asString(null)).isEqualTo(legalEntityId);
        assertThat(events.getFirst().path("envelope").path("legalEntityId").asString(null)).isEqualTo(legalEntityId);
    }

    @Test
    @DisplayName("R-1b P1 / G-C5 领域事件负载中的 ID 为标量：RoleCreated 的 roleId 是字符串，不是 {\"value\": …}")
    void roleCreatedCarriesAScalarId() throws Exception {
        MockHttpServletResponse created = mvc.perform(post(ROLES).with(csrf()).with(ROLE_ADMINISTRATOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"synthetic_scalar_role\",\"name\":\"Synthetic scalar role\",\"enabled\":true,"
                        + "\"permissionCodes\":[]}"))
                .andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        String roleId = JsonPath.read(created.getContentAsString(), "$.roleId");

        List<JsonNode> events = registered("RoleCreated", roleId);

        assertIdsAreScalar(events, field -> field.equals("roleId"));
        assertThat(events.getFirst().path("roleId").asString(null)).isEqualTo(roleId);
    }

    @Test
    @DisplayName("R-1b P1 / G-C5 领域事件负载中的 ID 为标量：AccountDisabled 的 userId 与 AccountRolesReplaced 的 userId、roleIds 是标量，不是 {\"value\": …}；R-1b P2（RULING-P2-1）账号 ID 是 uuid 字符串，不是员工 ID")
    void accountEventsCarryScalarUserAndRoleIds() throws Exception {
        UUID user = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement account = identity.prepareStatement(
                "insert into user_accounts (user_id, employee_id) values (?, ?) on conflict do nothing")) {
            account.setObject(1, user);
            account.setObject(2, employee);
            account.executeUpdate();
        }
        MockHttpServletResponse role = mvc.perform(post(ROLES).with(csrf()).with(ROLE_ADMINISTRATOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"synthetic_account_role\",\"name\":\"Synthetic account role\",\"enabled\":true,"
                        + "\"permissionCodes\":[]}")).andReturn().getResponse();
        assertThat(role.getStatus()).as(role.getContentAsString()).isEqualTo(201);

        MockHttpServletResponse before = mvc.perform(get(ACCOUNTS + user).with(ROLE_ADMINISTRATOR)).andReturn().getResponse();
        assertThat(before.getStatus()).as(before.getContentAsString()).isEqualTo(200);
        MockHttpServletResponse replaced = mvc.perform(post(ACCOUNTS + user + ":replace-roles").header("If-Match", before.getHeader("ETag")).with(csrf())
                .with(ROLE_ADMINISTRATOR).contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"synthetic_account_role\"]}")).andReturn().getResponse();
        assertThat(replaced.getStatus()).as(replaced.getContentAsString()).isEqualTo(204);

        MockHttpServletResponse middle = mvc.perform(get(ACCOUNTS + user).with(ROLE_ADMINISTRATOR)).andReturn().getResponse();
        MockHttpServletResponse disabled = mvc.perform(post(ACCOUNTS + user + ":disable").header("If-Match", middle.getHeader("ETag")).with(csrf())
                .with(ROLE_ADMINISTRATOR)).andReturn().getResponse();
        assertThat(disabled.getStatus()).as(disabled.getContentAsString()).isEqualTo(204);

        List<JsonNode> replacedEvents = registered("AccountRolesReplaced", user.toString());
        List<JsonNode> disabledEvents = registered("AccountDisabled", user.toString());

        assertIdsAreScalar(replacedEvents, field -> field.equals("userId"));
        assertIdsAreScalar(replacedEvents, field -> field.equals("roleIds"));
        assertIdsAreScalar(disabledEvents, field -> field.equals("userId"));
        assertThat(replacedEvents.getFirst().path("roleIds")).hasSize(1);
        assertThat(UUID.fromString(replacedEvents.getFirst().path("roleIds").get(0).asString())).isNotNull();
        assertThat(replacedEvents.getFirst().path("userId").asString(null)).isEqualTo(user.toString());
        assertThat(disabledEvents.getFirst().path("userId").asString(null)).isEqualTo(user.toString()).isNotEqualTo(employee.toString());
    }
}
