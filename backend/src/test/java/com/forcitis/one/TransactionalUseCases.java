package com.forcitis.one;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a @Transactional application method reaches inside its transaction: its own calls and method references, and those of every application method it calls, transitively
 * (a helper class without @Transactional runs in the caller's transaction). Calls into other layers and into other modules' apis stop the walk: they are the access itself.
 */
final class TransactionalUseCases {
    static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";

    private TransactionalUseCases() {
    }

    /** One call or method reference: the class and method it targets, and the application method it resolves to when there is one. */
    record Access(JavaClass owner, String name, Optional<JavaMethod> member) {
    }

    static boolean isApplication(JavaClass type) {
        return type.getPackageName().matches(".*\\.application(\\..*)?");
    }

    /** The transactional entry methods: those annotated, or every method of an annotated class. */
    static List<JavaMethod> entries(JavaClass type) {
        if (!isApplication(type)) {
            return List.of();
        }
        boolean wholeClass = type.isAnnotatedWith(TRANSACTIONAL);
        return type.getMethods().stream().filter(m -> wholeClass || m.isAnnotatedWith(TRANSACTIONAL)).toList();
    }

    static List<Access> accessesOf(JavaCodeUnit unit) {
        List<Access> accesses = new ArrayList<>();
        unit.getMethodCallsFromSelf().forEach(call -> accesses.add(new Access(call.getTargetOwner(), call.getTarget().getName(), call.getTarget().resolveMember())));
        unit.getMethodReferencesFromSelf().forEach(ref -> accesses.add(new Access(ref.getTargetOwner(), ref.getTarget().getName(), ref.getTarget().resolveMember())));
        return accesses;
    }

    /** Every access made by the entry method or by an application method reachable from it. */
    static List<Access> reachableAccesses(JavaMethod entry) {
        Set<JavaCodeUnit> seen = new LinkedHashSet<>();
        List<Access> out = new ArrayList<>();
        List<JavaCodeUnit> todo = new ArrayList<>(List.of(entry));
        while (!todo.isEmpty()) {
            JavaCodeUnit unit = todo.removeLast();
            if (!seen.add(unit)) {
                continue;
            }
            for (Access access : accessesOf(unit)) {
                out.add(access);
                access.member().filter(m -> isApplication(m.getOwner())).ifPresent(todo::add);
            }
        }
        return out;
    }
}
