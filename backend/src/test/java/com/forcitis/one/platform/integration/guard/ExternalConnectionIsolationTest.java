package com.forcitis.one.platform.integration.guard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §9a ArchUnit guard for the external connections:
 * <ul>
 *   <li>only {@code platform.integration.infrastructure.seeyon..} may connect to the Seeyon database or to the Seeyon portal;</li>
 *   <li>the old system's database may be read only by two approved read-only adapters: approval's old to-do view
 *       ({@code platform.approval.infrastructure.legacy..}) and the short-name view, which is read from
 *       {@code platform.integration.infrastructure.legacy..} (judgement: it is the same kind of anti-corruption read as the
 *       Seeyon one, so it lives in the module that owns external reads and workforce keeps no connection at all).</li>
 * </ul>
 * Nothing else may build a connection pool, name an SQL Server driver class, open an HTTP client or inject those connections.
 */
class ExternalConnectionIsolationTest {

    static final String ROOT = "com.forcitis.one";
    static final String SEEYON = ROOT + ".platform.integration.infrastructure.seeyon..";
    static final String INTEGRATION_LEGACY = ROOT + ".platform.integration.infrastructure.legacy..";
    static final String APPROVAL_LEGACY = ROOT + ".platform.approval.infrastructure.legacy..";

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    @DisplayName("§9a only three read-only adapters may build external connections: integration.seeyon (Seeyon), integration.legacy (old short-name view), approval.legacy (old to-do view)")
    void onlyTheApprovedAdaptersBuildExternalConnections() {
        noClasses().that().resideOutsideOfPackages(SEEYON, INTEGRATION_LEGACY, APPROVAL_LEGACY)
                .should().dependOnClassesThat().resideInAnyPackage("com.zaxxer.hikari..", "com.microsoft.sqlserver..")
                .because("the Seeyon database is reached only through platform.integration.infrastructure.seeyon (§3.3); the old system only "
                        + "through the short-name view (integration.legacy) and the to-do view (approval.legacy, §3.6)")
                .check(CLASSES);
    }

    @Test
    @DisplayName("§9a the Seeyon connection and the Seeyon portal are reached from integration.infrastructure.seeyon only")
    void seeyonIsOnlyReachedFromTheSeeyonPackage() {
        noClasses().that().resideOutsideOfPackage(SEEYON)
                .should().dependOnClassesThat().resideInAPackage(SEEYON)
                .because("the Seeyon ACL is used through the platform.integration api ports (OrgSource, SsoTicketVerifier), never directly")
                .check(CLASSES);
        noClasses().that().resideOutsideOfPackage(SEEYON)
                .should().dependOnClassesThat().resideInAPackage("java.net.http..")
                .because("the portal ticket check is the only HTTP client of the application's own code")
                .check(CLASSES);
        noClasses().that().resideOutsideOfPackage(INTEGRATION_LEGACY)
                .should().dependOnClassesThat().resideInAPackage(INTEGRATION_LEGACY)
                .check(CLASSES);
        noClasses().that().resideInAnyPackage(SEEYON).should().dependOnClassesThat().resideInAnyPackage(INTEGRATION_LEGACY, APPROVAL_LEGACY)
                .because("the Seeyon adapter never touches the old system")
                .check(CLASSES);
        noClasses().that().resideInAnyPackage(INTEGRATION_LEGACY, APPROVAL_LEGACY).should().dependOnClassesThat().resideInAPackage(SEEYON)
                .because("the old-system adapters never touch Seeyon")
                .check(CLASSES);
    }

    @Test
    @DisplayName("§9a domain, application, web and persistence of every module cannot touch JDBC drivers or pools through an external connection")
    void businessLayersDoNotHoldExternalConnections() {
        noClasses().that().resideInAnyPackage("..domain..", "..application..", "..web..")
                .should().dependOnClassesThat().resideInAnyPackage("com.zaxxer.hikari..", "java.sql..", "org.springframework.jdbc..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("§9a nobody outside the approved adapters injects a bean qualified 'seeyon' or 'legacy'")
    void qualifiersAreNotUsedElsewhere() {
        classes().that().resideOutsideOfPackages(SEEYON, INTEGRATION_LEGACY, APPROVAL_LEGACY)
                .should(new ArchCondition<JavaClass>("not inject beans qualified 'seeyon', 'legacy' or 'legacy-approval'") {
                    @Override
                    public void check(JavaClass c, ConditionEvents events) {
                        for (JavaCodeUnit unit : c.getCodeUnits()) {
                            unit.getParameters().forEach(p -> flag(c, p.getAnnotations(), events));
                            flag(c, unit.getAnnotations(), events);
                        }
                        c.getFields().forEach(f -> flag(c, f.getAnnotations(), events));
                    }

                    private void flag(JavaClass owner, Iterable<? extends JavaAnnotation<?>> annotations, ConditionEvents events) {
                        for (JavaAnnotation<?> a : annotations) {
                            if (a.getRawType().getName().equals("org.springframework.beans.factory.annotation.Qualifier")
                                    && List.of("seeyon", "legacy", "legacy-approval").contains(String.valueOf(a.get("value").orElse("")))) {
                                events.add(SimpleConditionEvent.violated(owner, owner.getName() + " uses @Qualifier(" + a.get("value").orElse("") + ")"));
                            }
                        }
                    }
                })
                .check(CLASSES);
    }

    @Test
    @DisplayName("the external connections are not DataSource beans: a second DataSource would switch off the application's own PostgreSQL DataSource")
    void connectionsAreNotDataSourceBeans() {
        Optional<JavaClass> connection = CLASSES.stream().filter(c -> c.getSimpleName().equals("ReadOnlyConnection")).findFirst();

        assertThat(connection).isPresent();
        assertThat(connection.get().isAssignableTo(DataSource.class)).isFalse();
        noClasses().that().resideInAnyPackage(SEEYON, INTEGRATION_LEGACY)
                .should().beAssignableTo(DataSource.class)
                .check(CLASSES);
        classes().that().resideInAnyPackage(SEEYON, INTEGRATION_LEGACY).and().haveSimpleNameEndingWith("Configuration")
                .should(new ArchCondition<JavaClass>("declare no @Bean method returning a DataSource") {
                    @Override
                    public void check(JavaClass c, ConditionEvents events) {
                        c.getMethods().stream().filter(m -> m.getRawReturnType().isAssignableTo(DataSource.class))
                                .forEach(m -> events.add(SimpleConditionEvent.violated(c, m.getFullName() + " returns a DataSource")));
                    }
                })
                .check(CLASSES);
    }

    @Test
    @DisplayName("I-1 the old system is read with nothing but the two views: one reader class per view, each used only by its own adapter package")
    void legacyAccessIsTwoViews() {
        long shortNameReaders = CLASSES.stream().filter(c -> c.getSimpleName().equals("LegacyOrgNames")).count();
        long todoReaders = CLASSES.stream().filter(c -> c.getSimpleName().equals("JdbcLegacyInbox")).count();
        assertThat(shortNameReaders).isEqualTo(1);
        assertThat(todoReaders).isEqualTo(1);
        noClasses().that().resideOutsideOfPackage(INTEGRATION_LEGACY)
                .should().dependOnClassesThat().haveSimpleName("LegacyOrgNames")
                .check(CLASSES);
        noClasses().that().resideOutsideOfPackage(APPROVAL_LEGACY)
                .should().dependOnClassesThat().haveSimpleName("JdbcLegacyInbox")
                .check(CLASSES);
    }
}
