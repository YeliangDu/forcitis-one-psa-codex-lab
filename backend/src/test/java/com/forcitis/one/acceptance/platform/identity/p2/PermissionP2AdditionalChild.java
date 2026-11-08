package com.forcitis.one.acceptance.platform.identity.p2;

import static org.junit.jupiter.api.Assertions.*;

import com.forcitis.one.OneApplication;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.identity.api.*;
import com.forcitis.one.platform.identity.application.*;
import com.forcitis.one.platform.identity.domain.*;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/** Separate fresh JVM; real provider, application commands/repositories and PostgreSQL. */
public final class PermissionP2AdditionalChild {
    static final String READ="platform.audit:synthetic-probe-record:read",SENSITIVE="platform.audit:synthetic-probe-record:export";
    static final String UNKNOWN="platform.audit:synthetic-missing-record:read";
    static final List<String> RETIRED=List.of("organization.workforce:sync-run:read","organization.workforce:sync-run:execute","organization.workforce:org-reconciliation:execute");

    public static void main(String[] args) throws Exception {
        String scenario=args[0];Properties report=new Properties();
        for(String key:List.of("importerIncluded","rejected","actorsValid","springOk","ordinaryStored","twoPersonAwaiting","unknownRetiredRefused","filterExact"))report.setProperty(key,"false");
        try {
            boolean imported=new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.forcitis.one")
                .stream().anyMatch(type->type.getName().equals("com.forcitis.one.platform.audit.infrastructure.p2fixture.AdditionalP2FixtureBean"));
            report.setProperty("importerIncluded",Boolean.toString(imported));
            if(!scenario.equals("persistence")) {
                try {PermissionCatalogue.fromClasspath();}
                catch(IllegalArgumentException rejection){report.setProperty("rejected","true");}
                return;
            }
            report.setProperty("actorsValid",Boolean.toString(PermissionP2Child.actorResourceEvidence(report)));
            if(!"true".equals(report.getProperty("actorsValid")))return;
            Properties supplied=new Properties();try(var stream=Files.newInputStream(Path.of(args[1]))){supplied.load(stream);}
            Set<String> keys=Set.of("spring.datasource.url","spring.datasource.username","spring.datasource.password","spring.flyway.url","spring.flyway.user","spring.flyway.password");
            if(!keys.containsAll(supplied.stringPropertyNames()))throw new IllegalArgumentException("unexpected fixture property");
            Map<String,Object> properties=new TreeMap<>();supplied.forEach((key,value)->properties.put(key.toString(),value));
            properties.put("server.address","127.0.0.1");properties.put("server.port",0);properties.put("spring.main.web-application-type","servlet");
            properties.put("flowable.database-schema-update",true);properties.put("spring.flyway.create-schemas",true);properties.put("flowable.async-executor-activate",false);
            for(String key:List.of("forcitis.platform.scheduling.jobs-enabled","forcitis.platform.integration.event-publication.jobs-enabled","forcitis.platform.identity.emergency.check.enabled",
                "forcitis.cost.timesheet.consumed-events.enabled","forcitis.platform.approval.process-archive.enabled","forcitis.platform.integration.outbound-purge.enabled",
                "forcitis.platform.integration.archive-store.enabled","forcitis.platform.audit.archive.enabled","forcitis.platform.approval.process-administrator-required"))properties.put(key,false);
            try(var context=new SpringApplicationBuilder(OneApplication.class,PermissionP2Child.ExternalTransportOnly.class).web(WebApplicationType.SERVLET)
                    .initializers(app->app.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("p2-additional-fixture",Map.copyOf(properties)))).run()) {
                report.setProperty("springOk","true");
                var catalogue=context.getBean(PermissionCatalogue.class);
                assertTrue(catalogue.isLive(READ));assertTrue(catalogue.isLive(SENSITIVE));
                assertTrue(SensitivePermissions.CODES.contains(SENSITIVE));assertFalse(SensitivePermissions.CODES.contains(READ));
                IdGenerator ids=context.getBean(IdGenerator.class);TransactionTemplate tx=context.getBean(TransactionTemplate.class);
                AccountRepository accounts=context.getBean(AccountRepository.class);RoleRepository roles=context.getBean(RoleRepository.class);
                UserId actor=new UserId(ids.next());RoleId admin=roles.findByCode(new RoleCode("super_admin")).orElseThrow().roleId();
                // Synthetic identity-only bootstrap fixture, not the role change path under test.
                // No workforce rows/system-role definitions/revisions or production grants changed.
                try(var acting=WorkContext.enter(actor.value(),null,"test.identity.p2.additional")) {
                    tx.executeWithoutResult(status->{
                        assertTrue(accounts.insertIfAbsent(UserAccount.open(actor,new EmployeeId(ids.next()),Set.of(),AccountOpeningRoute.OPERATIONS)));
                        accounts.saveGrants(actor,new GrantChanges(List.of(new GrantOpening(new AccountRoleGrantId(ids.next()),admin,GrantChannel.OPERATIONS,
                            null,null,new AuditEntryId(ids.next()))),List.of()));
                    });
                    assertTrue(accounts.findEnabledRoles(actor).stream().anyMatch(Role::administrative));
                    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(actor.value().toString(),null,"ROLE_ADMINISTRATOR"));
                    CreateRole create=context.getBean(CreateRole.class);GetRole get=context.getBean(GetRole.class);
                    String code="p2_fixture_"+ids.next().toString().substring(24);
                    AdminOutcome<RoleView> ordinary=create.execute(new SaveRoleCommand(code,"Synthetic P2 ordinary "+code,true,AccessScopeKind.SELF,Set.of(READ),null));
                    AdminOutcome.Done<?> done=assertInstanceOf(AdminOutcome.Done.class,ordinary);
                    RoleView view=assertInstanceOf(RoleView.class,done.value());
                    RoleView persisted=get.execute(view.roleId()).orElseThrow();
                    assertEquals(List.of(READ),persisted.permissionCodes());
                    JdbcClient jdbc=context.getBean(JdbcClient.class);
                    String[] stored=jdbc.sql("select permission_codes from platform_identity.role_versions where role_id=:id and effective_to_at is null")
                        .param("id",view.roleId().value()).query((rs,n)->(String[])rs.getArray(1).getArray()).single();
                    assertEquals(List.of(READ),List.of(stored));report.setProperty("ordinaryStored","true");
                    String sensitiveCode="p2_sensitive_"+ids.next().toString().substring(24);
                    AdminOutcome<RoleView> sensitive=create.execute(new SaveRoleCommand(sensitiveCode,"Synthetic P2 sensitive "+sensitiveCode,true,AccessScopeKind.SELF,Set.of(SENSITIVE),null));
                    AdminOutcome.Awaiting<?> awaiting=assertInstanceOf(AdminOutcome.Awaiting.class,sensitive);
                    assertTrue(roles.findByCode(new RoleCode(sensitiveCode)).isEmpty(),"ordinary sensitive request must not insert role");
                    assertEquals(1,jdbc.sql("select count(*) from platform_identity.authorization_requests where request_id=:id")
                        .param("id",java.util.UUID.fromString(awaiting.requestId())).query(Integer.class).single());
                    report.setProperty("twoPersonAwaiting","true");
                    for(String invalid:java.util.stream.Stream.concat(java.util.stream.Stream.of(UNKNOWN),RETIRED.stream()).toList()) {
                        String invalidCode="p2_invalid_"+ids.next().toString().substring(24);
                        assertInstanceOf(AdminOutcome.Refused.class,create.execute(new SaveRoleCommand(invalidCode,"Synthetic invalid "+invalidCode,true,AccessScopeKind.SELF,Set.of(invalid),null)));
                        assertTrue(roles.findByCode(new RoleCode(invalidCode)).isEmpty());
                    }
                    report.setProperty("unknownRetiredRefused","true");
                    // Existing own-schema legacy-corruption fixture pattern: append a new role
                    // version with unknown/retired metadata to exercise ACTUAL read filtering.
                    tx.executeWithoutResult(status->{
                        jdbc.sql("update platform_identity.role_versions set effective_to_at=now(),updated_at=now() where role_id=:id and effective_to_at is null")
                            .param("id",view.roleId().value()).update();
                        String[] codes={READ,UNKNOWN,RETIRED.get(0),RETIRED.get(1),RETIRED.get(2)};
                        jdbc.sql("""
                            insert into platform_identity.role_versions(role_version_id,role_id,version_number,effective_from_at,effective_to_at,
                            role_name,role_status,access_scope_kind,permission_codes,change_channel_kind,recorded_by,change_reason,authorization_request_id,
                            declaration_revision_number,version_audit_entry_id,closed_by,closure_reason,closure_audit_entry_id,created_at,updated_at)
                            values (:version,:role,2,now(),null,:name,'ENABLED','SELF',cast(:codes as text[]),'MANUAL',:actor,
                            'synthetic P2 read-filter fixture',null,null,:audit,null,null,null,now(),now())
                            """).param("version",ids.next()).param("role",view.roleId().value()).param("name",view.name())
                            .param("codes","{"+String.join(",",codes)+"}").param("actor",actor.value()).param("audit",ids.next()).update();
                    });
                    assertEquals(List.of(READ),get.execute(view.roleId()).orElseThrow().permissionCodes());
                    report.setProperty("filterExact","true");
                } finally {SecurityContextHolder.clearContext();}
            }
        } catch(RuntimeException|LinkageError|AssertionError failure) {PermissionP2Child.diagnose(report,"additional",failure);}
        finally {try(var stream=Files.newOutputStream(Path.of(args[2]))){report.store(stream,"safe synthetic P2 additional results");}}
    }
}
