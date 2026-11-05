package com.forcitis.one.platform.identity.web;

import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.PublicEndpoint;
import com.forcitis.one.platform.identity.application.ListUserAccounts;
import com.forcitis.one.platform.identity.application.LoginAsLocalTestUser;
import com.forcitis.one.platform.identity.application.LoginResult;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Card #1478: the test login of a developer's own machine. The class exists only in the profile local; in every other profile the two paths are not mapped
 * at all, and the login page takes any refusal as "no local login here". The caller names an account to sign in as, which no other login lets it do: that is
 * the whole point of the local profile, and why LocalProfileListener refuses to start it near a shared database.
 */
@RestController
@Profile("local")
@RequestMapping("/api/v1/platform")
class LocalTestLoginController {

    private static final int ACCOUNTS_SHOWN = 100;

    private final ListUserAccounts accounts;
    private final LoginAsLocalTestUser login;

    LocalTestLoginController(ListUserAccounts accounts, LoginAsLocalTestUser login) {
        this.accounts = accounts;
        this.login = login;
    }

    @GetMapping(path = "/local-accounts")
    @PublicEndpoint(reason = "Profile local only (card #1478): the enabled accounts of this machine's own database that the local test login can sign in as; creates no session")
    List<LocalAccountResponse> accounts() {
        return accounts.execute("ENABLED", null, PageRequest.of(ACCOUNTS_SHOWN, null, "user-accounts")).items().stream()
                .map(item -> new LocalAccountResponse(item.userId().value(), item.displayName() == null ? item.userId().value().toString() : item.displayName(), item.roleCodes()))
                .toList();
    }

    @PostMapping(path = "/local-sessions")
    @PublicEndpoint(reason = "Profile local only (card #1478): signs in as one existing account of this machine's own database through the ordinary admission and session start")
    LoginResponse signIn(@RequestBody LocalLoginRequest request) {
        return switch (login.execute(request.accountId())) {
            case LoginResult.Authenticated ok -> new LoginResponse(ok.userId().value(), ok.absoluteExpiresAt(), ok.administratorModeExpiresAt(),
                    ok.identityConfirmationRequired());
            case LoginResult.Rejected rejected -> throw rejected.error().refusal("login refused");
        };
    }
}
