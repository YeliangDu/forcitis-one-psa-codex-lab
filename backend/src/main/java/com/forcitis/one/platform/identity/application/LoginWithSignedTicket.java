package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.domain.AccessProfile;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Digests;
import com.forcitis.one.platform.identity.domain.IssuerKeys;
import com.forcitis.one.platform.identity.domain.OneTimeCredentials;
import com.forcitis.one.platform.identity.domain.SignedTicket;
import com.forcitis.one.platform.identity.domain.TicketIssuer;
import com.forcitis.one.platform.identity.domain.TicketIssuerRepository;
import com.forcitis.one.platform.integration.api.TicketSubjects;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every failure looks the same to the caller. */
@Service
public class LoginWithSignedTicket {

    private static final Duration NONCE_KEPT_AFTER_EXPIRY = Duration.ofMinutes(5);

    private final TicketIssuerRepository issuers;
    private final IssuerKeys keys;
    private final OneTimeCredentials credentials;
    private final OrgDirectory directory;
    private final AccountRepository accounts;
    private final LoginAdmission admission;
    private final TicketSubjects subjects;
    private final Clock clock;

    LoginWithSignedTicket(TicketIssuerRepository issuers, IssuerKeys keys, OneTimeCredentials credentials, OrgDirectory directory,
            AccountRepository accounts, LoginAdmission admission, TicketSubjects subjects, Clock clock) {
        this.issuers = issuers;
        this.keys = keys;
        this.credentials = credentials;
        this.directory = directory;
        this.accounts = accounts;
        this.admission = admission;
        this.subjects = subjects;
        this.clock = clock;
    }

    @Transactional
    public LoginResult execute(String rawTicket, @Nullable String browserIdentifier) {
        LoginMethod method = LoginMethod.SIGNED_TICKET;
        Optional<SignedTicket> parsed = SignedTicket.parse(rawTicket);
        if (parsed.isEmpty()) {
            return invalid(method, "malformed");
        }
        SignedTicket ticket = parsed.get();
        Optional<TicketIssuer> found = issuers.findByKid(ticket.kid());
        if (found.isEmpty()) {
            return invalid(method, "unknown kid");
        }
        TicketIssuer issuer = found.get();
        if (!issuer.enabled()) {
            return invalid(method, "issuer " + issuer.issuerCode() + " is switched off");
        }
        if (!issuer.issuerCode().equals(ticket.issuer())) {
            return invalid(method, "issuer does not match kid");
        }
        Optional<byte[]> key = keys.resolve(issuer.keyReference());
        if (key.isEmpty() || !ticket.isSignedWith(key.get())) {
            return invalid(method, "key missing or signature wrong");
        }
        Instant now = clock.instant();
        Optional<String> timeProblem = ticket.timeProblem(now);
        if (timeProblem.isPresent()) {
            return invalid(method, timeProblem.get());
        }
        if (!credentials.markTicketUsed(issuer.issuerCode(), Digests.sha256Hex(ticket.nonce()),
                ticket.expiresAt().plus(NONCE_KEPT_AFTER_EXPIRY))) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_TICKET_REPLAYED, method, null,
                    "nonce already used, issuer " + issuer.issuerCode());
        }
        Optional<EmployeeId> subject = subjects.employeeOf(ticket.subject());
        if (subject.isEmpty()) {
            return invalid(method, "subject has no organisation source reference");
        }
        Optional<EmployeeProfile> employee = directory.findEmployee(subject.get());
        if (employee.isEmpty()) {
            return invalid(method, "subject is not an employee");
        }
        Optional<IdentityError> refused = admission.check(employee.get());
        if (refused.isPresent()) {
            return admission.reject(refused.get(), method, admission.accountOf(employee.get()), refused.get().name() + ", issuer " + issuer.issuerCode());
        }
        UserId userId = admission.accountOf(employee.get());
        AccessProfile profile = AccessProfile.of(accounts.findEnabledRoles(userId));
        if (profile.isAdministrator()) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED, method, userId,
                    "administrators cannot enter through a ticket, issuer " + issuer.issuerCode());
        }
        if (issuer.allowedRoleCodes().stream().noneMatch(profile.roleCodes()::contains)) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED, method, userId,
                    "outside the user scope of issuer " + issuer.issuerCode());
        }
        return admission.completeFromTicket(userId, method, browserIdentifier);
    }

    private LoginResult invalid(LoginMethod method, String reason) {
        return admission.reject(IdentityError.PLATFORM_IDENTITY_TICKET_INVALID, method, null, reason);
    }
}
