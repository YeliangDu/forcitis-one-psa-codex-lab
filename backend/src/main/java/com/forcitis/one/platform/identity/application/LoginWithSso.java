package com.forcitis.one.platform.identity.application;

import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.integration.api.SsoTicketVerifier;
import com.forcitis.one.platform.integration.api.SsoVerdict;
import com.forcitis.one.platform.identity.domain.Digests;
import com.forcitis.one.platform.identity.domain.OneTimeCredentials;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ticket use is an atomic unique-key insert, so concurrent replays give one winner (I-9). */
@Service
public class LoginWithSso {

    private static final int MAX_TICKET_LENGTH = 256;
    private static final String SOURCE = "SSO";

    private final SsoSettings settings;
    private final SsoTicketVerifier verifier;
    private final OneTimeCredentials credentials;
    private final OrgDirectory orgDirectory;
    private final LoginAdmission admission;
    private final Clock clock;

    LoginWithSso(SsoSettings settings, SsoTicketVerifier verifier, OneTimeCredentials credentials,
            OrgDirectory orgDirectory, LoginAdmission admission, Clock clock) {
        this.settings = settings;
        this.verifier = verifier;
        this.credentials = credentials;
        this.orgDirectory = orgDirectory;
        this.admission = admission;
        this.clock = clock;
    }

    @Transactional
    public LoginResult execute(String ticket) {
        LoginMethod method = LoginMethod.SSO;
        if (!settings.enabled()) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_SSO_UNAVAILABLE, method, null, "single sign-on is switched off");
        }
        if (ticket == null || ticket.isBlank() || ticket.length() > MAX_TICKET_LENGTH) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_TICKET_INVALID, method, null, "ticket missing or too long");
        }
        SsoVerdict verdict = verifier.verify(ticket);
        if (verdict instanceof SsoVerdict.Unavailable) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_SSO_UNAVAILABLE, method, null, "portal unavailable");
        }
        if (!(verdict instanceof SsoVerdict.LoginName loginName)) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_TICKET_INVALID, method, null, "portal does not know the ticket");
        }
        // Marked before the person is looked up, like the old system: a ticket for an unknown person is burned too.
        boolean firstUse = credentials.markTicketUsed(SOURCE, Digests.sha256Hex(ticket),
                clock.instant().plus(settings.replayWindow()));
        if (!firstUse) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_TICKET_REPLAYED, method, null, "ticket already used");
        }
        Optional<EmployeeProfile> employee = orgDirectory.findEmployeeByLoginName(loginName.value());
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
