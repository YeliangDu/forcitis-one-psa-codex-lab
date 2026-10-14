package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    Optional<Role> findById(RoleId roleId);

    Optional<Role> findByCode(RoleCode code);

    List<Role> findAll();

    /** Stores a new role; throws RoleRuleException(DUPLICATE_CODE / DUPLICATE_NAME) on a clash. */
    Role insert(Role role);

    /** Replaces name, status, scope and the whole permission set (RULE-346); same duplicate rules as insert. */
    void update(Role role);

    /** Also removes the role's account links and permission links (RULE-345). */
    void delete(RoleId roleId);
}
