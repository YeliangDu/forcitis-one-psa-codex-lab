package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.integration.api.WecomEntry;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginWithWecom {

    private final WecomIdentification identification;
    private final LoginAdmission admission;

    LoginWithWecom(WecomIdentification identification, LoginAdmission admission) {
        this.identification = identification;
        this.admission = admission;
    }

    @Transactional
    public LoginResult execute(WecomLoginCommand command) {
        WecomEntry entry = command.entry();
        if (entry == null) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_STATE_INVALID, LoginMethod.WECOM_WORKBENCH, null, "no entry");
        }
        LoginMethod method = LoginMethod.of(entry);
        return switch (identification.identify(command, entry)) {
            case WecomIdentification.Outcome.Failed failed -> admission.reject(failed.error(), method, null, failed.reason());
            case WecomIdentification.Outcome.Identified identified -> {
                Optional<IdentityError> refused = admission.check(identified.employee());
                yield refused.isPresent()
                        ? admission.reject(refused.get(), method, admission.accountOf(identified.employee()), refused.get().name())
                        : admission.complete(admission.accountOf(identified.employee()), method);
            }
        };
    }
}
