package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ConfiguredProcessAdministrator implements ProcessAdministrator {

    private final AssignmentProperties properties;

    ConfiguredProcessAdministrator(AssignmentProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<UserId> current() {
        return Optional.ofNullable(properties.processAdministratorUserId()).map(UserId::new);
    }
}
