package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    Optional<Role> findById(RoleId roleId);

    Optional<Role> findByCode(RoleCode code);

    List<Role> findAll();

    /** Throws RoleRuleException(DUPLICATE_CODE / DUPLICATE_NAME) on a clash; returns the role with its id and its creation event recorded. */
    Role insert(Role unsaved);

    /** Applies only to the status and version the role was loaded with; otherwise a DomainException (CONFLICT). */
    void update(Role role);

    void delete(Role role);
}
