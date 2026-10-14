package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.Digests;
import com.forcitis.one.platform.identity.domain.OneTimeCredentials;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Who a WeCom code proves: the OAuth state is consumed atomically first (I-9); the person is found by exact WeCom user id only. */
@Component
class WecomIdentification {

    private static final int MAX_CODE_LENGTH = 512;
    private static final int MAX_STATE_LENGTH = 128;

    sealed interface Outcome {
        record Identified(EmployeeProfile employee) implements Outcome {
        }

        record Failed(IdentityError error, String reason) implements Outcome {
        }
    }

    private final OneTimeCredentials credentials;
    private final WecomGateway wecom;
    private final OrgDirectory directory;
    private final Clock clock;

    WecomIdentification(OneTimeCredentials credentials, WecomGateway wecom, OrgDirectory directory, Clock clock) {
        this.credentials = credentials;
        this.wecom = wecom;
        this.directory = directory;
        this.clock = clock;
    }

    Outcome identify(WecomLoginCommand command, WecomEntry entry) {
        if (isBlank(command.code()) || command.code().length() > MAX_CODE_LENGTH) {
            return new Outcome.Failed(IdentityError.PLATFORM_IDENTITY_WECOM_CODE_INVALID, "code missing or too long");
        }
        if (entry != WecomEntry.MINI_PROGRAM && !consumeState(command.state(), command.binding(), entry)) {
            return new Outcome.Failed(IdentityError.PLATFORM_IDENTITY_STATE_INVALID,
                    "state unknown, used, expired, for another entry or not from this browser");
        }
        Optional<WecomUserId> wecomUserId = wecom.resolveWecomUserId(command.code(), entry);
        if (wecomUserId.isEmpty()) {
            return new Outcome.Failed(IdentityError.PLATFORM_IDENTITY_WECOM_CODE_INVALID, "WeCom did not accept the code");
        }
        Optional<EmployeeProfile> employee = directory.findEmployeeByWecomUserId(wecomUserId.get());
        if (employee.isEmpty()) {
            return new Outcome.Failed(IdentityError.PLATFORM_IDENTITY_WECOM_NOT_BOUND,
                    "no employee for WeCom user id " + wecomUserId.get().value());
        }
        return new Outcome.Identified(employee.get());
    }

    private boolean consumeState(String state, String binding, WecomEntry entry) {
        if (isBlank(state) || state.length() > MAX_STATE_LENGTH || isBlank(binding) || binding.length() > MAX_STATE_LENGTH) {
            return false;
        }
        Optional<WecomEntry> issuedFor = credentials.consumeLoginState(Digests.sha256Hex(state), Digests.sha256Hex(binding), clock.instant());
        return issuedFor.isPresent() && issuedFor.get() == entry;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
