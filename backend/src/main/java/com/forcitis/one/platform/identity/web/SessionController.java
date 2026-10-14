package com.forcitis.one.platform.identity.web;

import com.forcitis.one.kernel.PublicEndpoint;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.application.BeginWecomLogin;
import com.forcitis.one.platform.identity.application.ClientOrigin;
import com.forcitis.one.platform.identity.application.LoginStateIssue;
import com.forcitis.one.platform.identity.application.LoginThrottle;
import com.forcitis.one.platform.identity.application.SessionSettings;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No request carries a user id: who logs in is decided by the proof, who is calling by the session only (I-3). */
@RestController
@RequestMapping("/api/v1/platform")
class SessionController {

    private final BeginWecomLogin beginWecomLogin;
    private final LoginWithWecom loginWithWecom;
    private final LoginWithSso loginWithSso;
    private final LoginWithSignedTicket loginWithSignedTicket;
    private final LoginAsEmergencyAdmin loginAsEmergencyAdmin;
    private final Logout logout;
    private final GetCurrentUserProfile getCurrentUserProfile;
    private final SsoSettings ssoSettings;
    private final LoginThrottle throttle;
    private final ClientOrigin clientOrigin;
    private final SessionSettings sessionSettings;

    SessionController(BeginWecomLogin beginWecomLogin, LoginWithWecom loginWithWecom, LoginWithSso loginWithSso,
            LoginWithSignedTicket loginWithSignedTicket, LoginAsEmergencyAdmin loginAsEmergencyAdmin, Logout logout,
            GetCurrentUserProfile getCurrentUserProfile, SsoSettings ssoSettings, LoginThrottle throttle, ClientOrigin clientOrigin,
            SessionSettings sessionSettings) {
        this.beginWecomLogin = beginWecomLogin;
        this.loginWithWecom = loginWithWecom;
        this.loginWithSso = loginWithSso;
        this.loginWithSignedTicket = loginWithSignedTicket;
        this.loginAsEmergencyAdmin = loginAsEmergencyAdmin;
        this.logout = logout;
        this.getCurrentUserProfile = getCurrentUserProfile;
        this.ssoSettings = ssoSettings;
        this.throttle = throttle;
        this.clientOrigin = clientOrigin;
        this.sessionSettings = sessionSettings;
    }

    @PostMapping(path = "/login-states")
    @PublicEndpoint(reason = "One-time OAuth state for the WeCom login redirect; creates no session. ADR-0009 §3, RULE-322/324, I-9")
    ResponseEntity<?> beginWecomLogin(@RequestBody LoginStateRequest request) {
        if (request.entry() == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("PLATFORM_IDENTITY_STATE_INVALID", null));
        }
        if (!throttle.allows("login-states", clientOrigin.address())) {
            return rateLimited();
        }
        LoginStateIssue issued = beginWecomLogin.execute(request.entry());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                LoginStateCookie.issue(issued.binding(), sessionSettings.secureCookie(), issued.lifetime()).toString())
                .body(new LoginStateResponse(issued.state()));
    }

    @PostMapping(path = "/sessions:wecom-login")
    @PublicEndpoint(reason = "WeCom OAuth login (workbench, mini program, PC scan): identity proven by the WeCom code and a one-time state. ADR-0009 §3, RULE-322/323/324/326/327, I-3, I-9")
    ResponseEntity<?> loginWithWecom(@RequestBody WecomLoginRequest request,
            @CookieValue(name = LoginStateCookie.NAME, required = false) String binding) {
        if (!throttle.allows("wecom-login", clientOrigin.address())) {
            return rateLimited();
        }
        ResponseEntity<?> response = respond(loginWithWecom.execute(new WecomLoginCommand(request.code(), request.state(), binding, request.entry())), null);
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .header(HttpHeaders.SET_COOKIE, LoginStateCookie.clear(sessionSettings.secureCookie()).toString()).body(response.getBody());
    }

    @PostMapping(path = "/sessions:sso-login")
    @PublicEndpoint(reason = "Portal single sign-on: the server checks the one-time ticket against the portal. ADR-0009 §3, RULE-320/321, I-3, I-9")
    ResponseEntity<?> loginWithSso(@RequestBody TicketLoginRequest request) {
        return respond(loginWithSso.execute(request.ticket()), emptyToNull(ssoSettings.legacyLoginUrl()));
    }

    @PostMapping(path = "/sessions:ticket-login")
    @PublicEndpoint(reason = "Signed one-time ticket from an allow-listed external system (HMAC, at most 60 s, nonce used once). ADR-0009 §3, RULE-318, C1a-03, I-2, I-3, I-9")
    ResponseEntity<?> loginWithSignedTicket(@RequestBody TicketLoginRequest request) {
        return respond(loginWithSignedTicket.execute(request.ticket()), null);
    }

    @PostMapping(path = "/sessions:emergency-login")
    @PublicEndpoint(reason = "Break-glass administrator: Argon2id password plus TOTP, internal network only, locked after 5 failures. ADR-0009 §3, RULE-316/317, C1a-04, I-3")
    ResponseEntity<?> loginAsEmergencyAdmin(@RequestBody EmergencyLoginRequest request) {
        return respond(loginAsEmergencyAdmin.execute(new EmergencyLoginCommand(request.loginName(), request.password(), request.totpCode())), null);
    }

    @PostMapping(path = "/sessions:logout")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> logout() {
        logout.execute();
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/sessions:current")
    @PreAuthorize("isAuthenticated()")
    CurrentUserResponse current() {
        return CurrentUserResponse.of(getCurrentUserProfile.execute());
    }

    private static ResponseEntity<?> rateLimited() {
        return IdentityResponses.error(IdentityError.PLATFORM_IDENTITY_LOGIN_RATE_LIMITED, null);
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
