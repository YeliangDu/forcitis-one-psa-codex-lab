package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    Optional<Role> findById(RoleId roleId);

    Optional<Role> findByCode(RoleCode code);

    List<Role> findAll();

    /** Throws RoleRuleException(DUPLICATE_CODE / DUPLICATE_NAME) on a clash. */
    Role insert(Role role);

    void update(Role role);

    void delete(RoleId roleId);
}
