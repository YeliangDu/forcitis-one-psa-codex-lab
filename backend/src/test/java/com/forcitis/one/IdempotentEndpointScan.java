package com.forcitis.one;

import java.io.IOException;
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
import java.util.Set;
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
    private static final Pattern RESPONSE_NAME = Pattern.compile("\\b([A-Z]\\w*Response)\\b");

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

    /** The record types an answer can carry: the declared type with ResponseEntity, lists and the like opened up; for ResponseEntity of a wildcard, the *Response types the controller source names. */
    static Set<Class<?>> answerTypes(Handler handler) throws IOException {
        Set<Class<?>> types = new LinkedHashSet<>();
        collect(handler.method().getGenericReturnType(), types, handler);
        return types;
    }

    private static void collect(Type type, Set<Class<?>> types, Handler handler) throws IOException {
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                collect(argument, types, handler);
            }
        } else if (type instanceof WildcardType) {
            String source = Files.readString(Path.of("src/main/java", handler.controller().getName().replace('.', '/') + ".java"));
            Matcher named = RESPONSE_NAME.matcher(methodText(JavaCode.withoutComments(source), handler.method().getName()));
            while (named.find()) {
                try {
                    Class<?> candidate = Class.forName(handler.controller().getPackageName() + "." + named.group(1));
                    if (candidate.isRecord()) {
                        types.add(candidate);
                    }
                } catch (ClassNotFoundException notInThisPackage) {
                    continue;
                }
            }
        } else if (type instanceof Class<?> raw && raw.isRecord()) {
            types.add(raw);
        }
    }

    /** The text of the method that returns ResponseEntity of a wildcard: from its name to the closing brace of its body. */
    private static String methodText(String source, String name) {
        Matcher head = Pattern.compile("ResponseEntity<\\?>\\s+" + name + "\\s*\\(").matcher(source);
        if (!head.find()) {
            throw new IllegalStateException("no method " + name + " returning ResponseEntity<?>");
        }
        int open = source.indexOf('{', head.end());
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            if (source.charAt(i) == '{') {
                depth++;
            } else if (source.charAt(i) == '}' && --depth == 0) {
                return source.substring(head.start(), i);
            }
        }
        throw new IllegalStateException("unbalanced braces in " + name);
    }

    /** The names of all components of the record types, nested records and generic arguments included. */
    static Set<String> componentNames(Set<Class<?>> types) {
        Set<String> names = new LinkedHashSet<>();
        types.forEach(type -> expand(type, names, new LinkedHashSet<>()));
        return names;
    }

    private static void expand(Type type, Set<String> names, Set<Type> seen) {
        if (!seen.add(type)) {
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                expand(argument, names, seen);
            }
        } else if (type instanceof Class<?> raw && raw.isRecord()) {
            for (RecordComponent component : raw.getRecordComponents()) {
                names.add(component.getName());
                expand(component.getGenericType(), names, seen);
            }
        }
    }

    static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }
}
