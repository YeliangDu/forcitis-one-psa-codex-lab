package com.forcitis.one.platform.identity.web;

import com.forcitis.one.kernel.PublicEndpoint;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.application.BeginWecomLogin;
import com.forcitis.one.platform.identity.application.BrowserIdentifierSettings;
import com.forcitis.one.platform.identity.application.ClientOrigin;
import com.forcitis.one.platform.identity.application.ConfirmIdentity;
import com.forcitis.one.platform.identity.application.LoginStateIssue;
import com.forcitis.one.platform.identity.application.LoginThrottle;
import com.forcitis.one.platform.identity.application.SessionSettings;
import com.forcitis.one.platform.identity.application.EmergencyLoginCommand;
import com.forcitis.one.platform.identity.application.GetCurrentUserProfile;
import com.forcitis.one.platform.identity.application.LoginAsEmergencyAdmin;
import com.forcitis.one.platform.identity.application.LoginResult;
import com.forcitis.one.platform.identity.application.LoginWithSignedTicket;
import com.forcitis.one.platform.identity.application.LoginWithWecom;
import com.forcitis.one.platform.identity.application.Logout;
import com.forcitis.one.platform.identity.application.WecomLoginCommand;
import io.swagger.v3.oas.annotations.Parameter;
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
    private final LoginWithSignedTicket loginWithSignedTicket;
    private final LoginAsEmergencyAdmin loginAsEmergencyAdmin;
    private final Logout logout;
    private final ConfirmIdentity confirmIdentity;
    private final GetCurrentUserProfile getCurrentUserProfile;
    private final LoginThrottle throttle;
    private final ClientOrigin clientOrigin;
    private final SessionSettings sessionSettings;
    private final BrowserIdentifierSettings identifierSettings;

    SessionController(BeginWecomLogin beginWecomLogin, LoginWithWecom loginWithWecom,
            LoginWithSignedTicket loginWithSignedTicket, LoginAsEmergencyAdmin loginAsEmergencyAdmin, Logout logout, ConfirmIdentity confirmIdentity,
            GetCurrentUserProfile getCurrentUserProfile, LoginThrottle throttle, ClientOrigin clientOrigin,
            SessionSettings sessionSettings, BrowserIdentifierSettings identifierSettings) {
        this.identifierSettings = identifierSettings;
        this.beginWecomLogin = beginWecomLogin;
        this.loginWithWecom = loginWithWecom;
        this.loginWithSignedTicket = loginWithSignedTicket;
        this.loginAsEmergencyAdmin = loginAsEmergencyAdmin;
        this.logout = logout;
        this.confirmIdentity = confirmIdentity;
        this.getCurrentUserProfile = getCurrentUserProfile;
        this.throttle = throttle;
        this.clientOrigin = clientOrigin;
        this.sessionSettings = sessionSettings;
    }

    @PostMapping(path = "/login-states")
    @PublicEndpoint(reason = "One-time OAuth state for the WeCom login redirect; creates no session. ADR-0009 §3, RULE-322/324, I-9")
    ResponseEntity<LoginStateResponse> beginWecomLogin(@RequestBody LoginStateRequest request) {
        if (request.entry() == null) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_STATE_INVALID, ErrorKind.INVALID, "no entry");
        }
        throttle("login-states");
        LoginStateIssue issued = beginWecomLogin.execute(request.entry());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                LoginStateCookie.issue(issued.binding(), sessionSettings.secureCookie(), issued.lifetime()).toString())
                .body(new LoginStateResponse(issued.state()));
    }

    @PostMapping(path = "/sessions:wecom-login")
    @PublicEndpoint(reason = "WeCom OAuth login (workbench, mini program, PC scan): identity proven by the WeCom code and a one-time state. ADR-0009 §3, RULE-322/323/324/326/327, I-3, I-9")
    ResponseEntity<LoginResponse> loginWithWecom(@RequestBody WecomLoginRequest request,
            @CookieValue(name = LoginStateCookie.NAME, required = false) String binding) {
        throttle("wecom-login");
        LoginResponse response = respond(loginWithWecom.execute(new WecomLoginCommand(request.code(), request.state(), binding, request.entry())));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, LoginStateCookie.clear(sessionSettings.secureCookie()).toString()).body(response);
    }

    @PostMapping(path = "/sessions:ticket-login")
    @PublicEndpoint(reason = "Signed one-time ticket from an allow-listed external system (HMAC, at most 60 s, nonce used once). ADR-0009 §3, RULE-318, C1a-03, I-2, I-3, I-9")
    ResponseEntity<LoginResponse> loginWithSignedTicket(@RequestBody TicketLoginRequest request,
            @Parameter(hidden = true) @CookieValue(name = BrowserIdentifierCookie.NAME, required = false) String browserIdentifier) {
        throttle("ticket-login");
        return withIdentifierCookie(loginWithSignedTicket.execute(request.ticket(), browserIdentifier));
    }

    @PostMapping(path = "/sessions:emergency-login")
    @PublicEndpoint(reason = "Break-glass administrator: Argon2id password plus TOTP, internal network only, locked after 5 failures. ADR-0009 §3, RULE-316/317, C1a-04, I-3")
    LoginResponse loginAsEmergencyAdmin(@RequestBody EmergencyLoginRequest request) {
        throttle("emergency-login");
        return respond(loginAsEmergencyAdmin.execute(new EmergencyLoginCommand(request.loginName(), request.password(), request.totpCode())));
    }

    @PostMapping(path = "/sessions:logout")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> logout() {
        logout.execute();
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/sessions:confirm-identity")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> confirmIdentity(@Parameter(hidden = true) @CookieValue(name = BrowserIdentifierCookie.NAME, required = false) String browserIdentifier) {
        String issued = confirmIdentity.execute(browserIdentifier);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, BrowserIdentifierCookie.issue(issued, identifierSettings.lifetime()).toString()).build();
    }

    @GetMapping(path = "/sessions:current")
    @PreAuthorize("isAuthenticated()")
    CurrentUserResponse current() {
        return CurrentUserResponse.of(getCurrentUserProfile.execute());
    }

    private void throttle(String kind) {
        if (!throttle.allows(kind, clientOrigin.address())) {
            throw IdentityError.PLATFORM_IDENTITY_LOGIN_RATE_LIMITED.refusal("too many " + kind + " requests");
        }
    }

    private ResponseEntity<LoginResponse> withIdentifierCookie(LoginResult result) {
        LoginResponse response = respond(result);
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
        if (result instanceof LoginResult.Authenticated authenticated && authenticated.browserIdentifier() != null) {
            ok.header(HttpHeaders.SET_COOKIE, BrowserIdentifierCookie.issue(authenticated.browserIdentifier(), identifierSettings.lifetime()).toString());
        }
        return ok.body(response);
    }

    private static LoginResponse respond(LoginResult result) {
        return switch (result) {
            case LoginResult.Authenticated ok -> new LoginResponse(ok.userId().value(), ok.absoluteExpiresAt(), ok.administratorModeExpiresAt(),
                    ok.identityConfirmationRequired());
            case LoginResult.Rejected rejected -> throw rejected.error().refusal("login refused");
        };
    }
}
