package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import java.time.LocalDate;
import java.util.Objects;

/** One appended correction: the closed period that started on periodFrom ended on previousEffectiveTo and from now on ends on newEffectiveTo. */
public record AttributionCorrection(LocalDate periodFrom, LocalDate previousEffectiveTo, LocalDate newEffectiveTo, Reason reason, UserId correctedBy) {
    public AttributionCorrection {
        Objects.requireNonNull(periodFrom, "periodFrom");
        Objects.requireNonNull(previousEffectiveTo, "previousEffectiveTo");
        Objects.requireNonNull(newEffectiveTo, "newEffectiveTo");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(correctedBy, "correctedBy");
    }
}
