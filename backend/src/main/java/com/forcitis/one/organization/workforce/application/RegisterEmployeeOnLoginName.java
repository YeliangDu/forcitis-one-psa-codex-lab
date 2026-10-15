package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.StaffLookup;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RegisterEmployeeOnLoginName {
    private static final Logger LOG = LoggerFactory.getLogger(RegisterEmployeeOnLoginName.class);

    private final OrgSource source;
    private final LoginMissRegistration registration;

    RegisterEmployeeOnLoginName(OrgSource source, LoginMissRegistration registration) {
        this.source = source;
        this.registration = registration;
    }

    public Optional<StaffEmployee> execute(String loginName) {
        List<SourcePerson> found;
        try {
            found = source.findStaffByLogin(new StaffLookup.ByLoginName(loginName));
        } catch (ExternalSystemUnavailableException unavailable) {
            LOG.warn("organization.workforce login-miss lookup skipped, source not available: {}", unavailable.getMessage());
            return Optional.empty();
        }
        return registration.register(found, "login name");
    }
}
