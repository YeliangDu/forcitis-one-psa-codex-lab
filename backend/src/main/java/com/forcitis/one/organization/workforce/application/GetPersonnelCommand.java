package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.application.CommandReadViews.Personnel;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries.Scope;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPersonnelCommand {
    private final CommandReadQueries queries;
    private final PersonnelCommandReadScope scope;
    private final AuditLog audit;
    private final CurrentUser currentUser;

    GetPersonnelCommand(CommandReadQueries queries, PersonnelCommandReadScope scope, AuditLog audit, CurrentUser currentUser) {
        this.queries = queries;
        this.scope = scope;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PersonnelCommandReadScope.PERMISSION + "')")
    public Personnel execute(PersonnelCommandId id) {
        Scope visible;
        try {
            visible = scope.resolve();
        } catch (DomainException unavailable) {
            if (unavailable.kind() != ErrorKind.FORBIDDEN) {
                throw unavailable;
            }
            // Resolve before looking up the id so an unavailable scope reveals no existence.
            throw CommandReadViews.personnelNotFound();
        }
        Optional<CommandReadQueries.Personnel> row = queries.personnel(visible, id);
        if (row.isPresent()) {
            return Personnel.of(row.get());
        }
        Optional<EmployeeId> employee = queries.employeeOf(id);
        if (employee.isPresent()) {
            audit.recordIndependently(new AuditEntry("organization.workforce.personnel-command.denied", currentUser.userId(), null,
                    AuditSubject.of(AuditSubjectKind.EMPLOYEE, employee.get().value()), "outside the scope of the caller"));
        }
        throw CommandReadViews.personnelNotFound();
    }
}
