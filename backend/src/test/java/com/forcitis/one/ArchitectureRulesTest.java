package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {
    static final String ROOT = "com.forcitis.one";
    static final int MAX_API_METHODS_PER_MODULE = 12;
    // 登记例外：模块 -> {上限, 出处}；全局上限不抬，新增例外须有出处并经 one-architecture 裁定
    static final Map<String, ApiLimitException> API_LIMIT_EXCEPTIONS = Map.of(
            ROOT + ".platform.integration", new ApiLimitException(17,
                    "ADR-0060 检测与业务文件桶端口；one-architecture 裁定 2026-10-21；到期：ADR-0069 D2 合入"),
            ROOT + ".organization.workforce", new ApiLimitException(13,
                    "#1440 G1 回填经 workforce api 列出法人（已签设计）；one-lead 裁定 2026-11-05（代 one-architecture，无该会话）；到期：法人目录移出 workforce，或退役一个 api 方法"));

    record ApiLimitException(int max, String source) {}

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void apiPackagesContainOnlyInterfacesAndRecords() {
        classes().that().resideInAPackage("..api..").and().doNotHaveSimpleName("package-info")
                .should().beInterfaces().orShould().beRecords().orShould().beEnums()
                .because(GuardMessage.of("G2", "a class in an api package is not an interface, record or enum (a module's api is a narrow contract)", "make it an interface, record or enum, or move the implementation to ..application.. or ..infrastructure.."))
                .check(CLASSES);
    }

    @Test
    void apiSignaturesDoNotLeakModuleInternals() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage("..domain..", "..application..", "..infrastructure..", "..web..")
                .because(GuardMessage.of("G3", "an api type depends on domain, application, infrastructure or web classes", "change the signature to use api records, kernel types or JDK types and map the internal model inside the module"))
                .check(CLASSES);
    }

    @Test
    void eventsAreRecords() {
        classes().that().resideInAPackage("..events..").and().doNotHaveSimpleName("package-info")
                .should().beRecords()
                .andShould().haveSimpleNameNotEndingWith("Event")
                .because(GuardMessage.of("G5", "a class in an events package is not a record or its name ends with Event", "make it a record named as a past-tense fact without the Event suffix, e.g. TimesheetApproved"))
                .check(CLASSES);
    }

    @Test
    void apiSurfaceStaysNarrow() {
        // 只数有模块外调用方的方法：被另一模块实现的必需端口（本模块调用、别处实现）不是本模块对外的面
        Map<String, Long> perModule = CLASSES.stream()
                .filter(c -> c.getPackageName().matches(".*\\.api(\\..*)?$") && c.isInterface())
                .collect(Collectors.groupingBy(c -> moduleOf(c),
                        Collectors.summingLong(c -> c.getMethods().stream().filter(m -> hasCallerOutside(m, moduleOf(c))).count())));
        assertApiSurfaceNarrow(perModule);
    }

    static String moduleOf(JavaClass api) {
        return api.getPackageName().replaceFirst("\\.api(\\..*)?$", "");
    }

    static boolean hasCallerOutside(JavaMethod method, String module) {
        return method.getAccessesToSelf().stream()
                .anyMatch(a -> !a.getOriginOwner().getPackageName().equals(module) && !a.getOriginOwner().getPackageName().startsWith(module + "."));
    }

    @Test
    void apiSurfaceExceptionModuleAllowsExactlyItsLimit() {
        String module = ROOT + ".platform.integration";
        assertApiSurfaceNarrow(Map.of(module, 17L));
        assertThatThrownBy(() -> assertApiSurfaceNarrow(Map.of(module, 18L)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Deepen the module instead of widening its interface.");
    }

    @Test
    void apiMethodsWithoutCallerOutsideTheModuleAreNotCounted() {
        String workforce = ROOT + ".organization.workforce";
        JavaClass port = CLASSES.get(workforce + ".api.LegalEntityNumberUsage");   // required port: workforce calls it, platform.numbering implements it
        assertThat(port.getMethods()).isNotEmpty().noneMatch(m -> hasCallerOutside(m, workforce));
        JavaClass exposed = CLASSES.get(workforce + ".api.EmployeeLegalEntities");
        assertThat(exposed.getMethods()).anyMatch(m -> hasCallerOutside(m, workforce));
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
                throw new AssertionError(GuardMessage.of("G4",
                        module + " exposes " + count + " api methods (max " + max + ")",
                        "Deepen the module instead of widening its interface. A wider limit needs an entry in API_LIMIT_EXCEPTIONS with a source and a one-architecture ruling"));
            }
        });
    }

    @Test
    void webAndInfrastructureAreNotPublic() {
        classes().that().resideInAnyPackage("..web..", "..infrastructure..")
                .and().doNotHaveFullyQualifiedName(ROOT + ".platform.web.NotFromQuery")
                .should().notBePublic()
                .because(GuardMessage.of("G6", "a class in web or infrastructure is public", "remove the public modifier so the class is package-private; Spring wires it and other code must go through the api"))
                .check(CLASSES);
    }

    @Test
    void domainIsFrameworkFree() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.servlet..", "..application..", "..infrastructure..", "..web..")
                .because(GuardMessage.of("G7", "a domain class depends on Spring, Servlet or another layer", "remove the dependency from domain: declare a port interface in domain and implement it in infrastructure, or pass the value in from application"))
                .check(CLASSES);
    }

    @Test
    void applicationDoesNotTouchWebOrInfrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..web..", "..infrastructure..")
                .because(GuardMessage.of("G8", "an application class depends on web or infrastructure", "depend on the domain port interface and let Spring inject the infrastructure adapter"))
                .check(CLASSES);
    }

    @Test
    void webOnlyCallsApplication() {
        noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..domain..")
                .because(GuardMessage.of("G9", "a web class depends on infrastructure or domain", "call one application use case from the controller and return an api record"))
                .check(CLASSES);
    }

    @Test
    void jdbcOnlyInInfrastructure() {
        noClasses().that().resideOutsideOfPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
                .because(GuardMessage.of("G10", "a class outside infrastructure uses JDBC or java.sql", "move the SQL into a Jdbc*Repository in ..infrastructure.persistence.. behind a domain port"))
                .check(CLASSES);
    }

    @Test
    void transactionsOnlyInApplication() {
        methods().that().areAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .should().beDeclaredInClassesThat().resideInAPackage("..application..")
                .because(GuardMessage.of("G11", "a @Transactional method is declared outside ..application..", "move @Transactional to the application use case that owns the transaction"))
                .check(CLASSES);
        noClasses().that().resideOutsideOfPackage("..application..")
                .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.transaction.support.TransactionTemplate")
                .because(GuardMessage.of("G11", "a class outside ..application.. is @Transactional or uses TransactionTemplate", "move the transaction boundary into the application use case"))
                .check(CLASSES);
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("jakarta.transaction.Transactional")
                .because(GuardMessage.of("G11", "jakarta.transaction.Transactional is used", "use org.springframework.transaction.annotation.Transactional on the application use case"))
                .check(CLASSES);
    }

    @Test
    void controllersLiveInWebAndEndWithController() {
        classes().that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().resideInAPackage("..web..").andShould().haveSimpleNameEndingWith("Controller")
                .because(GuardMessage.of("G12", "a @RestController is outside ..web.. or its name does not end with Controller", "move it to the module's ..web.. package and rename it <Name>Controller"))
                .check(CLASSES);
    }

    @Test
    void repositoriesFollowPortAdapterNaming() {
        classes().that().resideInAPackage("..domain..").and().haveSimpleNameEndingWith("Repository")
                .should().beInterfaces()
                .because(GuardMessage.of("G13", "a *Repository class in domain is not an interface", "make it an interface (the port) and put the implementation in ..infrastructure.persistence.. as Jdbc<Name>Repository"))
                .check(CLASSES);
        classes().that().resideInAPackage("..infrastructure.persistence..").and().areAnnotatedWith("org.springframework.stereotype.Repository")
                .should().haveSimpleNameStartingWith("Jdbc")
                .because(GuardMessage.of("G13", "a @Repository in ..infrastructure.persistence.. does not start with Jdbc", "rename it Jdbc<Name>Repository"))
                .check(CLASSES);
    }

    @Test
    void noVagueOrLegacyNames() {
        noClasses().should().haveSimpleNameEndingWith("Util").orShould().haveSimpleNameEndingWith("Utils")
                .orShould().haveSimpleNameEndingWith("Helper").orShould().haveSimpleNameEndingWith("Manager")
                .orShould().haveSimpleNameEndingWith("Info").orShould().haveSimpleNameEndingWith("Data")
                .orShould().haveSimpleNameEndingWith("Impl").orShould().haveSimpleNameEndingWith("DO")
                .orShould().haveSimpleNameContaining("ManHour").orShould().haveSimpleNameContaining("Manhour")
                .because(GuardMessage.of("G14", "a class name ends with Util, Utils, Helper, Manager, Info, Data, Impl or DO, or contains ManHour", "rename the class with the glossary term from docs/glossary/terms.yaml, e.g. Timesheet, TimeEntry, Quote, RateCardItem"))
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
        classes().should(asciiOnly).because(GuardMessage.of("G15", "a class or member name is not ASCII", "rename it with an English glossary term; keep Chinese only in UI text and comments"))
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
                .because(GuardMessage.of("G16", "a domain field named *status is not an enum", "declare the status as an enum stored by name"))
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void moneyIsNeverABareDecimal() {
        noFields().that().haveNameMatching("(?i).*(amount|price|cost|total|balance|fee|wage|tax)")
                .and().areDeclaredInClassesThat().resideInAnyPackage("..domain..", "..api..", "..events..")
                .should().haveRawType(java.math.BigDecimal.class).orShould().haveRawType(double.class)
                .orShould().haveRawType(Double.class).orShould().haveRawType(long.class)
                .because(GuardMessage.of("G17", "a domain, api or events field named like amount, price, cost, total, balance, fee, wage or tax is a bare number", "use kernel.Money (amount and currency) instead of BigDecimal, double or long"))
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void generalCodingRules() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.because(GuardMessage.of("G18", "a class uses field injection", "inject through the constructor")).check(CLASSES);
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because(GuardMessage.of("G18", "a class uses System.out or System.err", "log through SLF4J instead")).check(CLASSES);
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.because(GuardMessage.of("G18", "a class uses java.util.logging", "log through SLF4J instead")).check(CLASSES);
        noClasses().that().doNotHaveFullyQualifiedName(ROOT + ".platform.approval.infrastructure.flowable.FlowableEngineClock")
                .and().doNotHaveFullyQualifiedName(ROOT + ".platform.integration.infrastructure.filestore.OssBusinessFileStore")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Date")
                .because(GuardMessage.of("G18", "a class depends on java.util.Date", "use java.time types; only FlowableEngineClock and OssBusinessFileStore may use java.util.Date"))
                .check(CLASSES);
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("jakarta.annotation.security.PermitAll")
                .because(GuardMessage.of("G18", "a class uses @PermitAll", "remove @PermitAll; secure the endpoint with @PreAuthorize, or declare it @PublicEndpoint(reason)"))
                .check(CLASSES);
    }

    @Test
    void flowableOnlyInsideApprovalInfrastructure() {
        noClasses().that().resideOutsideOfPackage(ROOT + ".platform.approval.infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("org.flowable..")
                .because(GuardMessage.of("G19", "a class outside platform.approval.infrastructure depends on org.flowable", "remove the Flowable dependency and call the approval module through its api"))
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
                .because(GuardMessage.of("G20", "a @RestController does not declare @RequestMapping starting with /api/v1/", "add @RequestMapping(\"/api/v1/<subdomain>/<resources>\") to the controller"))
                .check(CLASSES);
    }

    @Test
    void valueObjectsAreRecords() {
        classes().that().areAnnotatedWith(com.forcitis.one.kernel.ValueObject.class)
                .should().beRecords()
                .because(GuardMessage.of("G21", "a @ValueObject class is not a record", "make it a record that validates itself in the compact constructor"))
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
                .because(GuardMessage.of("G22", "a @ValueObject holds a Date, ArrayList, HashMap or array field", "use an immutable type such as java.time, List.copyOf or Map.copyOf"))
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
                .because(GuardMessage.of("G31", "a domain field named *Id is not a typed id record", "declare the field with the typed id record, e.g. TimesheetId, instead of UUID, String or long"))
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void hoursAndQuantitiesUseValueObjects() {
        noFields().that().haveNameMatching("(?i).*hours").and().areDeclaredInClassesThat().resideInAPackage("..domain..")
                .should().haveRawType(java.math.BigDecimal.class).orShould().haveRawType(double.class)
                .orShould().haveRawType(Double.class)
                .because(GuardMessage.of("G23", "a domain field named *hours is a bare BigDecimal or double", "use kernel.Hours"))
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void noBigDecimalEqualsInBusinessCode() {
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
                .should().callMethod(java.math.BigDecimal.class, "equals", Object.class)
                .orShould().callConstructor(java.math.BigDecimal.class, double.class)
                .because(GuardMessage.of("G24", "domain or application code calls BigDecimal.equals or new BigDecimal(double)", "compare with compareTo or use Money/Hours, and build with BigDecimal.valueOf or new BigDecimal(String)"))
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
                .because(GuardMessage.of("G26", "a main class is named Fix*, Demo*, Mock*, Test*, Sample* or *Example*", "delete the class; repairs are Flyway scripts or ops jobs and test doubles belong in src/test"))
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
                .because(GuardMessage.of("G27", "an application @Service does not have exactly one public method", "split it into one use case class per action with a single public execute method"))
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
                .because(GuardMessage.of("G28", "a web handler has neither @PreAuthorize nor @PublicEndpoint", "add @PreAuthorize with a catalogued permission code, or @PublicEndpoint(reason) if it is deliberately open"))
                .check(CLASSES);
    }

    @Test
    void onlyIdentityConfiguresSecurity() {
        noClasses().that().resideOutsideOfPackage(ROOT + ".platform.identity..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.security.config..",
                        "org.springframework.security.web..")
                .because(GuardMessage.of("G33", "a class outside platform.identity depends on spring-security config or web classes", "remove it; only the identity module configures Spring Security"))
                .check(CLASSES);
    }

    @Test
    void requestsNeverCarryTheCallerIdentity() {
        noFields().that().areDeclaredInClassesThat().resideInAPackage("..web..")
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Request")
                .should().haveNameMatching("(?i)(userId|creator|creatorId|operatorId|approverId|submitterId|loginUser)")
                .because(GuardMessage.of("G33", "a web *Request record has a caller identity field such as userId or creator", "remove the field and read the caller from CurrentUser"))
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void contractsAreStronglyTyped() {
        noFields().that().areDeclaredInClassesThat().resideInAnyPackage("..api..", "..events..")
                .should().haveRawType(Object.class).orShould().haveRawType(java.util.Map.class)
                .because(GuardMessage.of("G32", "an api or events field is Object or Map", "use a record, an enum or a typed id instead"))
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
                .because(GuardMessage.of("G31", "an api, events or domain field named *Id is not a typed id record", "declare the field with the typed id record, e.g. TimesheetId, instead of UUID, String or long"))
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void eventsAreFactsNotRequests() {
        noClasses().that().resideInAPackage("..events..")
                .should().haveSimpleNameEndingWith("Request").orShould().haveSimpleNameEndingWith("Requested")
                .orShould().haveSimpleNameEndingWith("Command").orShould().haveSimpleNameEndingWith("Query")
                .because(GuardMessage.of("G29", "an events class name ends with Request, Requested, Command or Query", "rename it as a past-tense fact, or replace it with a call on the module's api interface"))
                .check(CLASSES);
        noMethodsAnnotatedWithEventListenerOutsideModuleListener();
    }

    private static void noMethodsAnnotatedWithEventListenerOutsideModuleListener() {
        methods().that().areAnnotatedWith("org.springframework.context.event.EventListener")
                .should().beDeclaredInClassesThat().resideInAPackage(ROOT + ".nothing")
                .because(GuardMessage.of("G29", "a method uses plain @EventListener", "use @ApplicationModuleListener"))
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void namedInterfacesAreOnlyApiAndEvents() {
        classes().that().haveSimpleName("package-info")
                .and().areAnnotatedWith("org.springframework.modulith.NamedInterface")
                .should().resideInAnyPackage("..api", "..events")
                .because(GuardMessage.of("G30", "@NamedInterface is used outside an api or events package", "remove @NamedInterface from that package; expose only api and events"))
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    // DATA_DESIGN_CHECKLIST section 14 row 3: events come from the aggregate; the application hands them to the registry and never makes one itself.
    static List<String> eventsBuiltOutsideTheDomain(JavaClasses classes) {
        List<String> problems = new ArrayList<>();
        for (JavaClass type : classes) {
            if (type.getPackageName().matches(".*\\.(domain|events)(\\..*)?")) {
                continue;
            }
            type.getConstructorCallsFromSelf().stream().filter(call -> call.getTargetOwner().isRecord() && call.getTargetOwner().getPackageName().matches(".*\\.events(\\..*)?"))
                    .forEach(call -> problems.add(type.getName() + " builds the event " + call.getTargetOwner().getSimpleName()));
        }
        return problems.stream().distinct().toList();
    }

    @Test
    void eventsAreMadeByTheAggregateAndPublishedOnlyByEventing() {
        assertThat(eventsBuiltOutsideTheDomain(CLASSES)).as(GuardMessage.of("DDD-3", "an event record is built outside a domain class",
                "build the event in the aggregate's transition method (domain) and have the use case hand it to DomainEvents")).isEmpty();
        noClasses().that().resideOutsideOfPackage(ROOT + ".platform.eventing..").should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.context.ApplicationEventPublisher")
                .because(GuardMessage.of("DDD-3", "a class outside platform.eventing uses ApplicationEventPublisher", "hand the event to DomainEvents; platform.eventing publishes through the Modulith registry")).check(CLASSES);
        JavaClasses fixture = new ClassFileImporter().withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS).importPackages("com.forcitis.one.fixture.eventbuild");
        assertThat(eventsBuiltOutsideTheDomain(fixture)).as("injected: only the use case that builds the event is caught, not the aggregate").hasSize(1).first().asString().contains("ThingUseCase");
    }

    // DATA_DESIGN_CHECKLIST section 14.3 I3: no external system is called inside a database transaction: a @Transactional use case, with the application helpers it calls, reaches no external-call port.
    // WecomGateway is not one: it writes the outbox and is meant to run inside the transaction. Existing gaps are listed and may only shrink.
    static final List<String> EXTERNAL_CALL_PORTS = List.of("WecomClient", "OrgSource", "OrgShortNames", "ArchiveVault", "BusinessFileStore", "ObjectStore", "MalwareScanner", "ExternalConnections");
    static final java.nio.file.Path EXTERNAL_GAPS = java.nio.file.Path.of("architecture/external-call-in-transaction-gaps.txt");

    static List<String> externalCallsInTransactions(JavaClasses classes, java.util.function.Predicate<JavaClass> port) {
        java.util.Set<String> problems = new java.util.TreeSet<>();
        for (JavaClass type : classes) {
            for (JavaMethod entry : TransactionalUseCases.entries(type)) {
                TransactionalUseCases.reachableAccesses(entry).stream().filter(a -> port.test(a.owner())).forEach(a -> problems.add(type.getName()));
            }
        }
        return new ArrayList<>(problems);
    }

    @Test
    void noExternalCallInATransaction() throws java.io.IOException {
        assertThat(EXTERNAL_CALL_PORTS).as(GuardMessage.of("I3", "EXTERNAL_CALL_PORTS names a type that no longer exists", "update the list in ArchitectureRulesTest to the current external-call ports"))
                .allMatch(name -> CLASSES.stream().anyMatch(c -> c.getSimpleName().equals(name)));
        List<String> gaps = java.nio.file.Files.readAllLines(EXTERNAL_GAPS).stream().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        List<String> found = externalCallsInTransactions(CLASSES, c -> EXTERNAL_CALL_PORTS.contains(c.getSimpleName()) && c.getPackageName().startsWith(ROOT + ".platform.integration"));
        assertThat(found.stream().filter(c -> !gaps.contains(c)).toList()).as(GuardMessage.of("I3",
                "a @Transactional use case, directly or through an application helper, reaches an external-call port, so an external call may run inside a database transaction",
                "do the external call outside the transaction, or send through the outbox (OutboundDelivery); do not add it to external-call-in-transaction-gaps.txt")).isEmpty();
        assertThat(gaps.stream().filter(c -> !found.contains(c)).toList()).as(GuardMessage.of("I3",
                "backend/architecture/external-call-in-transaction-gaps.txt lists a use case that no longer reaches an external-call port, or no longer exists", "delete the line (the list may only shrink)")).isEmpty();
        JavaClasses fixture = new ClassFileImporter().withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS).importPackages("com.forcitis.one.fixture.externaltx");
        assertThat(externalCallsInTransactions(fixture, c -> c.getSimpleName().equals("RemoteGateway"))).as("injected: the transactional caller and the one that goes through a helper are caught, the one without a transaction is not")
                .containsExactly("com.forcitis.one.fixture.externaltx.application.CallsOutsideInTransaction", "com.forcitis.one.fixture.externaltx.application.CallsViaHelper");
    }

    @SuppressWarnings("unused")
    private static final JavaModifier UNUSED = JavaModifier.PUBLIC;
}
