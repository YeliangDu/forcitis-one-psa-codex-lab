package com.forcitis.one.platform.identity.application;

import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import java.util.Optional;

/**
 * Port: A8 login name to employee. OrgDirectory has no such query yet (contract change requested from the main session),
 * so the shipped adapter answers "unknown" and Seeyon login stays off until it is wired to OrgDirectory.
 */
public interface LoginNameDirectory {

    Optional<EmployeeProfile> findEmployeeByLoginName(String loginName);
}
