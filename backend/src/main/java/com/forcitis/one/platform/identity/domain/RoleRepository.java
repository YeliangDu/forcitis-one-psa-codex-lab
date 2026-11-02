package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Roles with their current version (ADR-0034 decision 1): a role whose last version was closed (a deleted role) is not found and not
 * listed, but its row stays and its code is never used again. Every write runs in the caller's transaction and writes the audit entry of
 * the version it opens or closes in the same transaction.
 */
public interface RoleRepository {

    Optional<Role> findById(RoleId roleId);

    Optional<Role> findByCode(RoleCode code);

    List<Role> findAll();

    /** Those of the versions that are still the current version of their role (a replaced or closed version is not). */
    Set<RoleVersionId> currentVersions(Collection<RoleVersionId> versions);

    /**
     * Writes the role row and its version 1. Throws RoleRuleException(DUPLICATE_CODE) when any role, deleted ones included, has the code
     * (any case), and DUPLICATE_NAME when another role's version holds the name at the start of version 1; returns the role.
     */
    Role insert(Role unsaved, RoleChange change);

    /**
     * Applies only to the version the role was loaded with, otherwise a DomainException (CONFLICT). Under the row lock of the role: a changed
     * definition closes the current version and opens the next one at greatest(:now, current start, latest end); an unchanged one adds none.
     */
    void update(Role role, RoleChange change);

    /** Closes the last version (ADR-0034 decision 1 item 5); refused with ROLE_IN_USE while an account holds the role. */
    void delete(Role role, UserId deletedBy, String reason);

    /**
     * Reconciles one declared system role under its row lock (ADR-0034 decision 1 item 7): writes the role and a SEED version when it has
     * none, closes the current version and opens a SEED one when the declaration is newer, and writes nothing otherwise.
     */
    DeclarationResult saveDeclared(DeclaredRole declared);
}
