package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.api.TicketSubjects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class ResolveTicketSubject implements TicketSubjects {

    private final SourceReferences references;

    ResolveTicketSubject(SourceReferences references) {
        this.references = references;
    }

    @Override
    public Optional<EmployeeId> employeeOf(String subject) {
        long sourceId;
        try {
            sourceId = Long.parseLong(subject);
        } catch (NumberFormatException notASixtyFourBitNumber) {
            return Optional.empty();
        }
        return references.findOwnId(ExternalSource.ORG_SOURCE, SourceObjectKind.EMPLOYEE, sourceId).map(EmployeeId::new);
    }
}
