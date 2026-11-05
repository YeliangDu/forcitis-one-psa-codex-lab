package com.forcitis.one.organization.attendance.domain;

import com.forcitis.one.kernel.LegalEntityId;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface StandardHoursRepository {

    StandardHoursScopeTail tail(@Nullable LegalEntityId legalEntityId);

    Optional<StandardHours> find(UUID id);

    /** Inserts a version that was never saved; closing its predecessor is done by the database. */
    void insert(StandardHours version);

    /** Writes the voiding; reopening the predecessor is done by the database. */
    void saveVoided(StandardHours version);
}
