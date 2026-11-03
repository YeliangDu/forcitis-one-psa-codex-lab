package com.forcitis.one;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.function.support.RouterFunctionMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMethodMapping;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/** The register of HTTP read handlers and the data scope each answers under (#730, I-10): the parser, the enumeration of the GET handlers a context maps, and the comparison of the two. */
final class DataScopeReadPaths {
    enum Mode { SCOPED, SELF, NOT_SCOPE_BOUND }

    record Line(Mode mode, String reason) {
    }

    private static final String RULE = "#730 data-scope read-path register / security-invariants I-10";

    /**
     * The HandlerMapping classes this register accepts, by exact class: a subclass, an anonymous one or one of ours can route to any handler and is reported. The
     * welcome-page-not-acceptable one only answers 406 to a non-HTML request for "/", so it serves no rows.
     */
    private static final Set<Class<?>> KNOWN_MAPPINGS = Stream.of(
            "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping",
            "org.springframework.web.servlet.handler.SimpleUrlHandlerMapping",
            "org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping",
            "org.springframework.web.servlet.function.support.RouterFunctionMapping",
            "org.springframework.boot.webmvc.autoconfigure.WelcomePageHandlerMapping",
            "org.springframework.boot.webmvc.autoconfigure.WelcomePageNotAcceptableHandlerMapping",
            "org.springframework.boot.webmvc.actuate.endpoint.web.WebMvcEndpointHandlerMapping",
            "org.springframework.boot.webmvc.actuate.endpoint.web.ControllerEndpointHandlerMapping",
            "org.springframework.boot.webmvc.actuate.endpoint.web.AdditionalHealthEndpointPathsWebMvcHandlerMapping")
            .map(DataScopeReadPaths::load).collect(Collectors.toUnmodifiableSet());

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("the known HandlerMapping " + name + " is not on the classpath; update KNOWN_MAPPINGS", e);
        }
    }

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

    /**
     * One "GET pattern" per pattern of every mapping that serves GET (a mapping without a method serves GET too; a HEAD-only one is "HEAD pattern" and never matches a
     * register line) in every HandlerMapping of the context: controller and actuator endpoints of the servlet context, and the URL mappings (patterns, root handler, default handler). Endpoints on a separate management port live in a child context and are out of this register. A RouterFunction cannot
     * be enumerated by path, so one that is present is a problem.
     */
    static List<String> getHandlers(Collection<? extends HandlerMapping> mappings, List<String> problems) {
        List<String> keys = new ArrayList<>();
        for (HandlerMapping mapping : mappings) {
            if (!KNOWN_MAPPINGS.contains(mapping.getClass())) {
                problems.add(GuardMessage.of(RULE, mapping.getClass().getName() + " is a HandlerMapping this register does not know, so its routes cannot be enumerated",
                        "serve the read from a controller method, or ask design to extend the register to this kind of mapping"));
            } else if (mapping instanceof AbstractHandlerMethodMapping<?> methodMapping) {
                Set<String> framework = new LinkedHashSet<>();
                methodMapping.getHandlerMethods().forEach((key, handler) -> {
                    if (!(key instanceof RequestMappingInfo info)) {
                        problems.add(GuardMessage.of(RULE, "a handler mapping holds a key that is not a RequestMappingInfo: " + key,
                                "serve the read from a controller method, or ask design to extend the register to this kind of mapping"));
                    } else {
                        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                        String verb = methods.isEmpty() || methods.contains(RequestMethod.GET) ? "GET" : methods.contains(RequestMethod.HEAD) ? "HEAD" : null;
                        if (verb != null) {
                            // BasicErrorController maps /error twice (HTML and JSON, by produces), both answering the failed request's status and no rows: one key for the pair
                            boolean pair = handler.getBeanType() == BasicErrorController.class;
                            info.getPatternValues().forEach(pattern -> (pair ? framework : keys).add(verb + " " + pattern));
                        }
                    }
                });
                keys.addAll(framework);
            } else if (mapping instanceof AbstractUrlHandlerMapping urlMapping) {
                urlMapping.getHandlerMap().keySet().forEach(pattern -> keys.add("GET " + pattern));
                if (urlMapping.getRootHandler() != null) {
                    keys.add("GET /");
                }
                if (urlMapping.getDefaultHandler() != null) {
                    keys.add("GET <default handler of " + urlMapping.getClass().getSimpleName() + ">");
                }
            } else if (mapping instanceof RouterFunctionMapping routerMapping) {
                if (routerMapping.getRouterFunction() != null) {
                    problems.add(GuardMessage.of(RULE, "the context maps a RouterFunction, whose routes cannot be matched against the register",
                            "serve the read from a controller method, or ask design to extend the register to router functions"));
                }
            }
        }
        return keys;
    }

    /** Everything wrong between the handlers a context maps and the register: unlisted handlers, dangling lines, bad lines. Empty means they agree. */
    static List<String> problems(List<String> handlers, List<String> registerLines) {
        List<String> problems = new ArrayList<>();
        Set<String> listed = parse(registerLines, problems).keySet();
        Set<String> distinct = new TreeSet<>(handlers);
        distinct.forEach(key -> {
            if (Collections.frequency(handlers, key) > 1) {
                problems.add(GuardMessage.of(RULE, key + " is served by " + Collections.frequency(handlers, key) + " handlers (same path, different params, headers, consumes or produces), and one register line cannot cover them",
                        "give each read its own path, so each has its own line and its own scope test"));
            }
        });
        Set<String> unlisted = new LinkedHashSet<>(distinct);
        unlisted.removeAll(listed);
        Set<String> dangling = new LinkedHashSet<>(listed);
        dangling.removeAll(distinct);
        unlisted.forEach(key -> problems.add(GuardMessage.of(RULE, key + " is a GET handler that is not in the register",
                "add `" + key + " | SCOPED, SELF or NOT_SCOPE_BOUND | reason` to backend/architecture/data-scope-read-paths.txt after deciding which data scope its rows answer under")));
        dangling.forEach(key -> problems.add(GuardMessage.of(RULE, "the register lists " + key + " but no GET handler is mapped to it",
                "delete the line from backend/architecture/data-scope-read-paths.txt, or correct the path")));
        return problems;
    }
}
