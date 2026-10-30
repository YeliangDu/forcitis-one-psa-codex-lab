package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;

import com.forcitis.one.kernel.EmployeeId;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * Reaches the personnel command use cases, which have no HTTP surface in this phase. The contract is a Spring bean whose type, or one of
 * whose interfaces, is named PersonnelCommands in the workforce api package, with {@code enter(EnterPersonnelCommand)} giving the new
 * command id (a PersonnelCommandId, a UUID, or a record with a personnelCommandId component) and {@code withdraw(WithdrawPersonnelCommand)}.
 * Parameter records are filled by component name from {@link PersonnelCommandNames}; unnamed components stay null. Reflection keeps
 * this file compiling before the service exists: a missing bean, method or component fails the test at run time, it is never skipped.
 */
final class PersonnelCommandsProbe {

    private final Object service;
    private final Method enter;
    private final Method withdraw;
    private final Function<UUID, EmployeeId> employeeIds;
    /** #873: the single release of a separation hold; absent until the SeparationHolds bean exists, which fails only the tests that release. */
    private Object holds;
    private Method release;
    private String holdsMissing = "";

    private PersonnelCommandsProbe(Object service, Method enter, Method withdraw, Function<UUID, EmployeeId> employeeIds) {
        this.service = service;
        this.enter = enter;
        this.withdraw = withdraw;
        this.employeeIds = employeeIds;
    }

    private PersonnelCommandsProbe withHolds(ApplicationContext context) {
        List<String> seen = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null) {
                continue;
            }
            if (type.getSimpleName().equals(HOLDS_API_TYPE)) {
                seen.add(type.getName());
            }
            Class<?> contract = contractOf(ClassUtils.getUserClass(type), HOLDS_API_TYPE);
            if (contract != null) {
                holds = context.getBean(name);
                release = recordMethod(contract, RELEASE_METHOD, RELEASE_PARAMETER);
                return this;
            }
        }
        holdsMissing = "ADR-0068 item 2.3: no bean of a type named " + HOLDS_API_TYPE + " in " + API_PACKAGE + " (same-named types elsewhere: " + seen + ")";
        return this;
    }

    static PersonnelCommandsProbe of(ApplicationContext context, Function<UUID, EmployeeId> employeeIds) {
        List<String> seen = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null) {
                continue;
            }
            if (type.getSimpleName().equals(API_TYPE)) {
                seen.add(type.getName());
            }
            Class<?> contract = contractOf(ClassUtils.getUserClass(type), API_TYPE);
            if (contract != null) {
                return new PersonnelCommandsProbe(context.getBean(name), recordMethod(contract, ENTER_METHOD, ENTER_PARAMETER),
                        recordMethod(contract, WITHDRAW_METHOD, WITHDRAW_PARAMETER), employeeIds).withHolds(context);
            }
        }
        throw new AssertionError("ADR-0068 item 1.6: no bean of a type named " + API_TYPE + " in " + API_PACKAGE + " (same-named types elsewhere: " + seen + ")");
    }

    private static Class<?> contractOf(Class<?> type, String simpleName) {
        List<Class<?>> candidates = new ArrayList<>(ClassUtils.getAllInterfacesForClassAsSet(type));
        candidates.addFirst(type);
        for (Class<?> candidate : candidates) {
            if (candidate.getSimpleName().equals(simpleName) && candidate.getPackageName().equals(API_PACKAGE)) {
                return candidate;
            }
        }
        return null;
    }

    private static Method recordMethod(Class<?> contract, String name, String parameter) {
        for (Method method : contract.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isRecord()
                    && method.getParameterTypes()[0].getSimpleName().equals(parameter)) {
                return method;
            }
        }
        throw new AssertionError(contract.getName() + " has no method " + name + "(" + parameter + ") with one record parameter");
    }

    /** The record components of a parameter, by name: used to prove the caller cannot supply what the server decides. */
    List<String> enterComponents() {
        return names(enter.getParameterTypes()[0]);
    }

    List<String> withdrawComponents() {
        return names(withdraw.getParameterTypes()[0]);
    }

    private static List<String> names(Class<?> record) {
        List<String> names = new ArrayList<>();
        for (RecordComponent component : record.getRecordComponents()) {
            names.add(component.getName());
        }
        return names;
    }

    /** Enters a command; the fields are keyed by the component names of {@link PersonnelCommandNames}. Business refusals propagate. */
    UUID enter(Map<String, Object> fields) {
        return idOf(call(service, enter, fields));
    }

    /** Releases one hold as the signed-in caller; business refusals and permission refusals propagate. */
    void release(UUID separationHoldId, String reason) {
        if (release == null) {
            throw new AssertionError(holdsMissing);
        }
        Map<String, Object> fields = new java.util.HashMap<>();
        fields.put(SEPARATION_HOLD_ID, separationHoldId);
        fields.put(REASON, reason);
        call(holds, release, fields);
    }

    List<String> releaseComponents() {
        if (release == null) {
            throw new AssertionError(holdsMissing);
        }
        return names(release.getParameterTypes()[0]);
    }

    void withdraw(UUID personnelCommandId, String reason) {
        Map<String, Object> fields = new java.util.HashMap<>();
        fields.put(PERSONNEL_COMMAND_ID, personnelCommandId);
        fields.put(REASON, reason);
        call(service, withdraw, fields);
    }

    Object call(Object target, Method method, Map<String, Object> fields) {
        Class<?> parameter = method.getParameterTypes()[0];
        List<String> components = names(parameter);
        for (String name : fields.keySet()) {
            if (!components.contains(name)) {
                throw new AssertionError(parameter.getName() + " has no component " + name + " (agreed in PersonnelCommandNames), found " + components);
            }
        }
        RecordComponent[] declared = parameter.getRecordComponents();
        Object[] arguments = new Object[declared.length];
        Class<?>[] types = new Class<?>[declared.length];
        for (int i = 0; i < declared.length; i++) {
            types[i] = declared[i].getType();
            arguments[i] = convert(types[i], fields.get(declared[i].getName()));
        }
        try {
            return method.invoke(target, parameter.getDeclaredConstructor(types).newInstance(arguments));
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(failure.getCause());
        } catch (ReflectiveOperationException broken) {
            if (broken.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError("cannot build or call " + method, broken);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convert(Class<?> type, Object value) {
        if (value == null || type.isInstance(value) && !(value instanceof UUID && type != UUID.class)) {
            return value;
        }
        if (type.isEnum() && value instanceof String name) {
            return Enum.valueOf((Class<? extends Enum>) type, name);
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

    private static UUID idOf(Object result) {
        if (result instanceof UUID uuid) {
            return uuid;
        }
        if (result != null && result.getClass().isRecord()) {
            RecordComponent[] components = result.getClass().getRecordComponents();
            for (RecordComponent component : components) {
                if (component.getName().equals(PERSONNEL_COMMAND_ID) || components.length == 1 && component.getType() == UUID.class) {
                    try {
                        return idOf(component.getAccessor().invoke(result));
                    } catch (ReflectiveOperationException unreadable) {
                        throw new AssertionError(unreadable);
                    }
                }
            }
        }
        throw new AssertionError("enter must give the new command id (PersonnelCommandId, UUID or a record holding personnelCommandId), not " + result);
    }
}
