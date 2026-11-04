package com.forcitis.one.platform.integration.guard;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.GuardMessage;
import com.forcitis.one.platform.integration.domain.ExternalConnections;
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

/** Only integration.infrastructure.seeyon connects to Seeyon; the old database is read only by integration's legacy adapters (ADR-0039 item 1, R-1b P3b); only the connection factory builds pools or drivers, nothing else an HTTP client. */
class ExternalConnectionIsolationTest {

    static final String ROOT = "com.forcitis.one";
    static final String SEEYON = ROOT + ".platform.integration.infrastructure.seeyon..";
    static final String CONNECTION = ROOT + ".platform.integration.infrastructure.connection..";
    static final String WECOM = ROOT + ".platform.integration.infrastructure.wecom..";
    static final String INTEGRATION_LEGACY = ROOT + ".platform.integration.infrastructure.legacy..";

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    @DisplayName("§9a only the connection factory of integration builds pools and touches the JDBC driver; the three read-only adapters (Seeyon, old short-name view, old to-do view) get theirs from it")
    void onlyTheConnectionFactoryBuildsExternalConnections() {
        noClasses().that().resideOutsideOfPackage(CONNECTION)
                .should().dependOnClassesThat(resideInAnyPackage("com.zaxxer.hikari..", "org.springframework.jdbc.datasource..", "org.postgresql..", "com.microsoft.sqlserver..")
                        .or(equivalentTo(java.sql.Driver.class)).or(equivalentTo(java.sql.DriverManager.class)))
                .because(GuardMessage.of("D1-1 / CS-1.3", "a class outside platform.integration.infrastructure.connection uses a JDBC driver, pool or datasource builder", "get the connection from ExternalConnections and build pools only in platform.integration.infrastructure.connection, which sets the query, socket and lock-wait timeouts"))
                .check(CLASSES);
    }

    @Test
    @DisplayName("D1-1-R1 onlyTheApprovedAdaptersObtainExternalConnections: only the Seeyon and old-system adapter packages of platform.integration (and the factory itself) use platform.integration.domain.ExternalConnections")
    void onlyTheApprovedAdaptersObtainExternalConnections() {
        noClasses().that().resideOutsideOfPackages(SEEYON, INTEGRATION_LEGACY, CONNECTION)
                .should().dependOnClassesThat(equivalentTo(ExternalConnections.class))
                .because(GuardMessage.of("D1-1-R1 / CS-1.3", "a class outside the Seeyon, legacy and connection packages uses platform.integration.domain.ExternalConnections", "move the external read into an adapter in platform.integration.infrastructure.seeyon or .legacy and expose it through a platform.integration api port"))
                .check(CLASSES);
    }

    @Test
    @DisplayName("§9a the Seeyon connection and the Seeyon portal are reached from integration.infrastructure.seeyon only")
    void seeyonIsOnlyReachedFromTheSeeyonPackage() {
        noClasses().that().resideOutsideOfPackage(SEEYON)
                .should().dependOnClassesThat().resideInAPackage(SEEYON)
                .because(GuardMessage.of("CS-1.3", "a class outside platform.integration.infrastructure.seeyon depends on a class in it", "call Seeyon through the platform.integration api ports (OrgSource) instead of the adapter class"))
                .check(CLASSES);
        noClasses().that().resideOutsideOfPackages(SEEYON, WECOM)
                .should().dependOnClassesThat().resideInAPackage("java.net.http..")
                .because(GuardMessage.of("CS-1.3", "a class outside the Seeyon and WeCom adapters uses java.net.http", "put the HTTP call in an adapter under platform.integration.infrastructure.seeyon or .wecom"))
                .check(CLASSES);
        noClasses().that().resideOutsideOfPackage(INTEGRATION_LEGACY)
                .should().dependOnClassesThat().resideInAPackage(INTEGRATION_LEGACY)
                .because(GuardMessage.of("CS-1.3", "a class outside platform.integration.infrastructure.legacy depends on a class in it", "use the old-system data through the platform.integration api ports, not the legacy adapter classes"))
                .check(CLASSES);
        noClasses().that().resideInAnyPackage(SEEYON).should().dependOnClassesThat().resideInAnyPackage(INTEGRATION_LEGACY)
                .because(GuardMessage.of("CS-1.3", "the Seeyon adapter depends on the old-system (legacy) adapter", "remove the dependency; each adapter reads only its own external system"))
                .check(CLASSES);
        noClasses().that().resideInAnyPackage(INTEGRATION_LEGACY).should().dependOnClassesThat().resideInAPackage(SEEYON)
                .because(GuardMessage.of("CS-1.3", "an old-system (legacy) adapter depends on the Seeyon adapter", "remove the dependency; each adapter reads only its own external system"))
                .check(CLASSES);
    }

    @Test
    @DisplayName("§9a domain, application, web and persistence of every module cannot touch JDBC drivers or pools through an external connection")
    void businessLayersDoNotHoldExternalConnections() {
        noClasses().that().resideInAnyPackage("..domain..", "..application..", "..web..")
                .should().dependOnClassesThat().resideInAnyPackage("com.zaxxer.hikari..", "java.sql..", "org.springframework.jdbc..")
                .because(GuardMessage.of("D1-1 / CS-1.3", "a domain, application or web class depends on JDBC drivers or pools", "read external data through a platform.integration api port; SQL belongs in infrastructure.persistence"))
                .check(CLASSES);
    }

    @Test
    @DisplayName("§9a nobody outside the approved adapters injects a bean qualified 'seeyon' or 'legacy'")
    void qualifiersAreNotUsedElsewhere() {
        classes().that().resideOutsideOfPackages(SEEYON, INTEGRATION_LEGACY)
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
                .because(GuardMessage.of("CS-1.3", "a class outside the Seeyon and legacy adapters injects a bean qualified seeyon, legacy or legacy-approval", "remove the @Qualifier and read through a platform.integration api port"))
                .check(CLASSES);
    }

    @Test
    @DisplayName("the external connections are not DataSource beans: a second DataSource would switch off the application's own PostgreSQL DataSource")
    void connectionsAreNotDataSourceBeans() {
        Optional<JavaClass> connection = CLASSES.stream().filter(c -> c.getSimpleName().equals("ExternalConnectionFactory")).findFirst();

        assertThat(connection).as(GuardMessage.of("D1-1", "the class ExternalConnectionFactory is not found", "keep the factory class named ExternalConnectionFactory under platform.integration.infrastructure.connection")).isPresent();
        assertThat(connection.get().isAssignableTo(DataSource.class)).as(GuardMessage.of("D1-1", "ExternalConnectionFactory is a DataSource, so a second DataSource would switch off the application's own PostgreSQL DataSource", "keep it a plain factory that returns connections and does not implement DataSource")).isFalse();
        noClasses().that().resideInAnyPackage(SEEYON, INTEGRATION_LEGACY, CONNECTION)
                .should().beAssignableTo(DataSource.class)
                .because(GuardMessage.of("D1-1", "a class of the external adapter packages is a DataSource, which would switch off the application's own PostgreSQL DataSource", "return plain connections from ExternalConnections and do not implement DataSource"))
                .check(CLASSES);
        classes().that().resideInAnyPackage(SEEYON, INTEGRATION_LEGACY, CONNECTION).and().haveSimpleNameEndingWith("Configuration")
                .should(new ArchCondition<JavaClass>("declare no @Bean method returning a DataSource") {
                    @Override
                    public void check(JavaClass c, ConditionEvents events) {
                        c.getMethods().stream().filter(m -> m.getRawReturnType().isAssignableTo(DataSource.class))
                                .forEach(m -> events.add(SimpleConditionEvent.violated(c, m.getFullName() + " returns a DataSource")));
                    }
                })
                .because(GuardMessage.of("D1-1", "an adapter configuration declares a @Bean returning a DataSource, which would switch off the application's own PostgreSQL DataSource", "remove the @Bean method and hand out connections through ExternalConnections"))
                .check(CLASSES);
    }

    @Test
    @DisplayName("I-1 the old system is read with nothing but the two views: one reader class per view, each used only by its own adapter package")
    void legacyAccessIsTwoViews() {
        long shortNameReaders = CLASSES.stream().filter(c -> c.getSimpleName().equals("LegacyOrgNames")).count();
        assertThat(shortNameReaders).as(GuardMessage.of("I-1", "the old system must have exactly one LegacyOrgNames reader class", "keep one reader class per old-system view and do not add a second reader of the short-name view")).isEqualTo(1);
        noClasses().that().resideOutsideOfPackage(INTEGRATION_LEGACY)
                .should().dependOnClassesThat().haveSimpleName("LegacyOrgNames")
                .because(GuardMessage.of("I-1", "a class outside platform.integration.infrastructure.legacy depends on LegacyOrgNames", "use the platform.integration api port for the old-system names, not the reader class"))
                .check(CLASSES);
    }
}
