package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.domain.RoleRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListRoles {

    private final RoleRepository roles;

    ListRoles(RoleRepository roles) {
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public List<RoleView> execute() {
        return roles.findAll().stream().map(RoleView::of).sorted(Comparator.comparing(RoleView::code)).toList();
    }
}
