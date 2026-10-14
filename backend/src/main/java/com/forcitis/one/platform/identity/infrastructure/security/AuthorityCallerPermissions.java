package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.kernel.CallerPermissions;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class AuthorityCallerPermissions implements CallerPermissions {

    @Override
    public boolean holds(String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permissionCode));
    }
}
