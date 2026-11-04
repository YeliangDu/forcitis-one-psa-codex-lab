package com.forcitis.one.organization.attendance.domain;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A null legalEntityId is the DEFAULT scope. */
public interface StandardHoursQueries {

    /** Newest first, voided versions included. */
    List<StandardHoursVersionRecord> list(@Nullable LegalEntityId legalEntityId);

    boolean hasVersionInEffect(@Nullable LegalEntityId legalEntityId);

    Optional<StandardHoursVersionRecord> inEffectOn(@Nullable LegalEntityId legalEntityId, LocalDate date);
}
