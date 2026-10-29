package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.docref.api.StringReference;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A business document is identified across modules by kernel.DocumentReference, never by a bare String or long. */
class DocumentReferenceGuardTest {
    static final Pattern DOCUMENT_NAME = Pattern.compile("(?i)(document|business)(id|key|ref|reference)");
    static final Set<String> PENDING_MODULES = Set.of();

    static List<String> violations(Iterable<Class<?>> classes, Set<String> pendingModules) {
        List<String> problems = new ArrayList<>();
        for (Class<?> type : classes) {
            String pkg = type.getPackageName();
            boolean contract = pkg.endsWith(".api") || pkg.endsWith(".events") || pkg.contains(".api.") || pkg.contains(".events.");
            if (!contract || !type.isRecord() || pendingModules.contains(EventCataloguePolicy.moduleOf(type))) {
                continue;
            }
            for (RecordComponent component : type.getRecordComponents()) {
                if (DOCUMENT_NAME.matcher(component.getName()).matches()
                        && (component.getType() == String.class || component.getType() == long.class || component.getType() == Long.class)) {
                    problems.add(type.getName() + "." + component.getName() + " is a " + component.getType().getSimpleName()
                            + ": use kernel.DocumentReference (type and id together)");
                }
            }
        }
        return problems;
    }

    @Test
    @DisplayName("no api or event record carries a document as a bare String or long")
    void contractsUseDocumentReference() {
        List<Class<?>> classes = new ArrayList<>();
        ArchitectureRulesTest.CLASSES.forEach(c -> classes.add(c.reflect()));
        assertThat(violations(classes, PENDING_MODULES)).as(GuardMessage.of("CS-2.7",
                "an api or events record carries a business document as a bare String or long",
                "replace the component with kernel.DocumentReference (document type and id together)")).isEmpty();
    }

    @Test
    @DisplayName("injected: a record component documentId of type String or businessKey of type long is caught, a DocumentReference is not")
    void aBareReferenceIsCaught() {
        List<String> problems = violations(List.of(StringReference.class, com.forcitis.one.fixture.docref.api.TypedReference.class), Set.of());

        assertThat(problems).hasSize(2);
        assertThat(problems).anyMatch(p -> p.contains("documentId") && p.contains("String"));
        assertThat(problems).anyMatch(p -> p.contains("businessKey") && p.contains("long"));
    }
}
