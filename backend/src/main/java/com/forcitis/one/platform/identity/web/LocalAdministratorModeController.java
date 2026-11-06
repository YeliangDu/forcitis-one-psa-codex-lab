package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.LocalEnterAdministratorMode;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Card #1499: the administrator mode of a developer's own machine, without the WeCom proof. Exists only in the profile local; in every other profile the path is not
 * mapped. Who asks is the session, which must be one of the local test login and of an account that holds super_admin.
 */
@RestController
@Profile("local")
@RequestMapping("/api/v1/platform")
class LocalAdministratorModeController {

    private final LocalEnterAdministratorMode enter;

    LocalAdministratorModeController(LocalEnterAdministratorMode enter) {
        this.enter = enter;
    }

    @PostMapping(path = "/sessions:enter-local-administrator-mode")
    @PreAuthorize("isAuthenticated()")
    AdministratorModeResponse enter() {
        return new AdministratorModeResponse(enter.execute());
    }
}
