package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.GlobalDocumentNumberSource;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.NewEmployee;
import com.forcitis.one.organization.workforce.domain.PersonalProfile;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * The root of a person a HIRE brings in (W2 section 3.1, ADR-0068 item 1.5; one-design DD on #959). It runs in the hire's transaction: the id is the server's, the
 * code is numbered through the kernel port that platform.numbering implements, on the entry day, and a collision with any employee's code, or a WeCom user id anyone holds, refuses the hire, so the
 * counter rolls back with it and nothing stays.
 */
@Component
class NewEmployees {
    private static final DocumentTypeCode EMPLOYEE_CODE = new DocumentTypeCode("EMPLOYEE");

    private final PersonnelCommandRepository commands;
    private final GlobalDocumentNumberSource numbers;
    private final IdGenerator ids;

    NewEmployees(PersonnelCommandRepository commands, GlobalDocumentNumberSource numbers, IdGenerator ids) {
        this.commands = commands;
        this.numbers = numbers;
        this.ids = ids;
    }

    EmployeeId create(EnterPersonnelCommand request, LocalDate today, Instant now) {
        if (request.employeeId() != null) {
            throw invalid("a hire names no employee; the server makes the id");
        }
        if (request.displayName() == null) {
            throw invalid("a hire carries the name of the new employee");
        }
        PersonalProfile profile = new PersonalProfile(request.displayName(), request.gender(), request.email(), request.wecomUserId());
        if (profile.wecomUserId() != null && commands.wecomUserIdHeld(profile.wecomUserId(), null)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_WECOM_USER_ID_TAKEN, ErrorKind.UNPROCESSABLE, "the WeCom user id belongs to another employee");
        }
        String code = numbers.next(EMPLOYEE_CODE, today);
        if (commands.employeeCodeTaken(code)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_CODE_TAKEN, ErrorKind.CONFLICT, "the issued employee code is already an employee's");
        }
        EmployeeId employee = new EmployeeId(ids.next());
        commands.insertEmployee(new NewEmployee(employee, code, profile, Boolean.TRUE.equals(request.isTargetFeeEarner())), now);
        return employee;
    }

    private static DomainException invalid(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE, message);
    }
}
