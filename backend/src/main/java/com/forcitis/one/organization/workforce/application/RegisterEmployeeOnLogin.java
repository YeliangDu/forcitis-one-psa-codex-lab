package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.domain.OrgSource;
import com.forcitis.one.organization.workforce.domain.SourceMember;
import com.forcitis.one.organization.workforce.domain.SourceUnavailableException;
import com.forcitis.one.organization.workforce.domain.StaffMember;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Login miss by WeCom user id (a new hire not synced yet): ask the organisation source for this one person and register
 * them if every check of the sync holds (see {@link LoginMissRegistration}). Source unavailable answers "unknown".
 */
@Service
public class RegisterEmployeeOnLogin {

    private static final Logger LOG = LoggerFactory.getLogger(RegisterEmployeeOnLogin.class);

    private final OrgSource source;
    private final LoginMissRegistration registration;

    RegisterEmployeeOnLogin(OrgSource source, LoginMissRegistration registration) {
        this.source = source;
        this.registration = registration;
    }

    public Optional<StaffMember> execute(WecomUserId wecomUserId) {
        List<SourceMember> found;
        try {
            found = source.fetchMembersByWecomUserId(wecomUserId);
        } catch (SourceUnavailableException unavailable) {
            LOG.warn("organization.workforce login-miss lookup skipped, source not available: {}", unavailable.getMessage());
            return Optional.empty();
        }
        return registration.register(found, "WeCom user id");
    }
}
