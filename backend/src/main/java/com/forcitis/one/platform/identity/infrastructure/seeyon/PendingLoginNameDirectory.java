package com.forcitis.one.platform.identity.infrastructure.seeyon;

import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.platform.identity.application.LoginNameDirectory;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Placeholder until OrgDirectory can look an employee up by A8 login name (contract change requested): every login name is
 * unknown, so Seeyon login fails closed. Replace the body with a call to the OrgDirectory method once it exists.
 */
@Component
class PendingLoginNameDirectory implements LoginNameDirectory {

    @Override
    public Optional<EmployeeProfile> findEmployeeByLoginName(String loginName) {
        return Optional.empty();
    }
}
