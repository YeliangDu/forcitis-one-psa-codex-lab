package com.forcitis.one.acceptance.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.SessionRoles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * R-1b P5 G-A1, generic (card acceptance item 8): every path parameter and query parameter that the exported OpenAPI document declares
 * as "format: uuid" is answered with a 400 ProblemDetail when it is not a UUID. The requests are made from the document itself, so a new
 * endpoint is covered the day it is exported: GET as it is, a write with a well-formed Idempotency-Key and If-Match and an empty JSON
 * body, every other path and required query parameter filled with a value of its declared type. The caller holds every permission, so
 * that only the id can be what is refused.
 */
class OpenApiUuidParametersIT extends AcceptanceSupport {

    static final Path OPENAPI = Path.of("../frontend/packages/api/openapi.json");
    static final List<String> NOT_UUIDS = List.of("42", "not-a-uuid");
    static final Set<String> WRITES = Set.of("post", "put", "patch", "delete");
    static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");

    static final TestDatabase postgres = TestPostgres.database(OpenApiUuidParametersIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static RequestPostProcessor everything() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + SessionRoles.ORDINARY));
        Arrays.stream(Permission.values()).forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission.code())));
        return SecurityMockMvcRequestPostProcessors.user(principalName(51)).authorities(authorities);
    }

    private static JsonNode resolve(JsonNode root, JsonNode node) {
        JsonNode current = node;
        while (current.has("$ref")) {
            JsonNode target = root;
            for (String step : current.path("$ref").asString().substring(2).split("/")) {
                target = target.path(step);
            }
            current = target;
        }
        return current;
    }

    private static boolean isUuid(JsonNode schema) {
        return "uuid".equals(schema.path("format").asString(null));
    }

    /** A value of the declared type for a parameter that is not the one under test. */
    private static String filler(JsonNode schema) {
        if (isUuid(schema)) {
            return UUID.randomUUID().toString();
        }
        if (schema.has("enum")) {
            return schema.path("enum").get(0).asString();
        }
        String type = schema.path("type").toString();
        if (type.contains("integer") || type.contains("number")) {
            return "2026";
        }
        if ("date".equals(schema.path("format").asString(null))) {
            return "2026-11-02";
        }
        if ("date-time".equals(schema.path("format").asString(null))) {
            return "2026-11-02T02:00:00Z";
        }
        return "x";
    }

    private record Parameter(String name, String where, boolean required, JsonNode schema) {
    }

    private static List<Parameter> parametersOf(JsonNode root, JsonNode pathItem, JsonNode operation) {
        List<Parameter> found = new ArrayList<>();
        for (JsonNode list : List.of(pathItem.path("parameters"), operation.path("parameters"))) {
            for (JsonNode raw : list) {
                JsonNode parameter = resolve(root, raw);
                found.add(new Parameter(parameter.path("name").asString(), parameter.path("in").asString(), parameter.path("required").asBoolean(false),
                        resolve(root, parameter.path("schema"))));
            }
        }
        return found;
    }

    @Test
    @DisplayName("R-1b P5 G-A1（通用）：OpenAPI 中每个 format: uuid 的路径参数与查询参数，传非 uuid 都答 400 ProblemDetail——从导出的文档逐个生成请求，新接口自动纳入")
    void everyUuidParameterRefusesWhatIsNotAUuid() throws IOException {
        JsonNode root = JsonMapper.builder().build().readTree(Files.readString(OPENAPI));
        RequestPostProcessor caller = everything();
        List<String> wrong = new ArrayList<>();
        int asked = 0;
        for (var path : root.path("paths").properties()) {
            for (var operation : path.getValue().properties()) {
                if (!METHODS.contains(operation.getKey())) {
                    continue;
                }
                List<Parameter> parameters = parametersOf(root, path.getValue(), operation.getValue());
                for (Parameter under : parameters) {
                    if (!isUuid(under.schema()) || !(under.where().equals("path") || under.where().equals("query"))) {
                        continue;
                    }
                    for (String value : NOT_UUIDS) {
                        asked++;
                        try {
                            String problem = refusal(path.getKey(), operation.getKey(), parameters, under, value, caller);
                            if (problem != null) {
                                wrong.add(operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey() + " " + under.name() + "=" + value + ": " + problem);
                            }
                        } catch (Exception failure) {
                            wrong.add(operation.getKey() + " " + path.getKey() + " " + under.name() + "=" + value + ": " + failure);
                        }
                    }
                }
            }
        }

        assertThat(asked).as("uuid parameters found in the OpenAPI document (times values)").isGreaterThan(40);
        assertThat(wrong).isEmpty();
    }

    private String refusal(String template, String method, List<Parameter> parameters, Parameter under, String value, RequestPostProcessor caller) throws Exception {
        String url = template;
        for (Parameter parameter : parameters) {
            if (parameter.where().equals("path")) {
                url = url.replace("{" + parameter.name() + "}", parameter == under ? value : filler(parameter.schema()));
            }
        }
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method.toUpperCase(Locale.ROOT)), url).with(caller);
        for (Parameter parameter : parameters) {
            if (parameter.where().equals("query") && (parameter == under || parameter.required())) {
                builder.param(parameter.name(), parameter == under ? value : filler(parameter.schema()));
            }
        }
        if (WRITES.contains(method)) {
            builder.with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString()).header("If-Match", "\"1\"")
                    .contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        MockHttpServletResponse answer = mvc.perform(builder).andReturn().getResponse();
        if (answer.getStatus() != 400) {
            return "answered " + answer.getStatus();
        }
        if (answer.getContentType() == null || !answer.getContentType().startsWith("application/problem+json")) {
            return "answered 400 as " + answer.getContentType();
        }
        return null;
    }
}
