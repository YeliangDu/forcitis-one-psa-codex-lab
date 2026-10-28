package com.forcitis.one.platform.identity.infrastructure.startup;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.identity.application.ReconcileSystemRole;
import com.forcitis.one.platform.identity.domain.DeclaredRole;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.SystemRoleDeclaration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * ADR-0034 decision 1 item 7: the system roles are reconciled while the context refreshes, after every singleton exists (Flyway has run) and
 * before any lifecycle bean starts, so before the web server, the scheduler (event republication and every job), the event listeners and
 * any ApplicationRunner. Each role is its own transaction; a failure stops the start. Several instances may start at once: the role row
 * lock and the unique keys make the second one find the first one's versions.
 * <p>
 * Then the current version of every role, custom ones included, is read once (decision 1 item 3: in production the start-up reconciliation
 * checks the permission codes). The read goes through the one role read of the repository, which leaves out a code the catalogue does not
 * know or has deprecated and writes one ALERT per such code with the role, the version and the code; it only alerts, the start goes on.
 * <p>
 * The declaration is {@link SystemRoleDeclaration#CURRENT} unless the context holds a SystemRoleDeclaration bean (the seam for a test of a
 * raised revision).
 */
@Component
class SystemRoleReconciliation implements SmartInitializingSingleton {

    private final ReconcileSystemRole reconcile;
    private final RoleRepository roles;
    private final ObjectProvider<SystemRoleDeclaration> declaration;
    private final IdGenerator ids;

    SystemRoleReconciliation(ReconcileSystemRole reconcile, RoleRepository roles, ObjectProvider<SystemRoleDeclaration> declaration, IdGenerator ids) {
        this.reconcile = reconcile;
        this.roles = roles;
        this.declaration = declaration;
        this.ids = ids;
    }

    @Override
    public void afterSingletonsInstantiated() {
        SystemRoleDeclaration declared = declaration.getIfAvailable(() -> SystemRoleDeclaration.CURRENT);
        try (WorkContext.Scope scope = WorkContext.enter(ids.next(), null, "platform.identity.system-role-declaration")) {
            for (DeclaredRole role : declared.roles()) {
                reconcile.execute(role);
            }
        }
        roles.findAll(); // read for its alerts only: the roles themselves are not needed here
    }
}
