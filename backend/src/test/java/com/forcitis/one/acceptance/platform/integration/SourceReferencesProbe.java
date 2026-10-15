package com.forcitis.one.acceptance.platform.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * Reaches the source-reference service of platform.integration, which has no HTTP surface for allocation and outbound lookup.
 * The contract (ADR-0033 section 2 items 2 and 5) is a Spring bean whose type, or one of whose interfaces, is named SourceReferences
 * and lives in platform.integration, with:
 * <ul>
 *   <li>{@code allocate(sourceSystem, sourceObjectKind, long sourceId)} returning the own id (a UUID or a typed id wrapping one): the same
 *       source object always gets the same own id, also under concurrent first sight, and the reference is committed on its own;</li>
 *   <li>{@code sourceIdFor(sourceSystem, sourceObjectKind, ownId[, LocalDate on])} returning the source id of the confirmed reference in
 *       effect, and throwing (with an ALERT line) when there is none.</li>
 * </ul>
 * Source system and object kind are enums of the module; the probe passes their constant names. Reflection keeps this file compiling
 * before the service exists: a missing bean or method fails the test at run time, it is never skipped.
 */
final class SourceReferencesProbe {

    private final Object service;
    private final Method allocate;
    private final Method sourceIdFor;

    private SourceReferencesProbe(Object service, Method allocate, Method sourceIdFor) {
        this.service = service;
        this.allocate = allocate;
        this.sourceIdFor = sourceIdFor;
    }

    static SourceReferencesProbe of(ApplicationContext context) {
        List<String> seen = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null) {
                continue;
            }
            Class<?> contract = contractOf(ClassUtils.getUserClass(type));
            if (contract != null) {
                seen.add(contract.getName());
                Method allocate = method(contract, "allocate", 3, 3);
                Method sourceIdFor = method(contract, "sourceIdFor", 3, 4);
                return new SourceReferencesProbe(context.getBean(name), allocate, sourceIdFor);
            }
        }
        throw new AssertionError("ADR-0033 section 2: no bean of a type named SourceReferences in platform.integration " + seen);
    }

    private static Class<?> contractOf(Class<?> type) {
        List<Class<?>> candidates = new ArrayList<>(ClassUtils.getAllInterfacesForClassAsSet(type));
        candidates.addFirst(type);
        for (Class<?> candidate : candidates) {
            if (candidate.getSimpleName().equals("SourceReferences") && candidate.getPackageName().startsWith("com.forcitis.one.platform.integration")) {
                return candidate;
            }
        }
        return null;
    }

    private static Method method(Class<?> contract, String name, int minArity, int maxArity) {
        for (Method method : contract.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() >= minArity && method.getParameterCount() <= maxArity) {
                return method;
            }
        }
        throw new AssertionError("ADR-0033 section 2: " + contract.getName() + " has no method " + name + " with " + minArity + ".." + maxArity + " parameters");
    }

    UUID allocate(String sourceSystem, String sourceObjectKind, long sourceId) {
        Object own = invoke(allocate, sourceSystem, sourceObjectKind, sourceId, null);
        return asUuid(own);
    }

    long sourceIdFor(String sourceSystem, String sourceObjectKind, UUID ownId, LocalDate on) {
        Object found = invoke(sourceIdFor, sourceSystem, sourceObjectKind, ownId, on);
        if (found instanceof Number number) {
            return number.longValue();
        }
        if (found != null && found.getClass().isRecord() && found.getClass().getRecordComponents().length == 1) {
            return ((Number) read(found, found.getClass().getRecordComponents()[0])).longValue();
        }
        throw new AssertionError("sourceIdFor must give the source id itself or throw, not " + found);
    }

    private Object invoke(Method method, String sourceSystem, String sourceObjectKind, Object idArgument, LocalDate on) {
        Class<?>[] types = method.getParameterTypes();
        Object[] arguments = new Object[types.length];
        arguments[0] = constant(types[0], sourceSystem);
        arguments[1] = constant(types[1], sourceObjectKind);
        arguments[2] = idArgument instanceof UUID uuid ? ownId(types[2], uuid) : idArgument;
        if (types.length == 4) {
            if (types[3] != LocalDate.class) {
                throw new AssertionError(method + ": a fourth parameter can only be the date the reference must be in effect on");
            }
            arguments[3] = on;
        }
        try {
            return method.invoke(service, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(failure.getCause());
        } catch (IllegalAccessException inaccessible) {
            throw new AssertionError(inaccessible);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object constant(Class<?> type, String name) {
        if (!type.isEnum()) {
            throw new AssertionError("source system and source object kind are enums of platform.integration, not " + type);
        }
        return Enum.valueOf((Class<? extends Enum>) type, name);
    }

    private static Object ownId(Class<?> type, UUID value) {
        if (type == UUID.class) {
            return value;
        }
        try {
            return type.getDeclaredConstructor(UUID.class).newInstance(value);
        } catch (ReflectiveOperationException notATypedId) {
            throw new AssertionError("the own id parameter is a UUID or a typed id built from one, not " + type, notATypedId);
        }
    }

    private static UUID asUuid(Object own) {
        if (own instanceof UUID uuid) {
            return uuid;
        }
        if (own != null && own.getClass().isRecord()) {
            for (RecordComponent component : own.getClass().getRecordComponents()) {
                if (component.getType() == UUID.class) {
                    return (UUID) read(own, component);
                }
            }
        }
        throw new AssertionError("allocate must give the own id (a UUID or a typed id wrapping one), not " + own);
    }

    private static Object read(Object record, RecordComponent component) {
        try {
            Method accessor = component.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(record);
        } catch (ReflectiveOperationException unreadable) {
            throw new AssertionError(unreadable);
        }
    }
}
