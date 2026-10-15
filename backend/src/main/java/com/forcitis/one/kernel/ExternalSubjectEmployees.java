package com.forcitis.one.kernel;

import java.util.List;

/**
 * The employees an outside sign-in names, for the read-only lookup of the operations (ADR-0033 section 3 item 6); answered by the module
 * that keeps the organisation snapshot, every match listed (two people may share a login name). Asked by platform.integration, which may not
 * depend on organization.workforce (the context map points the other way), implemented by it. Moves to the external identities with DA-3.
 */
public interface ExternalSubjectEmployees {

    List<EmployeeId> withLoginName(String loginName);

    List<EmployeeId> withWecomUserId(WecomUserId wecomUserId);
}
