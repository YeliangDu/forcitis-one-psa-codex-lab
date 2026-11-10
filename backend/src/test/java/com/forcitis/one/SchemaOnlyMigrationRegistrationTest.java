package com.forcitis.one;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Card30 G44: only signed F1 schema-stage directory recognition.
 * Baseline H: 60db4a63b5970334f1ced9a8ad7f14d4691c1b44.
 * No runtime module, grants, Docker, or production DDL is created.
 * Author: one-test, 01a1b614-934c-7250-a715-5cf09e9c02c2.
 * Author execution: not run.
 */
class SchemaOnlyMigrationRegistrationTest {
    private static final String SIGNED_REVIEW =
            "docs/data/reviews/accounting-spine-f1.md";

    private static Map<String, Object> context() {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("kind", "schema-only");
        stage.put("slice", "F1");
        stage.put("schema", "operations_facts");
        stage.put("signed_review", SIGNED_REVIEW);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "operations.facts");
        row.put("status", "planned");
        row.put("schema", "operations_facts");
        row.put("migration_stage", stage);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stage(Map<String, Object> row) {
        return (Map<String, Object>) row.get("migration_stage");
    }

    private static Map<String, Object> root(List<Map<String, Object>> rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contexts", rows);
        return result;
    }

    private static Method resolver() {
        try {
            Method method = NamingSurfaceTest.class.getDeclaredMethod(
                    "schemaOnlyMigrationSchemas", Map.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException missing) {
            return fail("G44_F1_SCHEMA_STAGE_CAPABILITY_MISSING");
        }
    }

    @Test
    void exactSignedF1RegistrationAddsOnlyItsSchemaAndNeverAModule()
            throws Exception {
        Method resolver = resolver();
        Map<String, Object> row = context();
        Map<String, Object> other = new LinkedHashMap<>();
        other.put("id", "operations.policy");
        other.put("status", "planned");
        other.put("schema", "operations_policy");
        Map<String, Object> input = root(List.of(row, other));
        String before = input.toString();
        Map<String, String> modules = new TreeMap<>(NamingSurfaceTest.MODULES);
        assertEquals(Set.of("operations_facts"), resolver.invoke(null, input));
        assertEquals(before, input.toString(), "REGISTRATION_INPUT_MUTATED");
        assertEquals(modules, NamingSurfaceTest.MODULES, "RUNTIME_MODULES_CHANGED");
        assertFalse(NamingSurfaceTest.MODULES.containsKey("operations.facts"),
                "F1_MUST_NOT_CLAIM_A_RUNTIME_MODULE");
    }

    @Test
    void plannedUnknownApproximateOrMismatchedDeclarationsDoNotAuthorize()
            throws Exception {
        Method resolver = resolver();
        for (String mutation : List.of(
                "missing-stage", "planned-only", "kind", "slice", "signature",
                "missing-signature", "outer-schema", "stage-schema", "bc",
                "consistent-other-bc", "unknown-schema", "approximate", "prefix", "status",
                "duplicate", "conflicting-duplicate")) {
            Map<String, Object> row = context();
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(row);
            switch (mutation) {
                case "missing-stage" -> row.remove("migration_stage");
                case "planned-only" -> {
                    row.remove("migration_stage");
                    row.put("id", "operations.policy");
                    row.put("schema", "operations_policy");
                }
                case "kind" -> stage(row).put("kind", "runtime");
                case "slice" -> stage(row).put("slice", "F2");
                case "signature" -> stage(row).put(
                        "signed_review", "docs/data/reviews/other.md");
                case "missing-signature" -> stage(row).remove("signed_review");
                case "outer-schema" -> row.put("schema", "operations_policy");
                case "stage-schema" -> stage(row).put("schema", "operations_policy");
                case "bc" -> row.put("id", "operations.policy");
                case "consistent-other-bc" -> {
                    row.put("id", "operations.policy");
                    row.put("schema", "operations_policy");
                    stage(row).put("schema", "operations_policy");
                }
                case "unknown-schema" -> {
                    row.put("id", "unknown.context");
                    row.put("schema", "unknown_context");
                    stage(row).put("schema", "unknown_context");
                }
                case "approximate" -> {
                    row.put("schema", "operations_fact");
                    stage(row).put("schema", "operations_fact");
                }
                case "prefix" -> {
                    row.put("schema", "operations_facts_extra");
                    stage(row).put("schema", "operations_facts_extra");
                }
                case "status" -> row.put("status", "retired");
                case "duplicate" -> rows.add(context());
                case "conflicting-duplicate" -> {
                    Map<String, Object> duplicate = context();
                    stage(duplicate).put("slice", "F2");
                    rows.add(duplicate);
                }
                default -> throw new AssertionError("unknown fixture mutation");
            }
            assertEquals(Set.of(), resolver.invoke(null, root(rows)), mutation);
        }
    }

    @Test
    void originalTimestampPrefixAndEnglishSnakeCaseRulesRemainStrict() {
        String valid = "V20261108060000__operations_facts_baseline.sql";
        var accepted = NamingSurfaceTest.MIGRATION.matcher(valid);
        assertTrue(accepted.matches());
        assertEquals("operations_facts", accepted.group("module"));
        assertEquals("baseline", accepted.group("description"));
        LocalDateTime.parse(accepted.group("ts"), NamingSurfaceTest.TIMESTAMP);

        assertFalse(NamingSurfaceTest.MIGRATION.matcher(
                "V2026110806000__operations_facts_baseline.sql").matches());
        assertFalse(NamingSurfaceTest.MIGRATION.matcher(
                "V20261108060000__Operations_facts_baseline.sql").matches());
        assertThrows(DateTimeParseException.class, () -> LocalDateTime.parse(
                "20260230060000", NamingSurfaceTest.TIMESTAMP));
        assertThrows(DateTimeParseException.class, () -> LocalDateTime.parse(
                "20261108250000", NamingSurfaceTest.TIMESTAMP));

        var wrongPrefix = NamingSurfaceTest.MIGRATION.matcher(
                "V20261108060000__operations_policy_baseline.sql");
        assertTrue(wrongPrefix.matches());
        assertFalse(wrongPrefix.group("module").equals("operations_facts"));
        for (String description : List.of("Baseline", "base__line", "base_1", "base-line")) {
            var matcher = NamingSurfaceTest.MIGRATION.matcher(
                    "V20261108060000__operations_facts_" + description + ".sql");
            assertTrue(matcher.matches());
            assertFalse(matcher.group("description").matches("[a-z]+(_[a-z]+)*"));
        }
    }

    @Test
    void actualG44ConsumesTheExactContextRegistration() throws Exception {
        Map<String, String> modules = new TreeMap<>(NamingSurfaceTest.MODULES);
        new NamingSurfaceTest()
                .flywayFilesAreTimestampModuleEnglishSnakeCaseInTheModuleFolder();
        assertEquals(modules, NamingSurfaceTest.MODULES, "RUNTIME_MODULES_CHANGED");
        assertFalse(NamingSurfaceTest.MODULES.containsKey("operations.facts"));
    }
}
