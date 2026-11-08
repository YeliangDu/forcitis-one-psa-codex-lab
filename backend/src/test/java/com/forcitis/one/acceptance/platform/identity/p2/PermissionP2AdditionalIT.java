package com.forcitis.one.acceptance.platform.identity.p2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** #31 additive new JVM cases; never changes frozen original discovery tests. */
class PermissionP2AdditionalIT {
    static final TestDatabase postgres=TestPostgres.database(PermissionP2AdditionalIT.class,TestPostgres.MIGRATED)
            .withUrlParam("currentSchema","platform_approval");
    @TempDir Path temporary;

    @Test void foreignOwnerTokenOnlyResourceAndAnnotationCannotManufacturePermissions() throws Exception {
        for(String scenario:List.of("foreign-owner","token-only","foreign-term","annotation-only","duplicate")) {
            Properties report=child(scenario);
            assertTrue("true".equals(report.getProperty("importerIncluded")),"SETUP BLOCKED: real importer must see poisoned fixture");
            assertTrue("true".equals(report.getProperty("rejected")),"P2 rejects invalid owner/full-resource/declaration evidence");
        }
    }

    @Test void newLiveCustomRolePersistsAndReadsWhileSensitiveCreationAwaitsSecondPerson() throws Exception {
        Properties report=child("persistence");
        assertTrue("true".equals(report.getProperty("importerIncluded")),"SETUP BLOCKED: compiled fixture inclusion");
        assertTrue("true".equals(report.getProperty("actorsValid")),"SETUP BLOCKED: original complete actor catalogues");
        assertTrue("true".equals(report.getProperty("springOk")),"actual Spring must collect genuine new code before role behavior");
        assertTrue("true".equals(report.getProperty("ordinaryStored")),"actual CreateRole/SQL/GetRole must retain new live code");
        assertTrue("true".equals(report.getProperty("twoPersonAwaiting")),"real sensitive creation must open confirmation request, not grant directly");
        assertTrue("true".equals(report.getProperty("unknownRetiredRefused")),"unknown and all retired codes reject creation");
        assertTrue("true".equals(report.getProperty("filterExact")),"real role row filtering keeps new live code, drops unknown/retired");
    }

    Properties child(String scenario) throws Exception {
        Path classes=temporary.resolve(scenario+"/p2-fixture/classes"),src=temporary.resolve(scenario+"/source");
        Files.createDirectories(classes);Files.createDirectories(src);
        String code="platform.audit:synthetic-probe-record:read";
        String owner=scenario.equals("foreign-owner")?"platform.numbering":"platform.audit";
        String permissions="java.util.List.of(new com.forcitis.one.kernel.PermissionDeclaration(new com.forcitis.one.kernel.PermissionCode(\""+code+"\"),com.forcitis.one.kernel.PermissionGrantMode.INDIVIDUAL,false),new com.forcitis.one.kernel.PermissionDeclaration(new com.forcitis.one.kernel.PermissionCode(\"platform.audit:synthetic-probe-record:export\"),com.forcitis.one.kernel.PermissionGrantMode.TWO_PERSON_INDIVIDUAL,false))";
        if(scenario.equals("duplicate"))permissions="java.util.List.of(new com.forcitis.one.kernel.PermissionDeclaration(new com.forcitis.one.kernel.PermissionCode(\"platform.audit:audit-entry:read\"),com.forcitis.one.kernel.PermissionGrantMode.TWO_PERSON_INDIVIDUAL,false),new com.forcitis.one.kernel.PermissionDeclaration(new com.forcitis.one.kernel.PermissionCode(\"platform.audit:audit-entry:read\"),com.forcitis.one.kernel.PermissionGrantMode.TWO_PERSON_INDIVIDUAL,false))";
        Path api=src.resolve("AdditionalP2FixtureApi.java"),bean=src.resolve("AdditionalP2FixtureBean.java");
        Files.writeString(api,"package com.forcitis.one.platform.audit.api; public interface AdditionalP2FixtureApi extends com.forcitis.one.kernel.PermissionContribution { default String owner(){return \""+owner+"\";} default java.util.List<com.forcitis.one.kernel.PermissionDeclaration> permissions(){return "+permissions+";} }");
        String impl=scenario.equals("annotation-only")?
            "@org.springframework.stereotype.Component final class AdditionalP2FixtureBean { @org.springframework.security.access.prepost.PreAuthorize(\"hasAuthority('"+code+"')\") public void fixtureRead(){} }":
            "@org.springframework.stereotype.Component final class AdditionalP2FixtureBean implements com.forcitis.one.platform.audit.api.AdditionalP2FixtureApi {}";
        Files.writeString(bean,"package com.forcitis.one.platform.audit.infrastructure.p2fixture; "+impl);
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var compiler=ToolProvider.getSystemJavaCompiler();assertTrue(compiler!=null,"SETUP BLOCKED: hosted JDK compiler");
        assertTrue(compiler.run(null,null,null,"-classpath",cp,"-d",classes.toString(),api.toString(),bean.toString())==0,"SETUP BLOCKED: poison/new declaration must compile");
        Path vocabulary=classes.resolve("permission-vocabulary/p2-fixture/additional.yaml");Files.createDirectories(vocabulary.getParent());
        String termOwner=scenario.equals("foreign-term")?"platform.numbering":"platform.audit";
        StringBuilder yaml=new StringBuilder("terms:\n");
        for(String term:scenario.equals("token-only")?List.of("synthetic","probe","record"):List.of("synthetic_probe_record")) {
            yaml.append("- id: ").append(term).append("\n  en: ").append(term).append("\n  snake: ").append(term)
                .append("\n  zh: 合成探针\n  scope: domain\n  kind: read_model\n  status: proposed\n  bc: ").append(termOwner)
                .append("\n  definition: synthetic fixture only\n  aliases: []\n  forbidden: []\n");
        }
        Files.writeString(vocabulary,yaml);
        LinkedHashSet<Path> physical=new LinkedHashSet<>();physical.add(classes.toRealPath());
        String[] tokens=cp.split(java.util.regex.Pattern.quote(File.pathSeparator),-1);
        for(int i=0;i<tokens.length;i++) {
            if(i==tokens.length-1 && tokens[i].isEmpty())continue;
            assertTrue(!tokens[i].isBlank() && !tokens[i].contains("*"),"SETUP BLOCKED: ambiguous CP");physical.add(Path.of(tokens[i]).toRealPath());
        }
        Path config=temporary.resolve(scenario+"-connection.properties"),report=temporary.resolve(scenario+"-safe.properties"),log=temporary.resolve(scenario+"-child.log");
        Properties properties=new Properties();postgres.register((name,supplier)->properties.setProperty(name,String.valueOf(supplier.get())));
        try(var stream=Files.newOutputStream(config)){properties.store(stream,"private synthetic clone; do not upload");}
        Files.setPosixFilePermissions(config,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        String childCp=physical.stream().map(Path::toString).collect(java.util.stream.Collectors.joining(File.pathSeparator));
        Process process=new ProcessBuilder(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Duser.timezone=UTC","-cp",childCp,
            PermissionP2AdditionalChild.class.getName(),scenario,config.toString(),report.toString())).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {assertTrue(process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS),"SETUP BLOCKED: bounded additional child");assertTrue(process.exitValue()==0,"SETUP BLOCKED: child crash not semantic red");}
        finally {if(process.isAlive()){process.destroyForcibly();process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS);}Files.deleteIfExists(config);}
        Properties result=new Properties();try(var stream=Files.newInputStream(report)){result.load(stream);}
        result.stringPropertyNames().stream().filter(k->k.startsWith("diagnostic.")).sorted().forEach(k->System.out.println("P2_SAFE_DIAGNOSTIC "+k+"="+result.getProperty(k)));
        return result;
    }
}
