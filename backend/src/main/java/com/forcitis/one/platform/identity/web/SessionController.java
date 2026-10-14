package com.forcitis.one.platform.identity.web;

import com.forcitis.one.kernel.PublicEndpoint;
import com.forcitis.one.platform.identity.application.BeginWecomLogin;
import com.forcitis.one.platform.identity.application.EmergencyLoginCommand;
import com.forcitis.one.platform.identity.application.GetCurrentUserProfile;
import com.forcitis.one.platform.identity.application.LoginAsEmergencyAdmin;
import com.forcitis.one.platform.identity.application.LoginResult;
import com.forcitis.one.platform.identity.application.LoginWithSso;
import com.forcitis.one.platform.identity.application.LoginWithSignedTicket;
import com.forcitis.one.platform.identity.application.LoginWithWecom;
import com.forcitis.one.platform.identity.application.Logout;
import com.forcitis.one.platform.identity.application.SsoSettings;
import com.forcitis.one.platform.identity.application.WecomLoginCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No request carries a user id: who logs in is decided by the proof, who is calling by the session only (I-3). */
@RestController
@RequestMapping("/api/{version}/platform")
class SessionController {

    private final BeginWecomLogin beginWecomLogin;
    private final LoginWithWecom loginWithWecom;
    private final LoginWithSso loginWithSso;
    private final LoginWithSignedTicket loginWithSignedTicket;
    private final LoginAsEmergencyAdmin loginAsEmergencyAdmin;
    private final Logout logout;
    private final GetCurrentUserProfile getCurrentUserProfile;
    private final SsoSettings ssoSettings;

    SessionController(BeginWecomLogin beginWecomLogin, LoginWithWecom loginWithWecom, LoginWithSso loginWithSso,
            LoginWithSignedTicket loginWithSignedTicket, LoginAsEmergencyAdmin loginAsEmergencyAdmin, Logout logout,
            GetCurrentUserProfile getCurrentUserProfile, SsoSettings ssoSettings) {
        this.beginWecomLogin = beginWecomLogin;
        this.loginWithWecom = loginWithWecom;
        this.loginWithSso = loginWithSso;
        this.loginWithSignedTicket = loginWithSignedTicket;
        this.loginAsEmergencyAdmin = loginAsEmergencyAdmin;
        this.logout = logout;
        this.getCurrentUserProfile = getCurrentUserProfile;
        this.ssoSettings = ssoSettings;
    }

    @PostMapping(path = "/login-states", version = "1")
    @PublicEndpoint(reason = "One-time OAuth state for the WeCom login redirect; creates no session. ADR-0009 §3, RULE-322/324, I-9")
    ResponseEntity<?> beginWecomLogin(@RequestBody LoginStateRequest request) {
        if (request.entry() == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("PLATFORM_IDENTITY_STATE_INVALID", null));
        }
        return ResponseEntity.ok(new LoginStateResponse(beginWecomLogin.execute(request.entry())));
    }

    @PostMapping(path = "/sessions:wecom-login", version = "1")
    @PublicEndpoint(reason = "WeCom OAuth login (workbench, mini program, PC scan): identity proven by the WeCom code and a one-time state. ADR-0009 §3, RULE-322/323/324/326/327, I-3, I-9")
    ResponseEntity<?> loginWithWecom(@RequestBody WecomLoginRequest request) {
        return respond(loginWithWecom.execute(new WecomLoginCommand(request.code(), request.state(), request.entry())), null);
    }

    @PostMapping(path = "/sessions:sso-login", version = "1")
    @PublicEndpoint(reason = "Portal single sign-on: the server checks the one-time ticket against the portal. ADR-0009 §3, RULE-320/321, I-3, I-9")
    ResponseEntity<?> loginWithSso(@RequestBody TicketLoginRequest request) {
        return respond(loginWithSso.execute(request.ticket()), emptyToNull(ssoSettings.legacyLoginUrl()));
    }

    @PostMapping(path = "/sessions:ticket-login", version = "1")
    @PublicEndpoint(reason = "Signed one-time ticket from an allow-listed external system (HMAC, at most 60 s, nonce used once). ADR-0009 §3, RULE-318, C1a-03, I-2, I-3, I-9")
    ResponseEntity<?> loginWithSignedTicket(@RequestBody TicketLoginRequest request) {
        return respond(loginWithSignedTicket.execute(request.ticket()), null);
    }

    @PostMapping(path = "/sessions:emergency-login", version = "1")
    @PublicEndpoint(reason = "Break-glass administrator: Argon2id password plus TOTP, internal network only, locked after 5 failures. ADR-0009 §3, RULE-316/317, C1a-04, I-3")
    ResponseEntity<?> loginAsEmergencyAdmin(@RequestBody EmergencyLoginRequest request) {
        return respond(loginAsEmergencyAdmin.execute(new EmergencyLoginCommand(request.loginName(), request.password(), request.totpCode())), null);
    }

    @PostMapping(path = "/sessions:logout", version = "1")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> logout() {
        logout.execute();
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/sessions:current", version = "1")
    @PreAuthorize("isAuthenticated()")
    CurrentUserResponse current() {
        return CurrentUserResponse.of(getCurrentUserProfile.execute());
    }

    private static ResponseEntity<?> respond(LoginResult result, String legacyLoginUrl) {
        return switch (result) {
            case LoginResult.Authenticated ok -> ResponseEntity.ok(new LoginResponse(String.valueOf(ok.userId()), ok.absoluteExpiresAt()));
            case LoginResult.Rejected rejected -> IdentityResponses.error(rejected.error(), legacyLoginUrl);
        };
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
