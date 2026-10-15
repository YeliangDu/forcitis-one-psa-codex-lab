package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.EmployeeId;
import java.util.Optional;

/**
 * The subject of a signed ticket is the organisation source's person id (any signed 64-bit number); the employee is found through the
 * confirmed (ORG_SOURCE, EMPLOYEE) source reference, looked up and never allocated (ADR-0033 section 3 item 5). Empty when the subject is
 * no such number or has no reference.
 */
public interface TicketSubjects {

    Optional<EmployeeId> employeeOf(String subject);
}
