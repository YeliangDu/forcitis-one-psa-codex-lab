package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.EnterAdministratorMode;
import com.forcitis.one.platform.identity.application.ExitAdministratorMode;
import com.forcitis.one.platform.identity.application.WecomLoginCommand;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Who asks is the session; the proof is a fresh WeCom code of the same person. */
@RestController
@RequestMapping("/api/v1/platform")
class AdministratorModeController {

    private final EnterAdministratorMode enter;
    private final ExitAdministratorMode exit;

    AdministratorModeController(EnterAdministratorMode enter, ExitAdministratorMode exit) {
        this.enter = enter;
        this.exit = exit;
    }

    @PostMapping(path = "/sessions:enter-administrator-mode")
    @PreAuthorize("isAuthenticated()")
    AdministratorModeResponse enter(@RequestBody WecomLoginRequest request,
            @CookieValue(name = LoginStateCookie.NAME, required = false) String binding) {
        Instant until = enter.execute(new WecomLoginCommand(request.code(), request.state(), binding, request.entry()));
        return new AdministratorModeResponse(until);
    }

    @PostMapping(path = "/sessions:exit-administrator-mode")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> exit() {
        exit.execute();
        return ResponseEntity.noContent().build();
    }
}
