package com.forcitis.one.acceptance.identity.p1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Proposed frozen P1 seam (reflection keeps the pre-P1 baseline compilable):
 * kernel.PermissionCode(String), PermissionGrantMode enum,
 * PermissionDeclaration(PermissionCode, PermissionGrantMode, boolean),
 * PermissionContribution interface: owner():String, permissions():List.
 * identity.api.PermissionCatalogue.assemble(List legacy, List contributions,
 * Set sensitive, Set annotationReferences, Set roleGrants, Set administratorReferences)
 * validates and returns a catalogue with entries():Map<String,PermissionDeclaration>.
 * fromClasspath() uses the shared plain production-class contribution scan; Spring
 * exposes the same validated catalogue as a bean, never inferred from annotations.
 * NamingSurfaceTest G43 must use that same fromClasspath path.
 * No synthetic declaration here is a bean or production permission/role grant.
 */
final class PermissionP1Fixture {
    static final String CATALOGUE = "com.forcitis.one.platform.identity.api.PermissionCatalogue";
    static final String KERNEL = "com.forcitis.one.kernel.";
    static final Set<String> SENSITIVE = Set.of(
            "platform.identity:account-role:update", "platform.audit:audit-entry:read", "platform.audit:audit-entry:export",
            "platform.numbering:numbering-rule:approve", "platform.numbering:voucher-numbering-rule:approve",
            "organization.workforce:separation-hold:approve", "organization.payroll:bonus-input:submit",
            "organization.payroll:bonus-input:update", "organization.payroll:bonus-input:read");

    private PermissionP1Fixture() {}

    static Class<?> type(String name) {
        try { return Class.forName(name); }
        catch (ClassNotFoundException absent) { return fail("Missing ADR-0091 P1 entry: " + name, absent); }
    }

    static Object invoke(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        try {
            Class<?> clazz = target instanceof Class<?> c ? c : target.getClass();
            return clazz.getMethod(method, types).invoke(target instanceof Class<?> ? null : target, args);
        } catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof RuntimeException e) throw e;
            if (wrapped.getCause() instanceof Error e) throw e;
            throw wrapped;
        }
    }

    static Object declaration(String code, String mode, boolean deprecated) throws Exception {
        Class<?> codeType = type(KERNEL + "PermissionCode");
        Class<?> modeType = type(KERNEL + "PermissionGrantMode");
        Object grantMode = modeType.getMethod("valueOf", String.class).invoke(null, mode);
        return type(KERNEL + "PermissionDeclaration").getConstructor(codeType, modeType, boolean.class)
                .newInstance(codeType.getConstructor(String.class).newInstance(code), grantMode, deprecated);
    }

    static Object contribution(String owner, List<Object> entries) {
        Class<?> contribution = type(KERNEL + "PermissionContribution");
        return Proxy.newProxyInstance(contribution.getClassLoader(), new Class<?>[] {contribution}, (proxy, method, args) -> switch (method.getName()) {
            case "owner" -> owner;
            case "permissions" -> entries;
            case "toString" -> "synthetic:" + owner;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new AssertionError("unexpected contribution method: " + method.getName());
        });
    }

    static List<String> lines(String name) throws Exception {
        var stream = PermissionP1Fixture.class.getResourceAsStream("/acceptance/identity/p1/" + name);
        assertThat(stream).as("frozen base fixture " + name).isNotNull();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) { return reader.lines().toList(); }
    }

    static Map<String, String> frozen() throws Exception {
        Map<String, String> values = new TreeMap<>();
        for (String line : lines("permissions.tsv")) {
            String[] fields = line.split("\t");
            values.put(fields[0], fields[1] + ":" + fields[2]);
        }
        return values;
    }

    static List<Object> legacy() throws Exception {
        List<Object> entries = new ArrayList<>();
        for (var entry : frozen().entrySet()) {
            String[] flags = entry.getValue().split(":");
            entries.add(declaration(entry.getKey(), flags[0], Boolean.parseBoolean(flags[1])));
        }
        return entries;
    }

    static Object assemble(List<Object> legacy, List<Object> contributions, Set<String> sensitive,
                           Set<String> references, Set<String> grants, Set<String> admins) throws Exception {
        return invoke(type(CATALOGUE), "assemble", new Class<?>[] {List.class, List.class, Set.class, Set.class, Set.class, Set.class},
                legacy, contributions, sensitive, references, grants, admins);
    }

    static Object assemble(List<Object> contributions) throws Exception {
        return assemble(legacy(), contributions, SENSITIVE, Set.of(), Set.of(), Set.of());
    }

    static Map<String, String> snapshot(Object catalogue) throws Exception {
        Object raw = invoke(catalogue, "entries", new Class<?>[0]);
        assertThat(raw).isInstanceOf(Map.class);
        Map<String, String> result = new TreeMap<>();
        for (var entry : ((Map<?, ?>) raw).entrySet()) {
            Object value = entry.getValue();
            Object code = invoke(value, "code", new Class<?>[0]);
            assertThat(invoke(code, "value", new Class<?>[0])).isEqualTo(entry.getKey());
            result.put((String) entry.getKey(), invoke(value, "grantMode", new Class<?>[0]) + ":"
                    + invoke(value, "deprecated", new Class<?>[0]));
        }
        return result;
    }

    static Set<String> twoPerson(Map<String, String> entries) {
        return entries.entrySet().stream().filter(e -> e.getValue().equals("TWO_PERSON_INDIVIDUAL:false"))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }
}
