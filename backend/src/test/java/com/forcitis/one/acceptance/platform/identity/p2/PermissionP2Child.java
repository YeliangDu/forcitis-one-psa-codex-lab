package com.forcitis.one.acceptance.platform.identity.p2;

import com.forcitis.one.OneApplication;
import com.forcitis.one.kernel.PermissionContribution;
import com.forcitis.one.kernel.PermissionGrantMode;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.PermissionCatalogue;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import com.forcitis.one.platform.identity.domain.SystemRoleDeclaration;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Hosted child launcher only. Never a normal production bean or replacement catalogue/provider. */
public final class PermissionP2Child {
    static final String FIXTURE="com.forcitis.one.platform.audit.infrastructure.p2fixture.SyntheticP2FixtureBean";
    static final String INDIVIDUAL="platform.audit:synthetic-probe-record:read";
    static final String TWO_PERSON="platform.audit:synthetic-probe-record:export";

    @TestConfiguration
    public static class ExternalTransportOnly {
        @Bean @Primary WecomGateway fixtureWecomGateway() {
            return (WecomGateway)Proxy.newProxyInstance(WecomGateway.class.getClassLoader(),new Class<?>[]{WecomGateway.class},(proxy,method,args)->switch(method.getName()) {
                case "resolveWecomUserId" -> Optional.empty();
                case "send","markHandled" -> throw new AssertionError("synthetic P2 discovery must not issue external delivery");
                case "toString" -> "synthetic-disabled-external-transport";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==args[0];
                default -> throw new AssertionError("unexpected external method");
            });
        }
    }

    public static void main(String[] args) throws Exception {
        String scenario=args[0];Path output=Path.of(args[1]),config=Path.of(args[2]),reportPath=Path.of(args[3]);
        Properties report=new Properties();
        for(String flag:List.of("importerIncluded","productionDiscoveryIncluded","plainOk","springOk","springContributionIncluded","parity","newCodesExact","sensitivityExact","baselineExact"))report.setProperty(flag,"false");
        try {
            boolean included=ImportOption.Predefined.DO_NOT_INCLUDE_TESTS.includes(Location.of(output.toUri()))
                && new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.forcitis.one")
                    .stream().anyMatch(type->type.getName().equals(FIXTURE));
            report.setProperty("importerIncluded",Boolean.toString(included));
            // Actual P1 source, real ArchUnit discovery, NOT manual injection into catalogue.
            Object provider=PermissionCatalogue.source();
            var contributionsMethod=provider.getClass().getDeclaredMethod("contributions");
            if(!contributionsMethod.trySetAccessible())throw new IllegalStateException("production discovery inaccessible to fixture witness");
            Object raw=contributionsMethod.invoke(provider);
            boolean discovered=raw instanceof List<?> entries && entries.stream().anyMatch(value->value.getClass().getName().equals(FIXTURE));
            report.setProperty("productionDiscoveryIncluded",Boolean.toString(discovered));
            PermissionCatalogue plain=null;
            try {plain=PermissionCatalogue.fromClasspath();report.setProperty("plainOk","true");}
            catch(RuntimeException|LinkageError refusal){report.setProperty("plainRefusal",refusal.getClass().getSimpleName());diagnose(report,"plain",refusal);}

            Properties supplied=new Properties();try(var stream=Files.newInputStream(config)){supplied.load(stream);}
            Set<String> connectionKeys=Set.of("spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                    "spring.flyway.url", "spring.flyway.user", "spring.flyway.password");
            if(!connectionKeys.containsAll(supplied.stringPropertyNames()))throw new IllegalArgumentException("unexpected fixture property key");
            Map<String,Object> properties=new TreeMap<>();supplied.forEach((key,value)->properties.put(key.toString(),value));
            properties.put("server.address","127.0.0.1");properties.put("server.port",0);
            properties.put("spring.main.web-application-type","servlet");
            properties.put("flowable.async-executor-activate",false);properties.put("flowable.database-schema-update",true);
            properties.put("spring.flyway.create-schemas",true);
            for(String key:List.of("forcitis.platform.integration.event-publication.jobs-enabled","forcitis.platform.scheduling.jobs-enabled",
                "forcitis.platform.identity.emergency.check.enabled","forcitis.cost.timesheet.consumed-events.enabled",
                "forcitis.platform.approval.process-archive.enabled","forcitis.platform.integration.outbound-purge.enabled",
                "forcitis.platform.integration.archive-store.enabled","forcitis.platform.audit.archive.enabled",
                "forcitis.platform.approval.process-administrator-required"))properties.put(key,false);
            try(var context=new SpringApplicationBuilder(OneApplication.class,ExternalTransportOnly.class)
                    .web(WebApplicationType.SERVLET).properties(properties)
                    .initializers(application -> {
                        // Highest priority ONLY for fixed fixture switches and the
                        // test-owned clone connection; no CLI credentials or general overrides.
                        application.getEnvironment().getPropertySources().addFirst(
                                new org.springframework.core.env.MapPropertySource("p2-fixture-owned",Map.copyOf(properties)));
                        report.setProperty("diagnostic.fixtureOverridesFirst",Boolean.toString(
                                application.getEnvironment().getPropertySources().iterator().next().getName().equals("p2-fixture-owned")));
                        report.setProperty("diagnostic.loopbackRequested",Boolean.toString(
                                "127.0.0.1".equals(application.getEnvironment().getProperty("server.address"))));
                        report.setProperty("diagnostic.ephemeralPortRequested",Boolean.toString(
                                "0".equals(application.getEnvironment().getProperty("server.port"))));
                    }).run()) {
                report.setProperty("springOk","true");
                report.setProperty("diagnostic.jdbcSessionRepositoryPresent",Boolean.toString(
                        !context.getBeansOfType(org.springframework.session.jdbc.JdbcIndexedSessionRepository.class).isEmpty()));
                var beans=context.getBeansOfType(PermissionContribution.class);
                report.setProperty("springContributionIncluded",Boolean.toString(beans.values().stream().anyMatch(bean->bean.getClass().getName().equals(FIXTURE))));
                PermissionCatalogue runtime=context.getBean(PermissionCatalogue.class);
                report.setProperty("parity",Boolean.toString(plain!=null && runtime.entries().equals(plain.entries())));
                if(scenario.equals("new-codes")) {
                    Set<String> facade=java.util.Arrays.stream(Permission.values()).map(Permission::code).collect(Collectors.toSet());
                    Set<String> constants=new TreeSet<>();for(var field:Permission.Codes.class.getFields())constants.add((String)field.get(null));
                    boolean exact=!facade.contains(INDIVIDUAL)&&!facade.contains(TWO_PERSON)&&!constants.contains(INDIVIDUAL)&&!constants.contains(TWO_PERSON)
                        && runtime.isLive(INDIVIDUAL)&&runtime.isLive(TWO_PERSON)
                        && runtime.entries().get(INDIVIDUAL).grantMode()==PermissionGrantMode.INDIVIDUAL
                        && runtime.entries().get(TWO_PERSON).grantMode()==PermissionGrantMode.TWO_PERSON_INDIVIDUAL;
                    report.setProperty("newCodesExact",Boolean.toString(exact));
                    Set<String> expectedSensitive=new TreeSet<>();
                    Map<String,String> frozen=new TreeMap<>();
                    for(String line:resource("permissions.tsv")) {
                        String[] fields=line.split("\t");frozen.put(fields[0],fields[1]+":"+fields[2]);
                        if(fields[1].equals("TWO_PERSON_INDIVIDUAL")&&fields[2].equals("false"))expectedSensitive.add(fields[0]);
                    }
                    expectedSensitive.add(TWO_PERSON);
                    report.setProperty("sensitivityExact",Boolean.toString(expectedSensitive.equals(SensitivePermissions.CODES)
                        && SensitivePermissions.within(Set.of(INDIVIDUAL,TWO_PERSON)).equals(Set.of(TWO_PERSON))));
                    Map<String,String> actual=new TreeMap<>();runtime.entries().forEach((code,value)->{
                        if(!code.equals(INDIVIDUAL)&&!code.equals(TWO_PERSON))actual.put(code,value.grantMode()+":"+value.deprecated());});
                    List<String> roles=SystemRoleDeclaration.CURRENT.roles().stream().map(role->String.join("\t",role.code().value(),role.roleId().value().toString(),
                        role.definition().name(),role.definition().accessScope().name(),Boolean.toString(role.administrative()),Integer.toString(role.revisionNumber()),
                        String.join(",",new TreeSet<>(role.definition().permissionCodes()))).stripTrailing()).sorted().toList();
                    report.setProperty("baselineExact",Boolean.toString(actual.equals(frozen)&&roles.equals(resource("roles.tsv").stream().sorted().toList())));
                }
            } catch(RuntimeException|LinkageError refusal) {report.setProperty("springRefusal",refusal.getClass().getSimpleName());diagnose(report,"spring",refusal);}
        } finally {
            try(var stream=Files.newOutputStream(reportPath)){report.store(stream,"P2 synthetic child results; no connection data");}
        }
    }

    /** Safe allowlisted diagnostics only: never message, stack, config or credential values. */
    static void diagnose(Properties report, String stage, Throwable failure) {
        report.setProperty("diagnostic.requestedWebApplicationType", "SERVLET");
        Set<String> knownBeans=Set.of("permissionCatalogue", "springSecurityFilterChain", "httpSecurity", "dataSource", "flywayInitializer");
        Set<Throwable> visited=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        int depth=0;
        for(Throwable cause=failure;cause!=null && depth<16 && visited.add(cause);cause=cause.getCause()) {
            String prefix="diagnostic."+stage+"."+depth++;
            report.setProperty(prefix+".type", safeType(cause.getClass()));
            report.setProperty(prefix+".failureCode", safeFailureCode(cause));
            int recorded=0, inspected=0;
            for(StackTraceElement frame:cause.getStackTrace()) {
                if(inspected++>=64 || recorded>=8)break;
                String owner=frame.getClassName(), method=frame.getMethodName();
                if(!safeFrameOwner(owner) || !(method.matches("[A-Za-z0-9_$]+") || method.equals("<init>") || method.equals("<clinit>")))continue;
                String key=prefix+".frame."+recorded++;
                report.setProperty(key+".owner",owner);
                report.setProperty(key+".method",method);
                report.setProperty(key+".line",Integer.toString(Math.max(-1,frame.getLineNumber())));
                // Deliberately never serialize getFileName(), module location,
                // getMessage(), full StackTraceElement, path or configuration.
            }
            if(cause instanceof org.springframework.beans.factory.BeanCreationException bean) {
                String beanName=bean.getBeanName();
                report.setProperty(prefix+".bean",beanName!=null && knownBeans.contains(beanName)?beanName:"other-bean");
            }
            if(cause instanceof org.springframework.beans.factory.NoSuchBeanDefinitionException missing) {
                Class<?> required=missing.getBeanType();
                report.setProperty(prefix+".missingType",required==null?"not-class-addressed":safeType(required));
            }
            if(cause instanceof org.springframework.beans.factory.UnsatisfiedDependencyException dependency) {
                var point=dependency.getInjectionPoint();
                if(point!=null)report.setProperty(prefix+".injectionType",safeType(point.getDeclaredType()));
            }
            if(cause instanceof java.sql.SQLException sql) {
                String state=sql.getSQLState();
                report.setProperty(prefix+".sqlState",state!=null && state.matches("[A-Z0-9]{5}")?state:"unavailable");
            }
        }
    }

    static boolean safeFrameOwner(String owner) {
        return owner.matches("[A-Za-z0-9_.$]+") && (owner.startsWith("java.") || owner.startsWith("javax.")
                || owner.startsWith("org.springframework.") || owner.startsWith("org.apache.")
                || owner.startsWith("org.flywaydb.") || owner.startsWith("org.postgresql.")
                || owner.startsWith("com.forcitis.one.") || owner.startsWith("com.zaxxer."));
    }

    static String safeFailureCode(Throwable failure) {
        if(failure instanceof ExceptionInInitializerError)return "STATIC_INITIALIZATION";
        StackTraceElement[] frames=failure.getStackTrace();
        for(int i=0;i<Math.min(frames.length,64);i++) {
            String owner=frames[i].getClassName();
            if(owner.startsWith("org.springframework.boot.logging."))return "LOGGING_BOOTSTRAP";
            if(owner.startsWith("com.forcitis.one.platform.identity.infrastructure.permissions."))return "PERMISSION_CATALOGUE_BOOTSTRAP";
            if(owner.startsWith("org.apache.catalina.") || owner.startsWith("org.springframework.boot.web.server."))return "SERVLET_BOOTSTRAP";
            if(owner.equals("java.util.ServiceLoader"))return "SERVICE_LOADING";
        }
        return "OTHER_UNCLASSIFIED";
    }

    static String safeType(Class<?> type) {
        String name=type.getName();
        return name.matches("(?:java|javax|org\\.springframework|org\\.flywaydb|org\\.postgresql|com\\.zaxxer|com\\.forcitis\\.one)(?:\\.[A-Za-z0-9_$]+)+")?name:"other-type";
    }

    static List<String> resource(String name) throws Exception {
        var stream=PermissionP2Child.class.getResourceAsStream("/acceptance/identity/p1/"+name);
        if(stream==null)throw new IllegalStateException("missing frozen P1 snapshot");
        try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){return reader.lines().toList();}
    }
}
