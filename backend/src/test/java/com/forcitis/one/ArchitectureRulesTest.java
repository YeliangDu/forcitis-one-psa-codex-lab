package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {
    static final String ROOT = "com.forcitis.one";
    static final int MAX_API_METHODS_PER_MODULE = 12;
    // 登记例外：模块 -> {上限, 出处}；全局上限不抬，新增例外须有出处并经 one-architecture 裁定
    static final Map<String, ApiLimitException> API_LIMIT_EXCEPTIONS = Map.of(
            ROOT + ".organization.workforce", new ApiLimitException(13,
                    "NITS-1 项⑤；one-coordinator 同意；one-architecture 裁定 2026-10-17"),
            ROOT + ".platform.integration", new ApiLimitException(17,
                    "ADR-0060 检测与业务文件桶端口；one-architecture 裁定 2026-10-21；到期：ADR-0069 D2 合入"));

    record ApiLimitException(int max, String source) {}

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void apiPackagesContainOnlyInterfacesAndRecords() {
        classes().that().resideInAPackage("..api..").and().doNotHaveSimpleName("package-info")
                .should().beInterfaces().orShould().beRecords().orShould().beEnums()
                .because("a module's api is a narrow contract: interfaces, immutable records and typed enum catalogues")
                .check(CLASSES);
    }

    @Test
    void apiSignaturesDoNotLeakModuleInternals() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage("..domain..", "..application..", "..infrastructure..", "..web..")
                .because("api types may only mention api records, kernel types and JDK types; internals stay hidden")
                .check(CLASSES);
    }

    @Test
    void eventsAreRecords() {
        classes().that().resideInAPackage("..events..").and().doNotHaveSimpleName("package-info")
                .should().beRecords()
                .andShould().haveSimpleNameNotEndingWith("Event")
                .because("events are immutable facts named in the past tense, e.g. TimesheetApproved")
                .check(CLASSES);
    }

    @Test
    void apiSurfaceStaysNarrow() {
        Map<String, Long> perModule = CLASSES.stream()
                .filter(c -> c.getPackageName().matches(".*\\.api(\\..*)?$") && c.isInterface())
                .collect(Collectors.groupingBy(c -> c.getPackageName().replaceFirst("\\.api(\\..*)?$", ""),
                        Collectors.summingLong(c -> c.getMethods().size())));
        assertApiSurfaceNarrow(perModule);
    }

    @Test
    void apiSurfaceExceptionModuleAllowsExactlyItsLimit() {
        String module = ROOT + ".organization.workforce";
        assertApiSurfaceNarrow(Map.of(module, 13L));
        assertThatThrownBy(() -> assertApiSurfaceNarrow(Map.of(module, 14L)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Deepen the module instead of widening its interface.");
    }

    @Test
    void apiSurfaceNonExceptionModuleFailsAboveGlobalLimit() {
        assertApiSurfaceNarrow(Map.of(ROOT + ".billing.invoicing", 12L));
        assertThatThrownBy(() -> assertApiSurfaceNarrow(Map.of(ROOT + ".billing.invoicing", 13L)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Deepen the module instead of widening its interface.");
    }

    static void assertApiSurfaceNarrow(Map<String, Long> perModule) {
        perModule.forEach((module, count) -> {
            int max = API_LIMIT_EXCEPTIONS.containsKey(module)
                    ? API_LIMIT_EXCEPTIONS.get(module).max()
                    : MAX_API_METHODS_PER_MODULE;
            if (count > max) {
                throw new AssertionError(module + " exposes " + count + " api methods (max " + max
                        + "). Deepen the module instead of widening its interface.");
            }
        });
    }

    @Test
    void webAndInfrastructureAreNotPublic() {
        classes().that().resideInAnyPackage("..web..", "..infrastructure..")
                .and().doNotHaveFullyQualifiedName(ROOT + ".platform.web.NotFromQuery")
                .should().notBePublic()
                .because("controllers and adapters are wired by Spring; nothing else should reference them")
                .check(CLASSES);
    }

    @Test
    void domainIsFrameworkFree() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.servlet..", "..application..", "..infrastructure..", "..web..")
                .because("domain = pure business rules; it must be unit-testable without Spring or a database")
                .check(CLASSES);
    }

    @Test
    void applicationDoesNotTouchWebOrInfrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..web..", "..infrastructure..")
                .because("use cases talk to domain ports, never to controllers or JDBC adapters")
                .check(CLASSES);
    }

    @Test
    void webOnlyCallsApplication() {
        noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..domain..")
                .because("controllers delegate to one use case; they never load aggregates or run SQL")
                .check(CLASSES);
    }

    @Test
    void jdbcOnlyInInfrastructure() {
        noClasses().that().resideOutsideOfPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
                .because("SQL lives in infrastructure.persistence and only touches the module's own schema")
                .check(CLASSES);
    }

    @Test
    void transactionsOnlyInApplication() {
        methods().that().areAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .should().beDeclaredInClassesThat().resideInAPackage("..application..")
                .because("one use case = one transaction boundary")
                .check(CLASSES);
        noClasses().that().resideOutsideOfPackage("..application..")
                .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.transaction.support.TransactionTemplate")
                .check(CLASSES);
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("jakarta.transaction.Transactional")
                .because("use Spring's @Transactional on the use case only")
                .check(CLASSES);
    }

    @Test
    void controllersLiveInWebAndEndWithController() {
        classes().that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().resideInAPackage("..web..").andShould().haveSimpleNameEndingWith("Controller")
                .check(CLASSES);
    }

    @Test
    void repositoriesFollowPortAdapterNaming() {
        classes().that().resideInAPackage("..domain..").and().haveSimpleNameEndingWith("Repository")
                .should().beInterfaces()
                .because("the domain declares the port; infrastructure provides Jdbc<Name>Repository")
                .check(CLASSES);
        classes().that().resideInAPackage("..infrastructure.persistence..").and().areAnnotatedWith("org.springframework.stereotype.Repository")
                .should().haveSimpleNameStartingWith("Jdbc")
                .check(CLASSES);
    }

    @Test
    void noVagueOrLegacyNames() {
        noClasses().should().haveSimpleNameEndingWith("Util").orShould().haveSimpleNameEndingWith("Utils")
                .orShould().haveSimpleNameEndingWith("Helper").orShould().haveSimpleNameEndingWith("Manager")
                .orShould().haveSimpleNameEndingWith("Info").orShould().haveSimpleNameEndingWith("Data")
                .orShould().haveSimpleNameEndingWith("Impl").orShould().haveSimpleNameEndingWith("DO")
                .orShould().haveSimpleNameContaining("ManHour").orShould().haveSimpleNameContaining("Manhour")
                .because("use the glossary name (03-通用语言): Timesheet/TimeEntry, Quote, RateCardItem...")
                .check(CLASSES);
    }

    @Test
    void identifiersAreAscii() {
        ArchCondition<JavaClass> asciiOnly = new ArchCondition<>("use ASCII identifiers only") {
            @Override
            public void check(JavaClass c, ConditionEvents events) {
                c.getAllMembers().forEach(m -> {
                    if (!m.getName().matches("[\\x00-\\x7F]*")) {
                        events.add(SimpleConditionEvent.violated(c, c.getName() + "." + m.getName() + " is not ASCII"));
                    }
                });
                if (!c.getSimpleName().matches("[\\x00-\\x7F]*")) {
                    events.add(SimpleConditionEvent.violated(c, c.getName() + " is not ASCII"));
                }
            }
        };
        classes().should(asciiOnly).because("no Chinese or pinyin identifiers; Chinese belongs in UI text and comments")
                .check(CLASSES);
    }

    @Test
    void statusesAreEnums() {
        fields().that().haveNameMatching("(?i).*status").and().areDeclaredInClassesThat().resideInAPackage("..domain..")
                .should().haveRawType(new DescribedPredicate<JavaClass>("an enum") {
                    @Override
                    public boolean test(JavaClass t) {
                        return t.isEnum();
                    }
                })
                .because("statuses are enums stored by name; no magic ints or dictionary ids")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void moneyIsNeverABareDecimal() {
        noFields().that().haveNameMatching("(?i).*(amount|price|cost|total|balance|fee|wage|tax)")
                .and().areDeclaredInClassesThat().resideInAnyPackage("..domain..", "..api..", "..events..")
                .should().haveRawType(java.math.BigDecimal.class).orShould().haveRawType(double.class)
                .orShould().haveRawType(Double.class).orShould().haveRawType(long.class)
                .because("ADR-0001: Money = (amount, currency); use kernel.Money")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void generalCodingRules() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(CLASSES);
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(CLASSES);
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(CLASSES);
        noClasses().that().doNotHaveFullyQualifiedName(ROOT + ".platform.approval.infrastructure.flowable.FlowableEngineClock")
                .and().doNotHaveFullyQualifiedName(ROOT + ".platform.integration.infrastructure.filestore.OssBusinessFileStore")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
                .because("use java.time; the exceptions implement Flowable's Clock (FLW-1) and set the OSS presign expiry, both java.util.Date only")
                .check(CLASSES);
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("jakarta.annotation.security.PermitAll")
                .because("anonymous endpoints only via the identity module's SSO allowlist")
                .check(CLASSES);
    }

    @Test
    void flowableOnlyInsideApprovalInfrastructure() {
        noClasses().that().resideOutsideOfPackage(ROOT + ".platform.approval.infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("org.flowable..")
                .because("Flowable is an implementation detail of the approval module; others use approval::api")
                .check(CLASSES);
    }

    @Test
    void controllersAreVersioned() {
        classes().that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should(new ArchCondition<>("map to /api/v1/<module>/...") {
                    @Override
                    public void check(JavaClass c, ConditionEvents events) {
                        boolean ok = c.tryGetAnnotationOfType(org.springframework.web.bind.annotation.RequestMapping.class)
                                .map(a -> a.value().length > 0 && a.value()[0].startsWith("/api/v1/"))
                                .orElse(false);
                        if (!ok) {
                            events.add(SimpleConditionEvent.violated(c, c.getName() + " must declare @RequestMapping(\"/api/v1/<module>/...\")"));
                        }
                    }
                })
                .because("every endpoint path starts with /api/v1/; a breaking change gets its own /api/v2/ controller")
                .check(CLASSES);
    }

    @Test
    void valueObjectsAreRecords() {
        classes().that().areAnnotatedWith(com.forcitis.one.kernel.ValueObject.class)
                .should().beRecords()
                .because("value objects are immutable and validate themselves in the compact constructor")
                .check(CLASSES);
    }

    @Test
    void valueObjectsHoldNoMutableState() {
        noFields().that().areDeclaredInClassesThat().areAnnotatedWith(com.forcitis.one.kernel.ValueObject.class)
                .should().haveRawType(java.util.Date.class)
                .orShould().haveRawType(java.util.ArrayList.class).orShould().haveRawType(java.util.HashMap.class)
                .orShould().haveRawType(new DescribedPredicate<JavaClass>("an array") {
                    @Override
                    public boolean test(JavaClass t) {
                        return t.isArray();
                    }
                })
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void domainIdsAreTyped() {
        fields().that().areDeclaredInClassesThat().resideInAPackage("..domain..")
                .and().haveNameMatching(".*Id")
                .should().haveRawType(new DescribedPredicate<JavaClass>("a typed id record (e.g. TimesheetId)") {
                    @Override
                    public boolean test(JavaClass t) {
                        return t.isRecord() && t.getSimpleName().endsWith("Id");
                    }
                })
                .because("typed ids stop an agent passing a project id where a timesheet id is expected")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void hoursAndQuantitiesUseValueObjects() {
        noFields().that().haveNameMatching("(?i).*hours").and().areDeclaredInClassesThat().resideInAPackage("..domain..")
                .should().haveRawType(java.math.BigDecimal.class).orShould().haveRawType(double.class)
                .orShould().haveRawType(Double.class)
                .because("use kernel.Hours (non-negative, 2 decimals) instead of a bare number")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void noBigDecimalEqualsInBusinessCode() {
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
                .should().callMethod(java.math.BigDecimal.class, "equals", Object.class)
                .orShould().callConstructor(java.math.BigDecimal.class, double.class)
                .because("BigDecimal.equals is scale-sensitive and new BigDecimal(double) is inexact; use Money/Hours or compareTo")
                .check(CLASSES);
    }

    @Test
    void timeComesFromAnInjectedClock() {
        TimeSourceGuardTest.noAmbientTime().check(CLASSES);
    }

    @Test
    void noDebugOrDataFixCodeInMain() {
        noClasses().should().haveSimpleNameStartingWith("Fix").orShould().haveSimpleNameStartingWith("Demo")
                .orShould().haveSimpleNameStartingWith("Mock").orShould().haveSimpleNameStartingWith("Test")
                .orShould().haveSimpleNameStartingWith("Sample").orShould().haveSimpleNameContaining("Example")
                .because("legacy shipped @PermitAll data-fix and demo controllers to production; repairs are Flyway scripts or ops jobs")
                .check(CLASSES);
    }

    @Test
    void useCasesExposeExactlyOnePublicMethod() {
        classes().that().resideInAPackage("..application..").and().areAnnotatedWith("org.springframework.stereotype.Service")
                .should(new ArchCondition<>("expose exactly one public method") {
                    @Override
                    public void check(JavaClass c, ConditionEvents events) {
                        long n = c.getMethods().stream().filter(m -> m.getModifiers().contains(JavaModifier.PUBLIC)).count();
                        if (n != 1) {
                            events.add(SimpleConditionEvent.violated(c, c.getName() + " has " + n + " public methods"));
                        }
                    }
                })
                .because("one use case per class: no god services, and no this.call() bypassing @Transactional")
                .check(CLASSES);
    }

    @Test
    void everyHandlerIsAuthorizedOrExplicitlyPublic() {
        methods().that().areDeclaredInClassesThat().resideInAPackage("..web..")
                .and().areAnnotatedWith("org.springframework.web.bind.annotation.GetMapping")
                .or().areAnnotatedWith("org.springframework.web.bind.annotation.PostMapping")
                .or().areAnnotatedWith("org.springframework.web.bind.annotation.PutMapping")
                .or().areAnnotatedWith("org.springframework.web.bind.annotation.PatchMapping")
                .or().areAnnotatedWith("org.springframework.web.bind.annotation.DeleteMapping")
                .or().areAnnotatedWith("org.springframework.web.bind.annotation.RequestMapping")
                .should().beAnnotatedWith("org.springframework.security.access.prepost.PreAuthorize")
                .orShould().beAnnotatedWith(com.forcitis.one.kernel.PublicEndpoint.class)
                .because("default deny: legacy shipped 84 @PermitAll write endpoints and ~190 endpoints with no permission check")
                .check(CLASSES);
    }

    @Test
    void onlyIdentityConfiguresSecurity() {
        noClasses().that().resideOutsideOfPackage(ROOT + ".platform.identity..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.security.config..",
                        "org.springframework.security.web..")
                .because("one security configuration, owned by the identity module; no per-module permitAll()")
                .check(CLASSES);
    }

    @Test
    void requestsNeverCarryTheCallerIdentity() {
        noFields().that().areDeclaredInClassesThat().resideInAPackage("..web..")
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Request")
                .should().haveNameMatching("(?i)(userId|creator|creatorId|operatorId|approverId|submitterId|loginUser)")
                .because("the caller comes from CurrentUser (session); legacy trusted a client-sent creator (SEC-005)")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void contractsAreStronglyTyped() {
        noFields().that().areDeclaredInClassesThat().resideInAnyPackage("..api..", "..events..")
                .should().haveRawType(Object.class).orShould().haveRawType(java.util.Map.class)
                .because("no Map<String,Object> / Object in module contracts: use records, enums and typed ids")
                .allowEmptyShould(true)
                .check(CLASSES);
        fields().that().areDeclaredInClassesThat().resideInAnyPackage("..api..", "..events..", "..domain..")
                .and().haveNameMatching(".*Id")
                .should().haveRawType(new DescribedPredicate<JavaClass>("a typed id record") {
                    @Override
                    public boolean test(JavaClass t) {
                        return t.isRecord() && t.getSimpleName().endsWith("Id");
                    }
                })
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void eventsAreFactsNotRequests() {
        noClasses().that().resideInAPackage("..events..")
                .should().haveSimpleNameEndingWith("Request").orShould().haveSimpleNameEndingWith("Requested")
                .orShould().haveSimpleNameEndingWith("Command").orShould().haveSimpleNameEndingWith("Query")
                .because("events announce facts; synchronous questions go through api interfaces")
                .check(CLASSES);
        noMethodsAnnotatedWithEventListenerOutsideModuleListener();
    }

    private static void noMethodsAnnotatedWithEventListenerOutsideModuleListener() {
        methods().that().areAnnotatedWith("org.springframework.context.event.EventListener")
                .should().beDeclaredInClassesThat().resideInAPackage(ROOT + ".nothing")
                .because("cross-module listeners use @ApplicationModuleListener (async, own transaction, retried)")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void namedInterfacesAreOnlyApiAndEvents() {
        classes().that().haveSimpleName("package-info")
                .and().areAnnotatedWith("org.springframework.modulith.NamedInterface")
                .should().resideInAnyPackage("..api", "..events")
                .because("a module has exactly two exits; extra named interfaces would bypass the narrow-interface budget")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @SuppressWarnings("unused")
    private static final JavaModifier UNUSED = JavaModifier.PUBLIC;
}
