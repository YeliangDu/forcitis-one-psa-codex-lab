package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;

import com.forcitis.one.kernel.EmployeeId;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * Reaches the org-structure command use cases, which have no HTTP surface in this phase: a Spring bean whose type, or one of whose
 * interfaces, is named OrgStructureCommands in the workforce api package (see {@link OrgStructureCommandNames}). Methods are looked up
 * when they are called, so a missing one fails only the tests that need it; a missing bean fails every test. Parameter records are
 * filled by component name; unnamed components stay null. Reflection keeps this file compiling before the service exists.
 */
final class OrgStructureCommandsProbe {

    /** An illegal value for an enum-typed component: the api's type cannot even be built, which counts as the api refusing it. */
    static final class TypeRefused extends RuntimeException {
        TypeRefused(String message) {
            super(message);
        }
    }

    private final Object service;
    private final Class<?> contract;
    private final Function<UUID, EmployeeId> employeeIds;

    private OrgStructureCommandsProbe(Object service, Class<?> contract, Function<UUID, EmployeeId> employeeIds) {
        this.service = service;
        this.contract = contract;
        this.employeeIds = employeeIds;
    }

    static OrgStructureCommandsProbe of(ApplicationContext context, Function<UUID, EmployeeId> employeeIds) {
        List<String> seen = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null) {
                continue;
            }
            if (type.getSimpleName().equals(API_TYPE)) {
                seen.add(type.getName());
            }
            List<Class<?>> candidates = new ArrayList<>(ClassUtils.getAllInterfacesForClassAsSet(ClassUtils.getUserClass(type)));
            candidates.addFirst(ClassUtils.getUserClass(type));
            for (Class<?> candidate : candidates) {
                if (candidate.getSimpleName().equals(API_TYPE) && candidate.getPackageName().equals(API_PACKAGE)) {
                    return new OrgStructureCommandsProbe(context.getBean(name), candidate, employeeIds);
                }
            }
        }
        throw new AssertionError("ADR-0068 item 4: no bean of a type named " + API_TYPE + " in " + API_PACKAGE + " (same-named types elsewhere: " + seen + ")");
    }

    private Method method(String name, String parameter) {
        for (Method method : contract.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1 && (parameter == null || method.getParameterTypes()[0].isRecord()
                    && method.getParameterTypes()[0].getSimpleName().equals(parameter))) {
                return method;
            }
        }
        throw new AssertionError(contract.getName() + " has no method " + name + (parameter == null ? "(one parameter)" : "(" + parameter + ")"));
    }

    /** The record components of the enter parameter, by name: used to prove the caller cannot supply what the server decides. */
    List<String> enterComponents() {
        return names(method(ENTER_METHOD, ENTER_PARAMETER).getParameterTypes()[0]);
    }

    private static List<String> names(Class<?> record) {
        List<String> names = new ArrayList<>();
        for (RecordComponent component : record.getRecordComponents()) {
            names.add(component.getName());
        }
        return names;
    }

    /** Enters a command; the fields are keyed by the component names of {@link OrgStructureCommandNames}. Business refusals propagate. */
    UUID enter(Map<String, Object> fields) {
        return idOf(call(method(ENTER_METHOD, ENTER_PARAMETER), fields));
    }

    void withdraw(UUID orgStructureCommandId, String reason) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(ORG_STRUCTURE_COMMAND_ID, orgStructureCommandId);
        fields.put(REASON, reason);
        call(method(WITHDRAW_METHOD, WITHDRAW_PARAMETER), fields);
    }

    /** The employees the merge would move, as employee ids. Writes nothing. */
    List<UUID> previewMerge(UUID mergedUnit, UUID receivingUnit, LocalDate effectiveDate) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(TARGET_ORG_UNIT_ID, mergedUnit);
        fields.put(MERGE_INTO_ORG_UNIT_ID, receivingUnit);
        fields.put(EFFECTIVE_DATE, effectiveDate);
        List<UUID> found = new ArrayList<>();
        collect(call(method(PREVIEW_MERGE_METHOD, PREVIEW_PARAMETER), fields), found);
        return found;
    }

    void executeMerge(UUID orgStructureCommandId) {
        Method method = method(EXECUTE_MERGE_METHOD, null);
        Class<?> type = method.getParameterTypes()[0];
        Map<String, Object> fields = new HashMap<>();
        fields.put(ORG_STRUCTURE_COMMAND_ID, orgStructureCommandId);
        invoke(method, type.isRecord() && names(type).contains(ORG_STRUCTURE_COMMAND_ID) ? build(type, fields) : convert(type, orgStructureCommandId));
    }

    private Object call(Method method, Map<String, Object> fields) {
        Class<?> parameter = method.getParameterTypes()[0];
        for (String name : fields.keySet()) {
            if (!names(parameter).contains(name)) {
                throw new AssertionError(parameter.getName() + " has no component " + name + " (agreed in OrgStructureCommandNames), found " + names(parameter));
            }
        }
        return invoke(method, build(parameter, fields));
    }

    private Object build(Class<?> parameter, Map<String, Object> fields) {
        RecordComponent[] declared = parameter.getRecordComponents();
        Object[] arguments = new Object[declared.length];
        Class<?>[] types = new Class<?>[declared.length];
        for (int i = 0; i < declared.length; i++) {
            types[i] = declared[i].getType();
            arguments[i] = convert(types[i], fields.get(declared[i].getName()));
        }
        try {
            return parameter.getDeclaredConstructor(types).newInstance(arguments);
        } catch (InvocationTargetException failure) {
            throw rethrow(failure);
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError("cannot build " + parameter, broken);
        }
    }

    private Object invoke(Method method, Object argument) {
        try {
            return method.invoke(service, argument);
        } catch (InvocationTargetException failure) {
            throw rethrow(failure);
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError("cannot call " + method, broken);
        }
    }

    private static RuntimeException rethrow(InvocationTargetException failure) {
        if (failure.getCause() instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure.getCause() instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(failure.getCause());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convert(Class<?> type, Object value) {
        if (value == null || type.isInstance(value) && !(value instanceof UUID && type != UUID.class)) {
            return value;
        }
        if (type.isEnum() && value instanceof String name) {
            try {
                return Enum.valueOf((Class<? extends Enum>) type, name);
            } catch (IllegalArgumentException notAConstant) {
                // The type itself refuses the value: that is a refusal at the api boundary, not for the probe to decide.
                throw new TypeRefused(type.getName() + " has no constant " + name);
            }
        }
        if (value instanceof UUID uuid) {
            if (type == EmployeeId.class) {
                return employeeIds.apply(uuid);
            }
            try {
                return type.getDeclaredConstructor(UUID.class).newInstance(uuid);
            } catch (ReflectiveOperationException notATypedId) {
                throw new AssertionError("a UUID argument needs a UUID or a typed id built from one, not " + type, notATypedId);
            }
        }
        if (type == boolean.class && value instanceof Boolean || type == LocalDate.class && value instanceof LocalDate) {
            return value;
        }
        throw new AssertionError("cannot give " + value.getClass() + " for a component of type " + type);
    }

    /** Employee ids out of whatever the preview returns: ids, records holding an employeeId, or a record holding such a collection. */
    private static void collect(Object result, List<UUID> found) {
        if (result instanceof Collection<?> all) {
            all.forEach(each -> collect(each, found));
        } else if (result instanceof UUID uuid) {
            found.add(uuid);
        } else if (result instanceof EmployeeId employee) {
            found.add(employee.value());
        } else if (result != null && result.getClass().isRecord()) {
            RecordComponent[] components = result.getClass().getRecordComponents();
            for (RecordComponent component : components) {
                if (component.getName().equals("employeeId") || components.length == 1 || Collection.class.isAssignableFrom(component.getType())) {
                    try {
                        collect(component.getAccessor().invoke(result), found);
                    } catch (ReflectiveOperationException unreadable) {
                        throw new AssertionError(unreadable);
                    }
                    return;
                }
            }
            throw new AssertionError("cannot read employee ids from " + result);
        } else if (result != null) {
            throw new AssertionError("cannot read employee ids from " + result.getClass());
        }
    }

    private static UUID idOf(Object result) {
        if (result instanceof UUID uuid) {
            return uuid;
        }
        if (result != null && result.getClass().isRecord()) {
            RecordComponent[] components = result.getClass().getRecordComponents();
            for (RecordComponent component : components) {
                if (component.getName().equals(ORG_STRUCTURE_COMMAND_ID) || components.length == 1 && component.getType() == UUID.class) {
                    try {
                        return idOf(component.getAccessor().invoke(result));
                    } catch (ReflectiveOperationException unreadable) {
                        throw new AssertionError(unreadable);
                    }
                }
            }
        }
        throw new AssertionError("enter must give the new command id (OrgStructureCommandId, UUID or a record holding orgStructureCommandId), not " + result);
    }
}
