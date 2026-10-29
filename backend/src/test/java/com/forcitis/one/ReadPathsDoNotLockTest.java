package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * DDD-9 / review M-1: looking at something must never wait for the person who is changing it. A use case called Get*, List* or Check*
 * runs in a read-only transaction and reaches no repository method that locks (named *ForUpdate or lock*), however many components
 * it goes through, including a call through an interface, which is followed into every implementation.
 */
class ReadPathsDoNotLockTest {

    static final Pattern READ_USE_CASE = Pattern.compile("(Get|List|Check).*");
    static final Pattern LOCKING = Pattern.compile(".*ForUpdate.*|lock[A-Z].*");
    static final String ROOT = "com.forcitis.one";

    static List<String> violations(JavaClasses classes) {
        List<String> problems = new ArrayList<>();
        for (JavaClass type : classes) {
            if (!type.getPackageName().endsWith(".application") || !READ_USE_CASE.matcher(type.getSimpleName()).matches()
                    || type.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT)) {
                continue;
            }
            for (JavaMethod method : type.getMethods()) {
                Transactional transactional = method.tryGetAnnotationOfType(Transactional.class).orElse(null);
                if (transactional != null && !transactional.readOnly()) {
                    problems.add(type.getSimpleName() + "." + method.getName() + " is @Transactional but not readOnly");
                }
                problems.addAll(lockingCalls(type, method));
            }
        }
        return problems;
    }

    private static List<String> lockingCalls(JavaClass type, JavaMethod entry) {
        List<String> found = new ArrayList<>();
        Set<JavaMethod> seen = new HashSet<>();
        Deque<JavaMethod> todo = new ArrayDeque<>(List.of(entry));
        while (!todo.isEmpty()) {
            JavaMethod method = todo.pop();
            if (!seen.add(method)) {
                continue;
            }
            for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                if (LOCKING.matcher(call.getName()).matches()) {
                    found.add(type.getSimpleName() + "." + entry.getName() + " reaches the locking method " + call.getTargetOwner().getSimpleName()
                            + "." + call.getName() + " (through " + method.getOwner().getSimpleName() + "." + method.getName() + ")");
                }
                call.getTarget().resolveMember().ifPresent(target -> {
                    JavaClass owner = target.getOwner();
                    if (!owner.getPackageName().startsWith(ROOT)) {
                        return;
                    }
                    if (!owner.isInterface()) {
                        todo.push(target);
                        return;
                    }
                    for (JavaClass implementation : owner.getAllSubclasses()) {
                        if (!implementation.isInterface()) {
                            implementation.getAllMethods().stream().filter(m -> m.getName().equals(target.getName())
                                    && m.getRawParameterTypes().equals(target.getRawParameterTypes())).forEach(todo::push);
                        }
                    }
                });
            }
        }
        return found;
    }

    @Test
    @DisplayName("DDD-9 every Get*, List* and Check* use case of the code base is read-only and reaches no locking repository method")
    void readPathsDoNotLock() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ROOT);
        assertThat(classes.stream().filter(c -> c.getPackageName().endsWith(".application") && READ_USE_CASE.matcher(c.getSimpleName()).matches()).count())
                .as(GuardMessage.of("DDD-9", "the guard found no more than 5 Get*/List*/Check* use cases, so it is scanning the wrong classes",
                        "check READ_USE_CASE and ROOT in ReadPathsDoNotLockTest")).isGreaterThan(5);
        assertThat(violations(classes)).as(GuardMessage.of("DDD-9",
                "a Get*/List*/Check* use case is not read-only or reaches a repository method that locks (*ForUpdate or lock*)",
                "annotate it @Transactional(readOnly = true) and read through a method that does not lock; keep the locking method on the write path")).isEmpty();
    }

    @Test
    @DisplayName("DDD-9 injected: a read use case that is not read-only, or that reaches a locking call through a helper, is caught")
    void aLockingReadIsCaught() {
        JavaClasses fixture = new ClassFileImporter().withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("com.forcitis.one.fixture.locking");
        List<String> problems = violations(fixture);

        assertThat(problems).anyMatch(p -> p.contains("GetThing") && p.contains("not readOnly"));
        assertThat(problems).anyMatch(p -> p.contains("ListThings") && p.contains("findForUpdate"));
        assertThat(problems).anyMatch(p -> p.contains("ListThroughInterface") && p.contains("findForUpdate"));
        assertThat(problems).noneMatch(p -> p.contains("CheckThing"));
    }
}
