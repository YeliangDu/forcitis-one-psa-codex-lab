package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.application.ForceUserLogout;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
class ForcedLogoutController {

    private final ForceUserLogout forceUserLogout;

    ForcedLogoutController(ForceUserLogout forceUserLogout) {
        this.forceUserLogout = forceUserLogout;
    }

    @PostMapping(path = "/user-accounts/{userId}:force-logout")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_IDENTITY_SESSION_DELETE + "')")
    ResponseEntity<Void> forceLogout(@PathVariable long userId, @RequestBody ForcedLogoutRequest request) {
        forceUserLogout.execute(AccountPath.user(userId), request.reason());
        return ResponseEntity.noContent().build();
    }
}
