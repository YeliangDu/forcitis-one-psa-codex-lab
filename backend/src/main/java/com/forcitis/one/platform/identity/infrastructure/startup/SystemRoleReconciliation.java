package com.forcitis.one.platform.identity.infrastructure.startup;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.identity.application.ReconcileSystemRole;
import com.forcitis.one.platform.identity.domain.DeclaredRole;
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
 * The declaration is {@link SystemRoleDeclaration#CURRENT} unless the context holds a SystemRoleDeclaration bean (the seam for a test of a
 * raised revision).
 */
@Component
class SystemRoleReconciliation implements SmartInitializingSingleton {

    private final ReconcileSystemRole reconcile;
    private final ObjectProvider<SystemRoleDeclaration> declaration;
    private final IdGenerator ids;

    SystemRoleReconciliation(ReconcileSystemRole reconcile, ObjectProvider<SystemRoleDeclaration> declaration, IdGenerator ids) {
        this.reconcile = reconcile;
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
    }
}
