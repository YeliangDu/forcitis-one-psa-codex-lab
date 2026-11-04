package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
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
     * Locks the role rows the versions belong to, in creation order (created_at, then role id) (FOR UPDATE when the caller is about to write them, else FOR SHARE), so what
     * {@link #currentVersions} reads next cannot change before the transaction ends.
     */
    void lockRolesOf(Collection<RoleVersionId> versions, boolean forWrite);

    /** The roles whose current version has scope CUSTOM_ORG_UNITS and lists the org unit, enabled or not. */
    List<RoleId> currentRolesListing(OrgUnitId orgUnit);

    /** Whether the version lists the org unit (a version of any age, open or closed). */
    boolean versionLists(RoleVersionId version, OrgUnitId orgUnit);

    /** The audit entry written in the same transaction as the version. */
    AuditEntryId versionAuditEntry(RoleVersionId version);

    /** True when a version of some role was written by the confirmed request: its change is already applied. */
    boolean versionWrittenBy(AuthorizationRequestId requestId);

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
