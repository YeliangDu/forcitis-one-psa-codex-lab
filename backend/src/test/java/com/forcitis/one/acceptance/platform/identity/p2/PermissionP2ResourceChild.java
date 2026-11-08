package com.forcitis.one.acceptance.platform.identity.p2;

import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.PermissionCatalogue;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Fresh JVM per term variant, real shared production provider, no Spring or SQL/grants needed. */
public final class PermissionP2ResourceChild {
    public static void main(String[] args)throws Exception{
        Properties report=new Properties();report.setProperty("accepted","false");report.setProperty("illegalDeclaration","false");
        boolean imported=new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.forcitis.one")
            .stream().anyMatch(type->type.getName().equals("com.forcitis.one.platform.audit.infrastructure.p2fixture.ResourceMatrixBean"));
        report.setProperty("imported",Boolean.toString(imported));
        try{
            PermissionCatalogue catalogue=PermissionCatalogue.fromClasspath();
            report.setProperty("accepted",Boolean.toString(catalogue.isLive(args[0])));
            boolean notLegacy=java.util.Arrays.stream(Permission.values()).noneMatch(permission->permission.code().equals(args[0]));
            for(var field:Permission.Codes.class.getFields())if(args[0].equals(field.get(null)))notLegacy=false;
            report.setProperty("notLegacy",Boolean.toString(notLegacy));
        }catch(IllegalArgumentException rejection){report.setProperty("illegalDeclaration","true");}
        try(var stream=Files.newOutputStream(Path.of(args[1]))){report.store(stream,"safe synthetic resource matrix");}
    }
}
