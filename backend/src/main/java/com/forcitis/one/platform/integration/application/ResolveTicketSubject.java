package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.api.TicketSubjects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class ResolveTicketSubject implements TicketSubjects {

    private static final Logger log = LoggerFactory.getLogger(ResolveTicketSubject.class);
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
            log.warn("A signed ticket subject is not a signed 64-bit number; no employee is looked up");
            return Optional.empty();
        }
        if (!Long.toString(sourceId).equals(subject)) {
            log.warn("A signed ticket subject is not in canonical form (sign, leading zeros); no employee is looked up");
            return Optional.empty();
        }
        return references.findOwnId(ExternalSource.ORG_SOURCE, SourceObjectKind.EMPLOYEE, sourceId).map(EmployeeId::new);
    }
}
