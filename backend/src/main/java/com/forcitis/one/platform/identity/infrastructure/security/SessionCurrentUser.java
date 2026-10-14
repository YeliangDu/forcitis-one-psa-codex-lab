package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class SessionCurrentUser implements CurrentUser {

    @Override
    public UserId userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user");
        }
        return new UserId(Long.parseLong(authentication.getName()));
    }

    @Override
    public LegalEntityId legalEntityId() {
        return new LegalEntityId(1L);
    }
}
