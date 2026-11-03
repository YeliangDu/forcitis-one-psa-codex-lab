package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.API_PACKAGE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REASON;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * Reaches the countersign use cases (approve or reject one request row as the signed-in caller) by the names the #894 acceptance part
 * agreed: a bean named AttributionCountersigns in the workforce api package, one record parameter each. Looked up at run time so that this
 * directory compiles before the service exists; a missing bean or method fails the test, it is never skipped. The caller's security
 * context is set by the test before the call.
 */
final class OrgCountersignProbe {

    private final Object service;
    private final Class<?> contract;

    private OrgCountersignProbe(Object service, Class<?> contract) {
        this.service = service;
        this.contract = contract;
    }

    static OrgCountersignProbe of(ApplicationContext context) {
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null) {
                continue;
            }
            List<Class<?>> candidates = new ArrayList<>(ClassUtils.getAllInterfacesForClassAsSet(ClassUtils.getUserClass(type)));
            candidates.addFirst(ClassUtils.getUserClass(type));
            for (Class<?> candidate : candidates) {
                if (candidate.getSimpleName().equals(COUNTERSIGN_API_TYPE) && candidate.getPackageName().equals(API_PACKAGE)) {
                    return new OrgCountersignProbe(context.getBean(name), candidate);
                }
            }
        }
        throw new AssertionError("ADR-0073 item 3.6: no bean of a type named " + COUNTERSIGN_API_TYPE + " in " + API_PACKAGE);
    }

    void approve(UUID countersignId) {
        call(APPROVE_METHOD, APPROVE_PARAMETER, countersignId, null);
    }

    void reject(UUID countersignId, String reason) {
        call(REJECT_METHOD, REJECT_PARAMETER, countersignId, reason);
    }

    private void call(String methodName, String parameterName, UUID countersignId, String reason) {
        Method method = null;
        for (Method each : contract.getMethods()) {
            if (each.getName().equals(methodName) && each.getParameterCount() == 1 && each.getParameterTypes()[0].isRecord()
                    && each.getParameterTypes()[0].getSimpleName().equals(parameterName)) {
                method = each;
            }
        }
        if (method == null) {
            throw new AssertionError(contract.getName() + " has no method " + methodName + "(" + parameterName + ")");
        }
        Class<?> record = method.getParameterTypes()[0];
        RecordComponent[] components = record.getRecordComponents();
        Object[] arguments = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            if (components[i].getName().equals(COUNTERSIGN_ID)) {
                arguments[i] = typed(types[i], countersignId);
            } else if (components[i].getName().equals(REASON)) {
                arguments[i] = reason;
            }
        }
        try {
            method.invoke(service, record.getDeclaredConstructor(types).newInstance(arguments));
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(failure.getCause());
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError("cannot call " + method, broken);
        }
    }

    private static Object typed(Class<?> type, UUID id) {
        if (type == UUID.class) {
            return id;
        }
        try {
            return type.getDeclaredConstructor(UUID.class).newInstance(id);
        } catch (ReflectiveOperationException notATypedId) {
            throw new AssertionError(COUNTERSIGN_ID + " must be a UUID or a typed id built from one, not " + type, notATypedId);
        }
    }
}
