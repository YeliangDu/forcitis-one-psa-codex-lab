package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * R-1b P3b, ADR-0039 item 1, item 5.2 and its guard rule on ExternalConnectionIsolationTest (card section 3.2 items 5 and 8, RULING-P3B-D2):
 * the old to-do reader, its configuration, the old-system to-do and the merge of a row's tasks move into platform.integration; only the
 * adapters of platform.integration obtain an external connection; approval keeps no type that carries an old-system id. Judged on the
 * compiled main classes.
 */
class LegacyAdapterPlacementTest {

    static final String ROOT = "com.forcitis.one";
    static final String INTEGRATION = ROOT + ".platform.integration";
    static final String APPROVAL = ROOT + ".platform.approval";
    static final String APPROVAL_LEGACY = APPROVAL + ".infrastructure.legacy";
    /** The port through which an adapter obtains an external connection; since R-1b P3b in platform.integration.domain (RULING-P3B-12). */
    static final String EXTERNAL_CONNECTIONS = INTEGRATION + ".domain.ExternalConnections";
    /** The types that move into platform.integration under their present names (RULING-P3B-D2). */
    static final List<String> MOVED_TYPES = List.of("LegacyInstanceId");

    static final JavaClasses MAIN = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ROOT);

    private static String topLevel(JavaClass type) {
        return type.getName().replaceAll("\\$.*", "");
    }

    @Test
    @DisplayName("R-1b P3b ADR-0039 守护：approval.infrastructure.legacy 包不再存在，老待办读取器、配置、老待办条目与 LegacyInstanceId 都在 platform.integration")
    void theOldToDoReaderLivesInIntegration() {
        TreeSet<String> left = new TreeSet<>();
        TreeSet<String> moved = new TreeSet<>();
        for (JavaClass type : MAIN) {
            if (type.getPackageName().startsWith(APPROVAL_LEGACY)) {
                left.add(topLevel(type));
            }
            if (type.getPackageName().startsWith(APPROVAL) && MOVED_TYPES.contains(type.getSimpleName())) {
                left.add(type.getName());
            }
            if (type.getPackageName().startsWith(INTEGRATION) && MOVED_TYPES.contains(type.getSimpleName())) {
                moved.add(type.getSimpleName());
            }
        }

        assertThat(left).as("old-system to-do types still in platform.approval").isEmpty();
    }

    /** The classes outside platform.integration that depend on the external connection port. */
    static TreeSet<String> outsideUsersOfExternalConnections(JavaClasses classes) {
        TreeSet<String> outside = new TreeSet<>();
        for (JavaClass type : classes) {
            if (!type.getPackageName().startsWith(INTEGRATION) && type.getDirectDependenciesFromSelf().stream()
                    .anyMatch(dependency -> dependency.getTargetClass().getName().equals(EXTERNAL_CONNECTIONS))) {
                outside.add(topLevel(type));
            }
        }
        return outside;
    }

    @Test
    @DisplayName("R-1b P3b ADR-0039 守护 / RULING-P3B-12：只有 platform.integration 的适配器取得外部连接（ExternalConnections），审批模块不再在准入之列")
    void onlyIntegrationObtainsExternalConnections() {
        assertThat(MAIN.contain(EXTERNAL_CONNECTIONS)).as("the port %s exists, so the check below is not run against nothing", EXTERNAL_CONNECTIONS).isTrue();
        assertThat(MAIN.stream().filter(type -> type.getPackageName().startsWith(INTEGRATION)).map(LegacyAdapterPlacementTest::topLevel)
                .filter(name -> !name.equals(EXTERNAL_CONNECTIONS)).anyMatch(name -> MAIN.get(name).getDirectDependenciesFromSelf().stream()
                        .anyMatch(dependency -> dependency.getTargetClass().getName().equals(EXTERNAL_CONNECTIONS))))
                .as("the adapters of platform.integration use the port (control)").isTrue();

        assertThat(outsideUsersOfExternalConnections(MAIN)).as("classes outside platform.integration that obtain an external connection").isEmpty();
    }

    /** Lies outside platform.integration and holds the port: what the check must report (CS-5.9). */
    static final class OutsideHolder {
        com.forcitis.one.platform.integration.domain.ExternalConnections connections;
    }

    @Test
    @DisplayName("R-1b P3b RULING-P3B-12 注入：platform.integration 以外持有 ExternalConnections 的类被报出")
    void anOutsideUserOfTheConnectionPortIsReported() {
        JavaClasses sample = new ClassFileImporter().importClasses(OutsideHolder.class);

        assertThat(outsideUsersOfExternalConnections(sample)).containsExactly(OutsideHolder.class.getName().replaceAll("\\$.*", ""));
    }

    @Test
    @DisplayName("R-1b P3b ADR-0039 第 1 条：审批模块不依赖集成层的基础设施，只经 platform.integration.api 收到已翻译、不含 Legacy*Id 的条目")
    void approvalUsesOnlyTheIntegrationApi() {
        TreeSet<String> reaching = new TreeSet<>();
        for (JavaClass type : MAIN) {
            if (type.getPackageName().startsWith(APPROVAL)) {
                type.getDirectDependenciesFromSelf().stream().map(dependency -> dependency.getTargetClass())
                        .filter(target -> target.getPackageName().startsWith(INTEGRATION) && !target.getPackageName().startsWith(INTEGRATION + ".api")
                                || target.getSimpleName().matches("Legacy\\w*Id"))
                        .forEach(target -> reaching.add(topLevel(type) + " -> " + target.getName()));
            }
        }

        assertThat(reaching).as("approval classes that reach past the integration api or hold an old-system id").isEmpty();
    }
}
