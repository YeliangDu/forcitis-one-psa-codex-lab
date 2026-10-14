package com.forcitis.one.organization.workforce.guard;

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
 * §9a ArchUnit guard: only organization.workforce.infrastructure.. may reach the A8 views connection (qualified "a8")
 * and the old-system connection (qualified "legacy"). Nothing else may build a connection pool to an external
 * system, name an SQL Server driver class, or inject those connections.
 */
class A8DataSourceIsolationTest {

    static final String ROOT = "com.forcitis.one";
    static final String WORKFORCE_INFRASTRUCTURE = ROOT + ".organization.workforce.infrastructure..";
    static final String EXTERNAL = ROOT + ".organization.workforce.infrastructure.external..";

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    @DisplayName("§9a only the two approved read-only ACLs may build external connections: workforce (A8, legacy short names) and approval (legacy inbox view)")
    void onlyWorkforceInfrastructureBuildsExternalConnections() {
        noClasses().that().resideOutsideOfPackages(WORKFORCE_INFRASTRUCTURE, "com.forcitis.one.platform.approval.infrastructure.legacy..")
                .should().dependOnClassesThat().resideInAnyPackage("com.zaxxer.hikari..", "com.microsoft.sqlserver..")
                .because("A8 is reached only through the workforce anti-corruption layer (§3.3); the old inbox view only through approval's (§3.6)")
                .check(CLASSES);
    }

    @Test
    @DisplayName("§9a inside the module only infrastructure.external uses the external connections; domain, application, web and persistence cannot")
    void onlyTheExternalPackageTouchesTheConnections() {
        noClasses().that().resideOutsideOfPackage(EXTERNAL)
                .should().dependOnClassesThat().resideInAPackage(EXTERNAL)
                .because("the A8 views connection is qualified 'a8', the old-system one 'legacy'; both live in infrastructure.external")
                .check(CLASSES);
        noClasses().that().resideInAnyPackage(ROOT + ".organization.workforce.domain..", ROOT + ".organization.workforce.application..",
                        ROOT + ".organization.workforce.web..")
                .should().dependOnClassesThat().resideInAnyPackage("com.zaxxer.hikari..", "java.sql..", "org.springframework.jdbc..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("§9a nobody outside organization.workforce.infrastructure.. injects a bean qualified 'a8' or 'legacy'")
    void qualifiersAreNotUsedElsewhere() {
        classes().that().resideOutsideOfPackage(WORKFORCE_INFRASTRUCTURE)
                .should(new ArchCondition<JavaClass>("not inject beans qualified 'a8' or 'legacy'") {
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
                                    && List.of("a8", "legacy").contains(String.valueOf(a.get("value").orElse("")))) {
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
        noClasses().that().resideInAPackage(WORKFORCE_INFRASTRUCTURE)
                .should().beAssignableTo(DataSource.class)
                .check(CLASSES);
        classes().that().resideInAPackage(EXTERNAL).and().haveSimpleName("ExternalSourcesConfiguration")
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
    @DisplayName("I-1 no code anywhere reads the old system with anything but the short-name statement")
    void legacyAccessIsOneStatement() {
        long legacySources = CLASSES.stream().filter(c -> c.getSimpleName().equals("LegacyShortNameSource")).count();
        assertThat(legacySources).isEqualTo(1);
        noClasses().that().resideOutsideOfPackage(EXTERNAL)
                .should().dependOnClassesThat().haveSimpleName("LegacyShortNameSource")
                .check(CLASSES);
    }
}
