package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A separation request as stored (spec 4.2). Cross-row rules (a system-only basis for SYSTEM requests, the four dismissal checks before approval, the linked SEPARATE
 * command) are in tg_separation_requests_change and in the WC3 commands. The notes carry no health or pregnancy content (ADR-0079 invariant 10).
 */
public record SeparationRequest(SeparationRequestId id, EmploymentRelationshipId relationshipId, EmployeeId employeeId, SeparationInitiator initiator, String basisKey,
                                SeparationRequestSource source, UserId requestedBy, @Nullable UserId decidedBy, LocalDate requestedOn, @Nullable LocalDate approvedOn,
                                @Nullable LocalDate lastWorkingDate, @Nullable LocalDate handoverCompletedOn, @Nullable UUID agreementFileId,
                                @Nullable LocalDate protectedCircumstancesCheckedOn, @Nullable LocalDate unionNotifiedOn, @Nullable String unionOpinionNote,
                                @Nullable String unionNoticeResultNote, @Nullable String note, @Nullable PersonnelCommandId personnelCommandId,
                                SeparationRequestStatus status, long version) {
    public static final int MAX_NOTE_LENGTH = 500;

    public SeparationRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(relationshipId, "relationshipId");
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(basisKey, "basisKey");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(requestedOn, "requestedOn");
        Objects.requireNonNull(status, "status");
        for (String text : new String[] {unionOpinionNote, unionNoticeResultNote, note}) {
            if (text != null && (text.isBlank() || text.length() > MAX_NOTE_LENGTH)) {
                throw new IllegalArgumentException("a note has 1 to " + MAX_NOTE_LENGTH + " characters");
            }
        }
    }
}
