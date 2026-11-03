package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.CountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.API_PACKAGE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REASON;

import com.forcitis.one.kernel.EmployeeId;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * Reaches the countersign use cases (approve and reject one request row as the signed-in caller). Like {@link PersonnelCommandsProbe}, it
 * finds a bean named {@code AttributionCountersigns} in the workforce api package at run time; a missing bean or method fails the test, it
 * is never skipped. It does not touch the security context: the caller sets it (see {@code CountersignSupport#callAs}).
 */
final class CountersignProbe {

    private final PersonnelCommandsProbe base;
    private final Object service;
    private final Method approve;
    private final Method reject;

    private CountersignProbe(PersonnelCommandsProbe base, Object service, Method approve, Method reject) {
        this.base = base;
        this.service = service;
        this.approve = approve;
        this.reject = reject;
    }

    static CountersignProbe of(ApplicationContext context, Function<UUID, EmployeeId> employeeIds) {
        PersonnelCommandsProbe base = PersonnelCommandsProbe.of(context, employeeIds);
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
                    return new CountersignProbe(base, context.getBean(name), method(candidate, APPROVE_METHOD, APPROVE_PARAMETER),
                            method(candidate, REJECT_METHOD, REJECT_PARAMETER));
                }
            }
        }
        throw new AssertionError("ADR-0073 item 3.6: no bean of a type named " + API_TYPE + " in " + API_PACKAGE + " (same-named types elsewhere: " + seen + ")");
    }

    private static Method method(Class<?> contract, String name, String parameter) {
        for (Method method : contract.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isRecord()
                    && method.getParameterTypes()[0].getSimpleName().equals(parameter)) {
                return method;
            }
        }
        throw new AssertionError(contract.getName() + " has no method " + name + "(" + parameter + ") with one record parameter");
    }

    /** Approves one request as the signed-in caller; the reason may be null. Refusals propagate. */
    void approve(UUID countersignId, String reason) {
        base.call(service, approve, fields(countersignId, reason));
    }

    /** Rejects one request as the signed-in caller; a reason is required. Refusals propagate. */
    void reject(UUID countersignId, String reason) {
        base.call(service, reject, fields(countersignId, reason));
    }

    List<String> approveComponents() {
        return names(approve);
    }

    List<String> rejectComponents() {
        return names(reject);
    }

    private static List<String> names(Method method) {
        List<String> names = new ArrayList<>();
        for (RecordComponent component : method.getParameterTypes()[0].getRecordComponents()) {
            names.add(component.getName());
        }
        return names;
    }

    private static Map<String, Object> fields(UUID countersignId, String reason) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(COUNTERSIGN_ID, countersignId);
        if (reason != null) {
            fields.put(REASON, reason);
        }
        return fields;
    }
}
