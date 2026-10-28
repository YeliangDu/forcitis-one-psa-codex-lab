package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.ValueObject;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One system role as the code declares it (ADR-0034 decision 1 item 7): a fixed role id, its identity (code, administrative marker) and
 * the definition its current version must hold, under a revision number that only grows.
 */
@ValueObject
public record DeclaredRole(RoleId roleId, RoleCode code, boolean administrative, int revisionNumber, RoleDefinition definition) {

    public DeclaredRole {
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(definition, "definition");
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("a declaration revision starts at 1");
        }
    }

    /**
     * What the reconciliation does with this role, given what the database holds: the current version's definition and its declaration
     * revision (both empty when there is no current version, the revision also when the current version is not a SEED one), and the
     * highest declaration revision any version of the role was written under (empty when none ever was).
     */
    public DeclarationOutcome outcome(@Nullable RoleDefinition current, @Nullable Integer currentRevision, @Nullable Integer highestRevision) {
        if (highestRevision == null) {
            return current == null ? DeclarationOutcome.CREATED : DeclarationOutcome.REVISED;
        }
        if (revisionNumber < highestRevision) {
            return DeclarationOutcome.OLDER_IN_CODE;
        }
        if (revisionNumber > highestRevision) {
            return DeclarationOutcome.REVISED;
        }
        if (current == null || currentRevision == null) {
            return DeclarationOutcome.REVISED;
        }
        if (currentRevision != revisionNumber) {
            return DeclarationOutcome.REVISED;
        }
        return current.equals(definition) ? DeclarationOutcome.UNCHANGED : DeclarationOutcome.CONFLICTING;
    }
}
