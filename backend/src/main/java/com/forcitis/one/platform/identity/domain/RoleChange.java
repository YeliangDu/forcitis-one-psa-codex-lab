package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.ValueObject;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Who and what writes a role version (ADR-0034 decision 1 item 2; ck_role_versions_seed, ck_role_versions_manual): a MANUAL change names
 * the person and, when a second person confirmed it, the authorization request; a SEED change names the declaration revision and nobody.
 * The reason is a fixed phrase the application writes (ADR-0034-IMPL ruling A-5), never text typed by a person.
 */
@ValueObject
public record RoleChange(ChangeChannel channel, @Nullable UserId recordedBy, String reason, @Nullable AuthorizationRequestId authorizationRequestId,
                         @Nullable Integer declarationRevisionNumber) {

    public RoleChange {
        Objects.requireNonNull(channel, "channel");
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("a role change has a reason of 1 to 500 characters");
        }
        if ((channel == ChangeChannel.SEED) != (declarationRevisionNumber != null)) {
            throw new IllegalArgumentException("a declaration revision belongs to a SEED change and only to one");
        }
        if (channel == ChangeChannel.SEED && recordedBy != null) {
            throw new IllegalArgumentException("a SEED change is written by nobody");
        }
        if (channel == ChangeChannel.MANUAL && recordedBy == null) {
            throw new IllegalArgumentException("a MANUAL change names the person who made it");
        }
        if (declarationRevisionNumber != null && declarationRevisionNumber < 1) {
            throw new IllegalArgumentException("a declaration revision starts at 1");
        }
    }

    /** A change made in the role management; with a request when it waited for a second person. */
    public static RoleChange manual(UserId recordedBy, String reason, @Nullable AuthorizationRequestId authorizationRequestId) {
        String why = authorizationRequestId == null ? reason : reason + ", confirmed authorization request " + authorizationRequestId.value();
        return new RoleChange(ChangeChannel.MANUAL, Objects.requireNonNull(recordedBy, "recordedBy"), why, authorizationRequestId, null);
    }

    /** A version the start-up reconciliation writes from the system role declaration. */
    public static RoleChange seed(int declarationRevisionNumber) {
        return new RoleChange(ChangeChannel.SEED, null, "system role declaration " + SystemRoleDeclaration.class.getSimpleName() + " revision "
                + declarationRevisionNumber, null, declarationRevisionNumber);
    }
}
