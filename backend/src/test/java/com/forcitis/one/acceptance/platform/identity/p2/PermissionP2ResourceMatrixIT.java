package com.forcitis.one.acceptance.platform.identity.p2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** #31 additive exact full-resource semantics through real plain production provider. */
class PermissionP2ResourceMatrixIT {
    @TempDir Path temporary;
    static final String CODE="platform.audit:synthetic-probe-record:read";

    @Test void allowedResourceKindsAndApprovedOrProposedStatusesRemainAvailable()throws Exception{
        for(String kind:List.of("aggregate","entity","value_object","read_model"))for(String status:List.of("approved","proposed"))
            verify(kind+"-"+status,term("synthetic_probe_record","synthetic_probe_record",kind,status,"domain","same fixture concept"),null,CODE,true);
    }

    @Test void forbiddenDeprecatedWrongKindAndWrongScopeCannotAuthorizeNewCode()throws Exception{
        for(String status:List.of("forbidden","deprecated","unknown"))
            verify("status-"+status,term("synthetic_probe_record","synthetic_probe_record","read_model",status,"domain","same fixture concept"),null,CODE,false);
        for(String kind:List.of("event","field","unknown"))
            verify("kind-"+kind,term("synthetic_probe_record","synthetic_probe_record",kind,"proposed","domain","same fixture concept"),null,CODE,false);
        for(String scope:List.of("common","infrastructure"))
            verify("scope-"+scope,term("synthetic_probe_record","synthetic_probe_record","read_model","proposed",scope,"same fixture concept"),null,CODE,false);
    }

    @Test void onlyIdenticalStableConceptMayDeduplicateNeverAmbiguousAliasesOrSingularization()throws Exception{
        String registered=term("synthetic_probe_record","synthetic_probe_record","read_model","proposed","domain","same fixture concept");
        verify("identical-concept",registered,registered,CODE,true);
        verify("conflicting-id",registered,term("different_concept","synthetic_probe_record","read_model","proposed","domain","same fixture concept"),CODE,false);
        verify("changed-metadata",registered,term("synthetic_probe_record","synthetic_probe_record","read_model","proposed","domain","different fixture meaning"),CODE,false);
        String aliasOnly=term("different_resource","different_resource","read_model","proposed","domain","same fixture concept")
            .replace("aliases: []","aliases: [SyntheticProbeRecord]")+"common_words: [synthetic, probe, record]\n";
        verify("alias-only",aliasOnly,null,CODE,false);
        verify("plural-not-exact",registered,null,"platform.audit:synthetic-probe-records:read",false);
    }

    String term(String id,String snake,String kind,String status,String scope,String definition){
        return "terms:\n- id: "+id+"\n  en: SyntheticProbeRecord\n  zh: 合成权限探针\n  snake: "+snake+
            "\n  kind: "+kind+"\n  status: "+status+"\n  scope: "+scope+"\n  bc: platform.audit\n  definition: "+definition+"\n  aliases: []\n  forbidden: []\n";
    }

    void verify(String scenario,String first,String second,String code,boolean accepted)throws Exception{
        Path area=temporary.resolve(scenario),classes=area.resolve("p2-fixture/classes"),src=area.resolve("source");Files.createDirectories(classes);Files.createDirectories(src);
        Path api=src.resolve("ResourceMatrixApi.java"),bean=src.resolve("ResourceMatrixBean.java");
        Files.writeString(api,"package com.forcitis.one.platform.audit.api; public interface ResourceMatrixApi extends com.forcitis.one.kernel.PermissionContribution { default String owner(){return \"platform.audit\";} default java.util.List<com.forcitis.one.kernel.PermissionDeclaration> permissions(){return java.util.List.of(new com.forcitis.one.kernel.PermissionDeclaration(new com.forcitis.one.kernel.PermissionCode(\""+code+"\"),com.forcitis.one.kernel.PermissionGrantMode.INDIVIDUAL,false));} }");
        Files.writeString(bean,"package com.forcitis.one.platform.audit.infrastructure.p2fixture; @org.springframework.stereotype.Component final class ResourceMatrixBean implements com.forcitis.one.platform.audit.api.ResourceMatrixApi {}");
        String rawCp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var compiler=ToolProvider.getSystemJavaCompiler();assertTrue(compiler!=null,"SETUP BLOCKED: hosted compiler");
        assertTrue(compiler.run(null,null,null,"-classpath",rawCp,"-d",classes.toString(),api.toString(),bean.toString())==0,"SETUP BLOCKED: resource fixture compile");
        Path resources=classes.resolve("permission-vocabulary/p2-fixture");Files.createDirectories(resources);Files.writeString(resources.resolve("first.yaml"),first);
        if(second!=null)Files.writeString(resources.resolve("second.yaml"),second);
        LinkedHashSet<Path> cp=new LinkedHashSet<>();cp.add(classes.toRealPath());
        String[] tokens=rawCp.split(java.util.regex.Pattern.quote(File.pathSeparator),-1);
        for(int i=0;i<tokens.length;i++){
            if(i==tokens.length-1 && tokens[i].isEmpty())continue;
            assertTrue(!tokens[i].isBlank()&&!tokens[i].contains("*"),"SETUP BLOCKED: CP token");cp.add(Path.of(tokens[i]).toRealPath());
        }
        Path report=area.resolve("safe.properties"),log=area.resolve("child.log");
        Process child=new ProcessBuilder(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Duser.timezone=UTC","-cp",
            cp.stream().map(Path::toString).collect(java.util.stream.Collectors.joining(File.pathSeparator)),PermissionP2ResourceChild.class.getName(),code,report.toString()))
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{assertTrue(child.waitFor(60,java.util.concurrent.TimeUnit.SECONDS),"SETUP BLOCKED: bounded resource child");assertTrue(child.exitValue()==0,"SETUP BLOCKED: child crash not semantic reject");}
        finally{if(child.isAlive()){child.destroyForcibly();child.waitFor(5,java.util.concurrent.TimeUnit.SECONDS);}}
        Properties result=new Properties();try(var stream=Files.newInputStream(report)){result.load(stream);}
        assertTrue("true".equals(result.getProperty("imported")),"SETUP BLOCKED: actual production importer inclusion");
        assertTrue(Boolean.toString(accepted).equals(result.getProperty("accepted")),"full owning resource rule: "+scenario);
        if(accepted)assertTrue("true".equals(result.getProperty("notLegacy")),"genuinely new code must not acquire a facade alias");
        else assertTrue("true".equals(result.getProperty("illegalDeclaration")),"expected exact production declaration rejection");
    }
}
