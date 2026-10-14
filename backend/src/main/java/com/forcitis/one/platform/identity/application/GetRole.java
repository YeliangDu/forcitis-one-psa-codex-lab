package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.domain.RoleId;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetRole {

    private final RoleRepository roles;

    GetRole(RoleRepository roles) {
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public Optional<RoleView> execute(long roleId) {
        if (roleId <= 0) {
            return Optional.empty();
        }
        return roles.findById(new RoleId(roleId)).map(RoleView::of);
    }
}
