package com.forcitis.one.platform.identity.application;

import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.platform.identity.domain.Digests;
import com.forcitis.one.platform.identity.domain.OneTimeCredentials;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeyon OA single sign-on (RULE-320/321): the server asks OA whether the ticket is genuine, the ticket can be used once
 * (atomic unique-key insert, so concurrent replays give one winner, I-9), and the person must be employed and enabled.
 * The ticket arrives in the request body, never in a URL we keep.
 */
@Service
public class LoginWithSeeyon {

    private static final int MAX_TICKET_LENGTH = 256;
    private static final String SOURCE = "SEEYON";

    private final SeeyonSettings settings;
    private final SeeyonTicketVerifier verifier;
    private final OneTimeCredentials credentials;
    private final LoginNameDirectory loginNames;
    private final LoginAdmission admission;
    private final Clock clock;

    LoginWithSeeyon(SeeyonSettings settings, SeeyonTicketVerifier verifier, OneTimeCredentials credentials,
            LoginNameDirectory loginNames, LoginAdmission admission, Clock clock) {
        this.settings = settings;
        this.verifier = verifier;
        this.credentials = credentials;
        this.loginNames = loginNames;
        this.admission = admission;
        this.clock = clock;
    }

    @Transactional
    public LoginResult execute(String ticket) {
        LoginMethod method = LoginMethod.SEEYON;
        if (!settings.enabled()) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_SEEYON_UNAVAILABLE, method, null, "Seeyon SSO is switched off");
        }
        if (ticket == null || ticket.isBlank() || ticket.length() > MAX_TICKET_LENGTH) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_TICKET_INVALID, method, null, "ticket missing or too long");
        }
        SeeyonVerdict verdict = verifier.verify(ticket);
        if (verdict instanceof SeeyonVerdict.Unavailable) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_SEEYON_UNAVAILABLE, method, null, "OA unavailable");
        }
        if (!(verdict instanceof SeeyonVerdict.LoginName loginName)) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_TICKET_INVALID, method, null, "OA does not know the ticket");
        }
        // Marked before the person is looked up, like the old system: a ticket for an unknown person is burned too (RULE-320).
        boolean firstUse = credentials.markTicketUsed(SOURCE, Digests.sha256Hex(ticket),
                clock.instant().plus(settings.replayWindow()));
        if (!firstUse) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_TICKET_REPLAYED, method, null, "ticket already used");
        }
        Optional<EmployeeProfile> employee = loginNames.findEmployeeByLoginName(loginName.value());
        if (employee.isEmpty()) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_LOGIN_NAME_UNKNOWN, method, null,
                    "no employee for login name " + loginName.value());
        }
        Optional<IdentityError> refused = admission.check(employee.get());
        if (refused.isPresent()) {
            return admission.reject(refused.get(), method, employee.get().userId(), refused.get().name());
        }
        return admission.complete(employee.get().userId(), method);
    }
}
