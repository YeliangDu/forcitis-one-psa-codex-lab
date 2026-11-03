package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.events.LegalEntityActivated;
import com.forcitis.one.organization.workforce.events.LegalEntityCodeCorrected;
import com.forcitis.one.organization.workforce.events.LegalEntityDeactivated;
import com.forcitis.one.organization.workforce.events.LegalEntityRegistered;
import com.forcitis.one.organization.workforce.events.LegalEntityRenamed;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A legal entity maintained in this system. The code feeds {LE} in document numbers, so it can be corrected only while no number
 * has been issued for the entity.
 */
public final class LegalEntity extends AggregateRoot {
    public static final int MAX_NAME_LENGTH = 100;

    public static final StateMachine<LegalEntityStatus> MACHINE = StateMachine.of(LegalEntityStatus.class)
            .allow(LegalEntityStatus.ACTIVE, LegalEntityStatus.INACTIVE)
            .allow(LegalEntityStatus.INACTIVE, LegalEntityStatus.ACTIVE)
            .build();

    private final LegalEntityId legalEntityId;
    private final LegalEntityStatus loadedStatus;
    private final LegalEntityCode loadedCode;
    private LegalEntityCode code;
    private String name;
    private LegalEntityStatus status;
    private @Nullable EmployeeId financeHead;

    private LegalEntity(LegalEntityId legalEntityId, LegalEntityCode code, String name, LegalEntityStatus status, @Nullable EmployeeId financeHead, long version,
                        long eventSequence) {
        super(version, eventSequence);
        this.legalEntityId = legalEntityId;
        this.code = Objects.requireNonNull(code, "code");
        this.loadedCode = code;
        this.name = requireName(name);
        this.status = Objects.requireNonNull(status, "status");
        this.loadedStatus = status;
        this.financeHead = financeHead;
    }

    /** A legal entity about to be inserted; its id is made by the caller, never by the repository. */
    public static LegalEntity register(LegalEntityId legalEntityId, LegalEntityCode code, String name) {
        LegalEntity entity = new LegalEntity(Objects.requireNonNull(legalEntityId, "legalEntityId"), code, name, LegalEntityStatus.ACTIVE, null, 0, 0);
        entity.record((envelopes, version) -> new LegalEntityRegistered(envelopes.wrap(entity.draft("LegalEntityRegistered", version)),
                entity.legalEntityId, entity.code));
        return entity;
    }

    public static LegalEntity restore(LegalEntityId legalEntityId, LegalEntityCode code, String name, String status,
                                      @Nullable EmployeeId financeHead, long version, long eventSequence) {
        return new LegalEntity(Objects.requireNonNull(legalEntityId, "legalEntityId"), code, name, LegalEntityStatus.valueOf(status),
                financeHead, version, eventSequence);
    }

    public LegalEntityId legalEntityId() {
        return legalEntityId;
    }

    public LegalEntityCode code() {
        return code;
    }

    public LegalEntityCode loadedCode() {
        return loadedCode;
    }

    public String name() {
        return name;
    }

    public LegalEntityStatus status() {
        return status;
    }

    public LegalEntityStatus loadedStatus() {
        return loadedStatus;
    }

    /** The finance head in force today, read from the open version; empty until one is appointed. */
    public @Nullable EmployeeId financeHead() {
        return financeHead;
    }

    /** Appoints or, with null, clears the finance head; false when it is already that person, so nothing is saved or audited. */
    public boolean changeFinanceHead(@Nullable EmployeeId head) {
        if (Objects.equals(head, financeHead)) {
            return false;
        }
        this.financeHead = head;
        return true;
    }

    public void rename(String newName) {
        String trimmed = requireName(newName);
        if (trimmed.equals(name)) {
            return;
        }
        this.name = trimmed;
        record((envelopes, version) -> new LegalEntityRenamed(envelopes.wrap(draft("LegalEntityRenamed", version)), legalEntityId));
    }

    public void activate() {
        this.status = MACHINE.require(status, LegalEntityStatus.ACTIVE, WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_TRANSITION_ILLEGAL);
        record((envelopes, version) -> new LegalEntityActivated(envelopes.wrap(draft("LegalEntityActivated", version)), legalEntityId));
    }

    public void deactivate() {
        this.status = MACHINE.require(status, LegalEntityStatus.INACTIVE, WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_TRANSITION_ILLEGAL);
        record((envelopes, version) -> new LegalEntityDeactivated(envelopes.wrap(draft("LegalEntityDeactivated", version)), legalEntityId));
    }

    /** Sets the wanted status by name: asking for the status it already has changes nothing. */
    public void declareStatus(String wanted) {
        LegalEntityStatus target;
        try {
            target = LegalEntityStatus.valueOf(Objects.requireNonNull(wanted, "status"));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_STATUS_UNKNOWN, ErrorKind.UNPROCESSABLE,
                    "status must be ACTIVE or INACTIVE");
        }
        if (target == status) {
            return;
        }
        if (target == LegalEntityStatus.ACTIVE) {
            activate();
        } else {
            deactivate();
        }
    }

    /** numbersIssued: a document number carrying the old code exists, so the code is fixed for good. */
    public void correctCode(LegalEntityCode corrected, boolean numbersIssued) {
        Objects.requireNonNull(corrected, "corrected");
        if (corrected.equals(code)) {
            return;
        }
        if (numbersIssued) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_LOCKED, ErrorKind.CONFLICT,
                    "document numbers carrying " + code.value() + " have been issued; the code can no longer be corrected");
        }
        LegalEntityCode previous = code;
        this.code = corrected;
        record((envelopes, version) -> new LegalEntityCodeCorrected(envelopes.wrap(draft("LegalEntityCodeCorrected", version)),
                legalEntityId, previous, corrected));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NAME_INVALID, ErrorKind.UNPROCESSABLE,
                    "a legal entity name is 1 to " + MAX_NAME_LENGTH + " characters");
        }
        return name.trim();
    }

    private EventDraft draft(String eventName, long version) {
        return new EventDraft("organization.workforce." + eventName, 1, "LegalEntity", legalEntityId.value(), version,
                legalEntityId, null, List.of());
    }
}
