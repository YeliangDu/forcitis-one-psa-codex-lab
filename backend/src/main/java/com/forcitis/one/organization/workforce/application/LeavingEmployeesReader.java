package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.LeavingEmployees;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class LeavingEmployeesReader implements LeavingEmployees {

    private final PersonnelCommandRepository commands;

    LeavingEmployeesReader(PersonnelCommandRepository commands) {
        this.commands = commands;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<EmployeeId> excluded() {
        return commands.excludedEmployees();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<EmployeeId> held() {
        return commands.heldEmployees();
    }
}
