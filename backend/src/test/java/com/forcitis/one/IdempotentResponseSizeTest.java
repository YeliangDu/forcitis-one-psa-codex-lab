package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.IdempotentResponses;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.kernel.DocumentTypeCode;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/**
 * DATA-REPAIR-timesheet-audit TB-02: the stored limit of an answer (32768 characters) is far above what a listed endpoint can answer. Each answer
 * record of a listed endpoint is built with every text at 200 characters, every list at 100 entries (the permission list with every permission code of
 * today) and every number at its maximum; the JSON must stay under half of the limit, so that a growing catalogue fails here before it fails in production.
 */
class IdempotentResponseSizeTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final int HALF_LIMIT = IdempotentResponses.MAX_RESPONSE_LENGTH / 2;
    static final String LONG_TEXT = "x".repeat(200);

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: the limit in the code is the limit in the table's CHECK")
    void limitIsTheLimitOfTheTable() throws Exception {
        String ddl = Files.readString(Path.of("src/main/resources/db/migration/platform_idempotency/V20261014221000__platform_idempotency_baseline.sql"));

        assertThat(ddl).as(GuardMessage.of("TB-02", "IdempotentResponses.MAX_RESPONSE_LENGTH differs from the CHECK on response_text in the platform_idempotency baseline",
                "make the constant and the CHECK of the platform_idempotency migration the same number")).contains("CONSTRAINT ck_idempotent_responses_response_text CHECK (char_length(response_text) <= " + IdempotentResponses.MAX_RESPONSE_LENGTH + ")");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: the largest possible answer of every listed endpoint is under half of the stored limit")
    void largestAnswersAreFarUnderTheLimit() throws Exception {
        Set<String> listed = IdempotentEndpointGuardTest.listed(Files.readAllLines(Path.of("architecture/idempotent-endpoints.txt"))).keySet();
        Map<Class<?>, Integer> sizes = new java.util.LinkedHashMap<>();
        for (IdempotentEndpointScan.Handler handler : IdempotentEndpointScan.postHandlers()) {
            if (listed.contains(handler.template())) {
                for (Class<?> type : IdempotentEndpointScan.answerTypes(handler)) {
                    sizes.computeIfAbsent(type, t -> JSON.writeValueAsString(largest(t)).length());
                }
            }
        }

        assertThat(sizes).as("the answer types of the listed endpoints").isNotEmpty();
        sizes.forEach((type, size) -> assertThat(size).as(GuardMessage.of("TB-02", "the largest possible answer " + type.getSimpleName() + " is " + size + " characters, not under half of the stored limit " + HALF_LIMIT,
                "make the answer smaller (fewer or shorter fields, a bounded list) or take the endpoint off backend/architecture/idempotent-endpoints.txt")).isLessThan(HALF_LIMIT));
        assertThat(sizes.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getValue()).isPositive();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: the problem+json of a refusal, with long texts and extensions, is under half of the stored limit")
    void refusalIsFarUnderTheLimit() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), "d".repeat(200));
        problem.setType(java.net.URI.create("urn:forcitis:error:" + "E".repeat(100)));
        problem.setProperty("errorCode", "E".repeat(100));
        problem.setProperty("correlationId", UUID.randomUUID().toString());
        problem.setProperty("invalidParameters", "p".repeat(2000));

        assertThat(JSON.writeValueAsString(problem).length()).as(GuardMessage.of("TB-02", "the problem+json of a refusal with long texts is not under half of the stored limit",
                "shorten the refusal fields or the extensions the error handler adds")).isLessThan(HALF_LIMIT);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02 injected: a record with a list grown past the limit is measured as too large")
    void tooLargeIsMeasured() {
        record Big(List<String> codes) {
        }

        assertThat(JSON.writeValueAsString(new Big(Arrays.asList(new String[1000]).stream().map(x -> LONG_TEXT).toList())).length()).isGreaterThan(HALF_LIMIT);
        assertThat(JSON.writeValueAsString(new Big(List.of("a", "b"))).length()).isLessThan(HALF_LIMIT);
    }

    private static Object largest(Class<?> type) {
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        RecordComponent[] components = type.getRecordComponents();
        Object[] arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            arguments[i] = value(components[i].getName(), components[i].getGenericType());
        }
        try {
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException failed) {
            throw new IllegalStateException(type.getName(), failed);
        }
    }

    private static Object value(String name, Type type) {
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType().equals(List.class)) {
            Type element = parameterized.getActualTypeArguments()[0];
            if (name.equals("permissionCodes")) {
                return Arrays.stream(Permission.values()).map(Permission::code).toList();
            }
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                items.add(value(name, element));
            }
            return items;
        }
        if (type.equals(String.class)) {
            return LONG_TEXT;
        }
        if (type.equals(UUID.class)) {
            return UUID.randomUUID();
        }
        if (type.equals(boolean.class)) {
            return true;
        }
        if (type.equals(long.class)) {
            return Long.MAX_VALUE;
        }
        if (type.equals(int.class)) {
            return Integer.MAX_VALUE;
        }
        if (type.equals(LocalDate.class)) {
            return LocalDate.MAX;
        }
        if (type.equals(Instant.class)) {
            return Instant.now();
        }
        if (type instanceof Class<?> raw && raw.isEnum()) {
            return Arrays.stream(raw.getEnumConstants()).max(Comparator.comparingInt(c -> c.toString().length())).orElseThrow();
        }
        if (type.equals(DocumentTypeCode.class)) {
            return new DocumentTypeCode("A" + "B".repeat(39));
        }
        if (type instanceof Class<?> raw && raw.isRecord()) {
            return largest(raw);
        }
        throw new IllegalStateException("no maximal value for " + name + " of " + type);
    }
}
