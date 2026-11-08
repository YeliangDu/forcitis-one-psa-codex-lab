package com.forcitis.one.acceptance.platform.identity.p2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** #31: only hosted JDK compilation/fresh JVM; no fixture in normal production/test output. */
class PermissionP2DiscoveryIT {
    static final TestDatabase postgres=TestPostgres.database(PermissionP2DiscoveryIT.class,TestPostgres.MIGRATED)
            .withUrlParam("currentSchema","platform_approval");
    @TempDir Path temporary;

    @Test void setupWitnessActualImporterAndSpringSeeCompiledLegacyOwnerContribution() throws Exception {
        Properties report=child("setup");
        assertTrue("true".equals(report.getProperty("importerIncluded")),"SETUP BLOCKED: unmodified ArchUnit importer excludes fixture");
        assertTrue("true".equals(report.getProperty("productionDiscoveryIncluded")),"SETUP BLOCKED: actual production discovery misses compiled class");
        assertTrue("true".equals(report.getProperty("springOk")),"SETUP BLOCKED: actual Spring legacy-overlap fixture must start");
        assertTrue("true".equals(report.getProperty("springContributionIncluded")),"SETUP BLOCKED: Spring does not see fixture component");
        assertTrue("true".equals(report.getProperty("parity")),"SETUP BLOCKED: actual plain/runtime catalogues differ");
    }

    @Test void newOwningFullResourceDeclarationAndDerivedSensitivityWorkInActualFreshContext() throws Exception {
        Properties report=child("new-codes");
        assertTrue("true".equals(report.getProperty("importerIncluded")),"SETUP BLOCKED: importer fixture precondition");
        assertTrue("true".equals(report.getProperty("productionDiscoveryIncluded")),"SETUP BLOCKED: real discovery precondition");
        assertTrue("true".equals(report.getProperty("plainOk")),"P2 BEHAVIOR RED: genuinely new registered module-owned declarations must be accepted");
        assertTrue("true".equals(report.getProperty("springOk")),"P2 BEHAVIOR RED: real startup must accept same new declarations without cycle");
        assertTrue("true".equals(report.getProperty("newCodesExact")),"new codes absent from facade but exact in both catalogues");
        assertTrue("true".equals(report.getProperty("sensitivityExact")),"derive baseline-nine plus only synthetic two-person code");
        assertTrue("true".equals(report.getProperty("baselineExact")),"legacy modes/deprecation and all system roles unchanged");
    }

    Properties child(String scenario) throws Exception {
        Path output=temporary.resolve(scenario).resolve("p2-fixture/classes");Files.createDirectories(output);
        Path sources=temporary.resolve(scenario).resolve("fixture-src");Files.createDirectories(sources);
        String code=scenario.equals("setup")?"platform.audit:audit-entry:read":"platform.audit:synthetic-probe-record:read";
        String declarations=scenario.equals("setup")?
            "List.of(new PermissionDeclaration(new PermissionCode(\""+code+"\"), PermissionGrantMode.TWO_PERSON_INDIVIDUAL, false))":
            "List.of(new PermissionDeclaration(new PermissionCode(\"platform.audit:synthetic-probe-record:read\"), PermissionGrantMode.INDIVIDUAL, false), new PermissionDeclaration(new PermissionCode(\"platform.audit:synthetic-probe-record:export\"), PermissionGrantMode.TWO_PERSON_INDIVIDUAL, false))";
        String api="""
            package com.forcitis.one.platform.audit.api;
            import java.util.List;
            import com.forcitis.one.kernel.*;
            public interface SyntheticP2FixtureContribution extends PermissionContribution {
                default String owner() { return "platform.audit"; }
                default List<PermissionDeclaration> permissions() { return REPLACE; }
            }
            """.replace("REPLACE",declarations);
        String component="""
            package com.forcitis.one.platform.audit.infrastructure.p2fixture;
            @org.springframework.stereotype.Component
            final class SyntheticP2FixtureBean implements com.forcitis.one.platform.audit.api.SyntheticP2FixtureContribution {}
            """;
        Path a=sources.resolve("SyntheticP2FixtureContribution.java"),b=sources.resolve("SyntheticP2FixtureBean.java");
        Files.writeString(a,api);Files.writeString(b,component);
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var compiler=ToolProvider.getSystemJavaCompiler();assertTrue(compiler!=null,"SETUP BLOCKED: hosted full JDK compiler required");
        int compiled=compiler.run(null,null,null,"-classpath",cp,"-d",output.toString(),a.toString(),b.toString());
        assertTrue(compiled==0,"SETUP BLOCKED: generated fixture must compile before any behavior red");
        Path vocabulary=output.resolve("permission-vocabulary/p2-fixture/independent.yaml");Files.createDirectories(vocabulary.getParent());
        Files.writeString(vocabulary,"""
            terms:
            - id: synthetic_probe_record
              zh: 合成权限探针
              en: SyntheticProbeRecord
              snake: synthetic_probe_record
              kind: read_model
              bc: platform.audit
              scope: domain
              status: proposed
              definition: Independent synthetic fixture only, no business audience or production grant.
              aliases: []
              forbidden: []
            """);
        Path config=temporary.resolve(scenario+"-db.properties"),report=temporary.resolve(scenario+"-report.properties"),log=temporary.resolve(scenario+"-child.log");
        Properties settings=new Properties();postgres.register((name,supplier)->settings.setProperty(name,String.valueOf(supplier.get())));
        try(var stream=Files.newOutputStream(config)){settings.store(stream,"synthetic hosted clone configuration; never upload this file");}
        // Test-created private config, not any production secret. Child logs/report are uploadable; this file isn't.
        try {Files.setPosixFilePermissions(config,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}
        catch(UnsupportedOperationException error){throw new AssertionError("hosted POSIX private fixture required",error);}
        String childCp=canonicalChildClasspath(output,cp);
        List<String> command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),
            "-Duser.timezone=UTC","-cp",childCp,PermissionP2Child.class.getName(),scenario,
            output.toString(),config.toString(),report.toString()));
        Process child=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(Duration.ofSeconds(180).toMillis(),java.util.concurrent.TimeUnit.MILLISECONDS),"SETUP BLOCKED: bounded child JVM timeout");
            assertTrue(child.exitValue()==0,"SETUP BLOCKED: child crash is not assertion red; inspect sanitized child log");
        } finally {if(child.isAlive()){child.destroyForcibly();child.waitFor(10,java.util.concurrent.TimeUnit.SECONDS);}Files.deleteIfExists(config);}
        assertTrue(Files.isRegularFile(report),"SETUP BLOCKED: child report missing");
        Properties result=new Properties();try(var stream=Files.newInputStream(report)){result.load(stream);}
        // Child generates these allowlisted class/property-name/failure-code fields;
        // do not print arbitrary report entries, child log, configuration or values.
        result.stringPropertyNames().stream().filter(key->key.startsWith("diagnostic.")).sorted()
                .forEach(key->System.out.println("P2_SAFE_DIAGNOSTIC "+key+"="+result.getProperty(key)));
        return result;
    }
    /** Physical de-dup only: keep fixture first and every legitimate classes/test/dependency entry. */
    static String canonicalChildClasspath(Path fixture, String inherited) throws Exception {
        java.util.LinkedHashSet<Path> entries=new java.util.LinkedHashSet<>();
        entries.add(fixture.toRealPath());
        String[] tokens=inherited.split(java.util.regex.Pattern.quote(File.pathSeparator),-1);
        assertTrue(tokens.length<=4096,"SETUP BLOCKED: excessive child classpath token count");
        int empty=0,whitespace=0,wildcard=0;
        for(String token:tokens) {
            if(token.isEmpty())empty++;
            else if(token.isBlank())whitespace++;
            if(token.contains("*"))wildcard++;
        }
        int trailingEmptyIndex=tokens.length>0 && tokens[tokens.length-1].isEmpty()?tokens.length-1:-1;
        boolean omitOnlyTrailingCwd=empty==1 && trailingEmptyIndex>0;
        System.out.println("P2_SAFE_DIAGNOSTIC diagnostic.childClasspath.tokenCount="+tokens.length);
        System.out.println("P2_SAFE_DIAGNOSTIC diagnostic.childClasspath.emptyCount="+empty);
        System.out.println("P2_SAFE_DIAGNOSTIC diagnostic.childClasspath.whitespaceCount="+whitespace);
        System.out.println("P2_SAFE_DIAGNOSTIC diagnostic.childClasspath.wildcardCount="+wildcard);
        System.out.println("P2_SAFE_DIAGNOSTIC diagnostic.childClasspath.trailingEmptyIndex="+trailingEmptyIndex);
        System.out.println("P2_SAFE_DIAGNOSTIC diagnostic.childClasspath.omittedImplicitTrailingCwd="+omitOnlyTrailingCwd);
        assertTrue(empty==0 || omitOnlyTrailingCwd,"SETUP BLOCKED: internal/multiple empty child classpath tokens");
        assertTrue(whitespace==0 && wildcard==0,"SETUP BLOCKED: whitespace/wildcard child classpath token");
        int end=omitOnlyTrailingCwd?tokens.length-1:tokens.length;
        for(int index=0;index<end;index++) {
            String entry=tokens[index];
            assertTrue(!entry.isBlank() && !entry.contains("*"),"SETUP BLOCKED: ambiguous child classpath entry");
            Path physical=Path.of(entry).toRealPath();
            assertTrue(Files.isDirectory(physical) || Files.isRegularFile(physical),"SETUP BLOCKED: missing classpath object");
            entries.add(physical);
        }
        System.out.println("P2_SAFE_DIAGNOSTIC diagnostic.childClasspathPhysicalEntries="+entries.size());
        return entries.stream().map(Path::toString).collect(java.util.stream.Collectors.joining(File.pathSeparator));
    }

}
