package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.domain.DeclarationOutcome;
import com.forcitis.one.platform.identity.domain.DeclarationResult;
import com.forcitis.one.platform.identity.domain.DeclaredRole;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles one declared system role with the database, in its own transaction (ADR-0034 decision 1 item 7; card 0034-G8-01): a missing
 * role is created, a higher declaration revision opens a SEED version, the same one changes nothing. A database that holds a higher revision
 * than the code is an alert and keeps what it has; the same revision with another definition stops the start, with nothing written.
 */
@Service
public class ReconcileSystemRole {

    private static final Logger LOG = LoggerFactory.getLogger(ReconcileSystemRole.class);

    private final RoleRepository roles;

    ReconcileSystemRole(RoleRepository roles) {
        this.roles = roles;
    }

    @Transactional
    public DeclarationOutcome execute(DeclaredRole declared) {
        DeclarationResult result = roles.saveDeclared(declared);
        switch (result.outcome()) {
            case OLDER_IN_CODE -> LOG.error("ALERT platform.identity system role {}: the declaration revision {} is lower than the database revision {};"
                    + " the database keeps its version and nothing was written", declared.roleId().value(), declared.revisionNumber(), result.storedRevisionNumber());
            case CONFLICTING -> {
                LOG.error("ALERT platform.identity system role {}: the declaration revision {} equals the database revision {} with another definition,"
                        + " or the role row has another identity; the start stops and nothing was written", declared.roleId().value(),
                        declared.revisionNumber(), result.storedRevisionNumber());
                throw new IllegalStateException("system role " + declared.roleId().value() + " does not match its declaration revision "
                        + declared.revisionNumber() + " (ADR-0034 decision 1 item 7)");
            }
            case CREATED, REVISED -> LOG.info("platform.identity system role {} written at declaration revision {} ({})", declared.roleId().value(),
                    declared.revisionNumber(), result.outcome());
            case UNCHANGED -> LOG.debug("platform.identity system role {} is at declaration revision {}", declared.roleId().value(), declared.revisionNumber());
        }
        return result.outcome();
    }
}
