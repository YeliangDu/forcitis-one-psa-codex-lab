package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.domain.Digests;
import com.forcitis.one.platform.identity.domain.OneTimeCredentials;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The OAuth state is consumed atomically first (I-9); the person is found by exact WeCom user id only, never by e-mail or name. */
@Service
public class LoginWithWecom {

    private static final int MAX_CODE_LENGTH = 512;
    private static final int MAX_STATE_LENGTH = 128;

    private final OneTimeCredentials credentials;
    private final WecomGateway wecom;
    private final OrgDirectory directory;
    private final LoginAdmission admission;
    private final Clock clock;

    LoginWithWecom(OneTimeCredentials credentials, WecomGateway wecom, OrgDirectory directory, LoginAdmission admission,
            Clock clock) {
        this.credentials = credentials;
        this.wecom = wecom;
        this.directory = directory;
        this.admission = admission;
        this.clock = clock;
    }

    @Transactional
    public LoginResult execute(WecomLoginCommand command) {
        WecomEntry entry = command.entry();
        if (entry == null) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_STATE_INVALID, LoginMethod.WECOM_WORKBENCH, null, "no entry");
        }
        LoginMethod method = LoginMethod.of(entry);
        if (isBlank(command.code()) || command.code().length() > MAX_CODE_LENGTH) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_WECOM_CODE_INVALID, method, null, "code missing or too long");
        }
        if (entry != WecomEntry.MINI_PROGRAM && !consumeState(command.state(), entry)) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_STATE_INVALID, method, null, "state unknown, used, expired or for another entry");
        }
        Optional<WecomUserId> wecomUserId = wecom.resolveWecomUserId(command.code(), entry);
        if (wecomUserId.isEmpty()) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_WECOM_CODE_INVALID, method, null, "WeCom did not accept the code");
        }
        Optional<EmployeeProfile> employee = directory.findEmployeeByWecomUserId(wecomUserId.get());
        if (employee.isEmpty()) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_WECOM_NOT_BOUND, method, null,
                    "no employee for WeCom user id " + wecomUserId.get().value());
        }
        Optional<IdentityError> refused = admission.check(employee.get());
        if (refused.isPresent()) {
            return admission.reject(refused.get(), method, employee.get().userId(), refused.get().name());
        }
        return admission.complete(employee.get().userId(), method);
    }

    private boolean consumeState(String state, WecomEntry entry) {
        if (isBlank(state) || state.length() > MAX_STATE_LENGTH) {
            return false;
        }
        Optional<WecomEntry> issuedFor = credentials.consumeLoginState(Digests.sha256Hex(state), clock.instant());
        return issuedFor.isPresent() && issuedFor.get() == entry;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
