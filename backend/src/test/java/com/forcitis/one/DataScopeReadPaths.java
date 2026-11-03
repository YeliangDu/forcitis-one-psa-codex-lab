package com.forcitis.one;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** The register of HTTP read handlers and the data scope each answers under (#730, I-10): the parser, the enumeration of the GET handlers a context maps, and the comparison of the two. */
final class DataScopeReadPaths {
    enum Mode { SCOPED, SELF, NOT_SCOPE_BOUND }

    record Line(Mode mode, String reason) {
    }

    private static final String RULE = "#730 data-scope read-path register / security-invariants I-10";

    private DataScopeReadPaths() {
    }

    /** "GET path" -> mode and reason, in file order. A line that is malformed or lists a key twice is a problem, not skipped. */
    static Map<String, Line> parse(List<String> lines, List<String> problems) {
        Map<String, Line> rows = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            String[] key = parts[0].strip().split("\\s+");
            Mode mode = parts.length == 3 ? mode(parts[1].strip()) : null;
            if (key.length != 2 || !key[0].equals("GET") || !key[1].startsWith("/") || mode == null) {
                problems.add(GuardMessage.of(RULE, "a register line is not `GET <path> | SCOPED, SELF or NOT_SCOPE_BOUND | <reason>`: " + line,
                        "write it as `GET /api/v1/... | SCOPED | reason`; only GET handlers are registered"));
                continue;
            }
            String reason = parts[2].strip();
            if (mode == Mode.NOT_SCOPE_BOUND && reason.isEmpty()) {
                problems.add(GuardMessage.of(RULE, key[0] + " " + key[1] + " is NOT_SCOPE_BOUND without a reason",
                        "say in the third column why the response carries no person-bound or org-unit-bound rows; a reviewer confirms it"));
            }
            if (rows.put(key[0] + " " + key[1], new Line(mode, reason)) != null) {
                problems.add(GuardMessage.of(RULE, "the register has two lines for " + key[0] + " " + key[1], "delete the duplicate"));
            }
        }
        return rows;
    }

    private static Mode mode(String text) {
        for (Mode mode : Mode.values()) {
            if (mode.name().equals(text)) {
                return mode;
            }
        }
        return null;
    }

    /** "GET pattern" for every pattern of every mapping that serves GET; a mapping without a method serves GET too. */
    static Set<String> getHandlers(RequestMappingHandlerMapping mappings) {
        Set<String> keys = new TreeSet<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            if ((methods.isEmpty() || methods.contains(RequestMethod.GET)) && info.getPathPatternsCondition() != null) {
                info.getPathPatternsCondition().getPatternValues().forEach(pattern -> keys.add("GET " + pattern));
            }
        }
        return keys;
    }

    /** Everything wrong between the handlers a context maps and the register: unlisted handlers, dangling lines, bad lines. Empty means they agree. */
    static List<String> problems(Set<String> handlers, List<String> registerLines) {
        List<String> problems = new ArrayList<>();
        Set<String> listed = parse(registerLines, problems).keySet();
        Set<String> unlisted = new LinkedHashSet<>(handlers);
        unlisted.removeAll(listed);
        Set<String> dangling = new LinkedHashSet<>(listed);
        dangling.removeAll(handlers);
        unlisted.forEach(key -> problems.add(GuardMessage.of(RULE, key + " is a GET handler that is not in the register",
                "add `" + key + " | SCOPED, SELF or NOT_SCOPE_BOUND | reason` to backend/architecture/data-scope-read-paths.txt after deciding which data scope its rows answer under")));
        dangling.forEach(key -> problems.add(GuardMessage.of(RULE, "the register lists " + key + " but no GET handler is mapped to it",
                "delete the line from backend/architecture/data-scope-read-paths.txt, or correct the path")));
        return problems;
    }
}
