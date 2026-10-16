package com.forcitis.one;

import java.io.IOException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Finds the POST handlers of the main code and the types of their answers, for the guards about what an Idempotency-Key may store. */
final class IdempotentEndpointScan {
    /** The types a ResponseEntity of a wildcard can carry: the *Response names and whatever is built inside .body(...). */
    private static final Pattern ANSWER_NAME = Pattern.compile("\\b(\\w*Response)\\b|\\.body\\(\\s*(?:new\\s+)?([A-Z]\\w*)");

    record Handler(String template, Method method, Class<?> controller) {
    }

    private IdempotentEndpointScan() {
    }

    /** Every @PostMapping handler of a @RestController in the main classes (test probes are left out), in path order. */
    static List<Handler> postHandlers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Handler> handlers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.forcitis.one")) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            if (!controller.getProtectionDomain().getCodeSource().getLocation().toString().endsWith("/target/classes/")) {
                continue;
            }
            RequestMapping base = controller.getAnnotation(RequestMapping.class);
            String prefix = base == null || base.value().length == 0 ? "" : base.value()[0];
            for (Method method : controller.getDeclaredMethods()) {
                PostMapping post = method.getAnnotation(PostMapping.class);
                if (post != null) {
                    String[] paths = post.value().length > 0 ? post.value() : post.path();
                    handlers.add(new Handler(prefix + (paths.length == 0 ? "" : paths[0]), method, controller));
                }
            }
        }
        handlers.sort(Comparator.comparing(Handler::template));
        return handlers;
    }

    /** The record types an answer can carry: the declared type with ResponseEntity, lists, arrays and sealed interfaces opened up; for ResponseEntity of a wildcard, the record types the controller source names. Any other answer type fails. */
    static Set<Class<?>> answerTypes(Handler handler) throws IOException {
        Set<Class<?>> types = new LinkedHashSet<>();
        collect(handler.method().getGenericReturnType(), types, handler);
        return types;
    }

    private static void collect(Type type, Set<Class<?>> types, Handler handler) throws IOException {
        if (type instanceof ParameterizedType parameterized) {
            if (parameterized.getRawType() instanceof Class<?> raw && Map.class.isAssignableFrom(raw)) {
                throw unsupported(handler, type);
            }
            for (Type argument : parameterized.getActualTypeArguments()) {
                collect(argument, types, handler);
            }
        } else if (type instanceof GenericArrayType array) {
            collect(array.getGenericComponentType(), types, handler);
        } else if (type instanceof WildcardType) {
            wildcardRecords(handler, types);
        } else if (type instanceof Class<?> raw) {
            if (raw.isArray()) {
                collect(raw.getComponentType(), types, handler);
            } else if (raw.isRecord()) {
                types.add(raw);
            } else if (raw.isSealed()) {
                for (Class<?> permitted : raw.getPermittedSubclasses()) {
                    collect(permitted, types, handler);
                }
            } else if (!raw.equals(void.class) && !raw.equals(Void.class)) {
                throw unsupported(handler, type);
            }
        } else {
            throw unsupported(handler, type);
        }
    }

    private static IllegalStateException unsupported(Handler handler, Type type) {
        return new IllegalStateException(handler.template() + " answers with " + type.getTypeName()
                + ", which the guard cannot open up: use a record (or a sealed interface of records), or take the endpoint off the list");
    }

    private static void wildcardRecords(Handler handler, Set<Class<?>> types) throws IOException {
        String source = Files.readString(Path.of("src/main/java", handler.controller().getName().replace('.', '/') + ".java"));
        String code = JavaCode.withoutComments(source);
        String method = methodText(code, handler.method().getName());
        int before = types.size();
        Matcher named = ANSWER_NAME.matcher(method);
        while (named.find()) {
            for (String candidate : candidates(handler, code, named.group(1) != null ? named.group(1) : named.group(2))) {
                try {
                    Class<?> found = Class.forName(candidate);
                    if (found.isRecord()) {
                        types.add(found);
                    } else if (found.isSealed()) {
                        collect(found, types, handler);
                    }
                    break;
                } catch (ClassNotFoundException notThere) {
                    continue;
                }
            }
        }
        if (types.size() == before) {
            throw new IllegalStateException(handler.template() + " returns ResponseEntity<?> and its source names no record the guard can find");
        }
    }

    /** Where a simple name of the controller source can live: its own package, or the type an import names. */
    private static List<String> candidates(Handler handler, String code, String simpleName) {
        List<String> candidates = new ArrayList<>();
        candidates.add(handler.controller().getPackageName() + "." + simpleName);
        Matcher imported = Pattern.compile("import\\s+([\\w.]+\\." + simpleName + ")\\s*;").matcher(code);
        while (imported.find()) {
            candidates.add(imported.group(1));
        }
        return candidates;
    }

    /** The text of a method, from its return type to the closing brace of its body, plus the methods of the same class it calls, as far as they go. */
    private static String methodText(String source, String name) {
        StringBuilder text = new StringBuilder();
        Set<String> done = new LinkedHashSet<>();
        List<String> todo = new ArrayList<>(List.of(name));
        while (!todo.isEmpty()) {
            String next = todo.remove(0);
            if (!done.add(next)) {
                continue;
            }
            Matcher head = Pattern.compile("[\\w<>?,.\\[\\] ]+\\s+" + next + "\\s*\\(").matcher(source);
            boolean declared = false;
            while (head.find() && !declared) {
                String declaration = head.group().strip();
                if (declaration.startsWith("return") || declaration.startsWith("throw") || declaration.contains("new ")) {
                    continue;
                }
                int paramsEnd = closing(source, head.end() - 1, '(', ')');
                int open = source.indexOf('{', paramsEnd);
                String between = open < 0 ? "x" : source.substring(paramsEnd + 1, open).strip();
                if (!between.isEmpty() && !between.startsWith("throws")) {
                    continue;
                }
                String body = source.substring(head.start(), closing(source, open, '{', '}') + 1);
                text.append(body).append('\n');
                Matcher calls = Pattern.compile("\\b([a-z]\\w*)\\s*\\(").matcher(body);
                while (calls.find()) {
                    todo.add(calls.group(1));
                }
                declared = true;
            }
            if (!declared && next.equals(name)) {
                throw new IllegalStateException("no method " + name);
            }
        }
        return text.toString();
    }

    private static int closing(String code, int open, char up, char down) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            if (code.charAt(i) == up) {
                depth++;
            } else if (code.charAt(i) == down && --depth == 0) {
                return i;
            }
        }
        throw new IllegalStateException("unbalanced " + up + down);
    }

    /** What an answer type reaches: component names (Java and JSON, as written), the simple names of the types, and the types the guard could not open up. */
    record Reach(Set<String> names, Set<String> typeNames, List<String> unsupported) {
    }

    private static final Set<Class<?>> LEAVES = Set.of(String.class, CharSequence.class, UUID.class, Boolean.class, Character.class, Void.class);

    /** The names of all components of the record types, nested records, lists, arrays and sealed alternatives included. */
    static Set<String> componentNames(Set<Class<?>> types) {
        return reach(types).names();
    }

    static Reach reach(Set<Class<?>> types) {
        Reach reach = new Reach(new LinkedHashSet<>(), new LinkedHashSet<>(), new ArrayList<>());
        Set<Type> seen = new LinkedHashSet<>();
        types.forEach(type -> expand(type, reach, seen));
        return reach;
    }

    private static boolean leaf(Class<?> raw) {
        return raw.isPrimitive() || raw.isEnum() || LEAVES.contains(raw) || Number.class.isAssignableFrom(raw) || raw.getName().startsWith("java.time.");
    }

    private static void expand(Type type, Reach reach, Set<Type> seen) {
        if (!seen.add(type)) {
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            if (Map.class.isAssignableFrom(raw)) {
                reach.unsupported().add(type.getTypeName());
            } else {
                if (raw.isRecord()) {
                    expand(raw, reach, seen);
                }
                for (Type argument : parameterized.getActualTypeArguments()) {
                    expand(argument, reach, seen);
                }
            }
        } else if (type instanceof GenericArrayType array) {
            expand(array.getGenericComponentType(), reach, seen);
        } else if (type instanceof Class<?> raw) {
            reach.typeNames().add(raw.getSimpleName());
            if (raw.isArray()) {
                expand(raw.getComponentType(), reach, seen);
            } else if (leaf(raw)) {
                return;
            } else if (raw.isRecord()) {
                for (RecordComponent component : raw.getRecordComponents()) {
                    reach.names().add(component.getName());
                    jsonName(component).ifPresent(reach.names()::add);
                    expand(component.getGenericType(), reach, seen);
                }
            } else if (raw.isSealed()) {
                for (Class<?> permitted : raw.getPermittedSubclasses()) {
                    expand(permitted, reach, seen);
                }
            } else {
                reach.unsupported().add(type.getTypeName());
            }
        } else {
            reach.unsupported().add(type.getTypeName());
        }
    }

    private static Optional<String> jsonName(RecordComponent component) {
        JsonProperty renamed = component.getAnnotation(JsonProperty.class);
        if (renamed == null) {
            renamed = component.getAccessor().getAnnotation(JsonProperty.class);
        }
        return renamed == null || renamed.value().isEmpty() ? Optional.empty() : Optional.of(renamed.value());
    }

    static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }
}
