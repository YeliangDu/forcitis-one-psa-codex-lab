package com.forcitis.one.acceptance.platform.identity.role_versioning;

import com.forcitis.one.OneApplication;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.Permission.Codes;
import com.forcitis.one.platform.identity.api.SessionRoles;
import com.forcitis.one.platform.identity.domain.DeclaredRole;
import com.forcitis.one.platform.identity.domain.RoleDefinition;
import com.forcitis.one.platform.identity.domain.RoleStatus;
import com.forcitis.one.platform.identity.domain.SystemRoleDeclaration;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * SystemRoleDeclarationLauncher with one change: the employee role is declared at revision 2 with another definition (ADR-0034 decision 1
 * item 7: 声明修订号提高 → 关闭当前版本、追加 SEED 版本; card 0034-G8-01). The other five roles keep the code's own declaration.
 * <p>
 * The seam is the one the implementation offers and documents: a SystemRoleDeclaration bean in the context replaces
 * SystemRoleDeclaration.CURRENT (SystemRoleReconciliation). Neither the card nor the ADR defines a seam, so this is the only coupling to
 * the implementation; it is registered by an initializer, never by component scan, so no other test context sees it.
 */
public final class RaisedDeclarationLauncher {

    static final String EMPLOYEE = "019a0000-0000-7000-8000-000000000102";
    static final String AUDIT_PROBE = "ROLE-AUDIT-PROBE";
    static final String OPENED = "platform.identity.role-version.opened";
    static final int RAISED_REVISION = 2;
    static final String RAISED_NAME = "员工（修订2）";
    static final AccessScopeKind RAISED_SCOPE = AccessScopeKind.SELF;
    /** Differs from the seed: platform.file:file:create is gone, platform.numbering:legacy-number:read is new. */
    static final Set<String> RAISED_CODES = Set.of(Codes.PLATFORM_APPROVAL_TASK_READ, Codes.PLATFORM_APPROVAL_TASK_APPROVE,
            Codes.PLATFORM_AUDIT_OWN_LOGIN_RECORD_READ, Codes.PLATFORM_FILE_FILE_READ, Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ);

    private RaisedDeclarationLauncher() {
    }

    static SystemRoleDeclaration raised() {
        List<DeclaredRole> roles = new ArrayList<>();
        for (DeclaredRole role : SystemRoleDeclaration.CURRENT.roles()) {
            if (role.roleId().value().toString().equals(EMPLOYEE)) {
                roles.add(new DeclaredRole(role.roleId(), role.code(), role.administrative(), RAISED_REVISION,
                        new RoleDefinition(RAISED_NAME, RoleStatus.ENABLED, RAISED_SCOPE, RAISED_CODES)));
            } else {
                roles.add(role);
            }
        }
        return new SystemRoleDeclaration(roles);
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(OneApplication.class);
        application.addInitializers(context -> {
            GenericApplicationContext generic = (GenericApplicationContext) context;
            generic.registerBean("raisedSystemRoleDeclaration", SystemRoleDeclaration.class, RaisedDeclarationLauncher::raised);
            generic.registerBean("roleDeclarationLifecycleProbe", SmartLifecycle.class, () -> new SystemRoleDeclarationLauncher.LifecycleProbe(generic));
            generic.registerBean("roleDeclarationRunnerProbe", ApplicationRunner.class, () -> new AuditThenRunnerProbe(generic));
        });
        application.run(args);
    }

    /**
     * Before the runner probe line (the test stops the process on it), prints each audit entry of a role version opened, as the audit module's
     * own HTTP query answers it to a caller who holds only the audit-read permission: one line "ROLE-AUDIT-PROBE entryId action". The audit
     * table itself is never read by SQL from this module's tests (TestIsolationGuardTest).
     */
    static final class AuditThenRunnerProbe implements ApplicationRunner, Ordered {
        private final GenericApplicationContext context;

        AuditThenRunnerProbe(GenericApplicationContext context) {
            this.context = context;
        }

        @Override
        public void run(ApplicationArguments args) {
            try {
                MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
                MockHttpServletResponse found = mvc.perform(MockMvcRequestBuilders.get("/api/v1/platform/audit-entries")
                        .param("actionPrefix", OPENED).param("limit", "200")
                        .with(SecurityMockMvcRequestPostProcessors.user("01900000-0000-7000-8000-000000000002")
                                .authorities(new SimpleGrantedAuthority("ROLE_" + SessionRoles.ORDINARY), new SimpleGrantedAuthority(Codes.PLATFORM_AUDIT_ENTRY_READ))))
                        .andReturn().getResponse();
                if (found.getStatus() != 200) {
                    System.out.println(AUDIT_PROBE + " failed: status " + found.getStatus() + " " + found.getContentAsString());
                } else {
                    List<String> ids = JsonPath.read(found.getContentAsString(), "$.items[*].entryId");
                    List<String> actions = JsonPath.read(found.getContentAsString(), "$.items[*].action");
                    for (int i = 0; i < ids.size(); i++) {
                        System.out.println(AUDIT_PROBE + " " + ids.get(i) + " " + actions.get(i));
                    }
                }
            } catch (Exception | LinkageError e) {
                System.out.println(AUDIT_PROBE + " failed: " + e);
            }
            new SystemRoleDeclarationLauncher.RunnerProbe(context).run(args);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
