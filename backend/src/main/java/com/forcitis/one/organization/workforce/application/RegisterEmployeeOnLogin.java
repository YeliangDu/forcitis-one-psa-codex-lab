package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.WecomUserId;
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
public class RegisterEmployeeOnLogin {
    private static final Logger LOG = LoggerFactory.getLogger(RegisterEmployeeOnLogin.class);

    private final OrgSource source;
    private final LoginMissRegistration registration;

    RegisterEmployeeOnLogin(OrgSource source, LoginMissRegistration registration) {
        this.source = source;
        this.registration = registration;
    }

    public Optional<StaffEmployee> execute(WecomUserId wecomUserId) {
        List<SourcePerson> found;
        try {
            found = source.findStaffByLogin(new StaffLookup.ByWecomUser(wecomUserId));
        } catch (ExternalSystemUnavailableException unavailable) {
            LOG.warn("organization.workforce login-miss lookup skipped, source not available: {}", unavailable.getMessage());
            return Optional.empty();
        }
        return registration.register(found, "WeCom user id");
    }
}
