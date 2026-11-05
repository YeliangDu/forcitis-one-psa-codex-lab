package com.forcitis.one.acceptance.organization.workforce;

import com.forcitis.one.kernel.EmployeeId;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * Reaches the read api {@code EmploymentRelationships} of the employment-relationship design (ADR-0086) (section 6.2) by name and reflection, so
 * that the acceptance tests compile before the implementation exists: a missing bean, method or component fails the test at run time, it is never
 * skipped. Only {@code on(EmployeeId, LocalDate)} and the view components {@code employerLegalEntityId} and {@code employerConfirmed} are used.
 */
final class EmploymentRelationshipReads {

    private static final String API = "com.forcitis.one.organization.workforce.api.EmploymentRelationships";

    private EmploymentRelationshipReads() {
    }

    /** The {@code employerConfirmed} of the person's relationship with the employer that is in service on the day. */
    static boolean employerConfirmed(ApplicationContext beans, EmployeeId person, LocalDate day, UUID employer) {
        Object api = null;
        Class<?> contract = null;
        for (String name : beans.getBeanDefinitionNames()) {
            Class<?> type = beans.getType(name);
            if (type == null) {
                continue;
            }
            for (Class<?> candidate : ClassUtils.getAllInterfacesForClassAsSet(ClassUtils.getUserClass(type))) {
                if (candidate.getName().equals(API)) {
                    api = beans.getBean(name);
                    contract = candidate;
                }
            }
        }
        if (api == null) {
            throw new AssertionError("design 6.2: no bean implements " + API);
        }
        try {
            Method on = contract.getMethod("on", EmployeeId.class, LocalDate.class);
            List<?> views = (List<?>) on.invoke(api, person, day);
            for (Object view : views) {
                if (employer.equals(uuidOf(accessor(view, "employerLegalEntityId")))) {
                    return (Boolean) accessor(view, "employerConfirmed");
                }
            }
            throw new AssertionError("design 6.2: the person has no relationship with the employer " + employer + " in service on " + day + ": " + views);
        } catch (InvocationTargetException failed) {
            throw new AssertionError("design 6.2: on(...) failed: " + failed.getCause(), failed.getCause());
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError("design 6.2: the read api does not have the ruled shape: " + broken, broken);
        }
    }

    private static Object accessor(Object view, String name) throws ReflectiveOperationException {
        return view.getClass().getMethod(name).invoke(view);
    }

    private static UUID uuidOf(Object id) throws ReflectiveOperationException {
        if (id instanceof UUID uuid) {
            return uuid;
        }
        Object value = id.getClass().getMethod("value").invoke(id);
        return (UUID) value;
    }
}
