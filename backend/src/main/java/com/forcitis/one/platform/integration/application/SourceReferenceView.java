package com.forcitis.one.platform.integration.application;

import com.forcitis.one.platform.integration.api.ConfirmationStatus;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.domain.SourceReference;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record SourceReferenceView(UUID sourceReferenceId, ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, long sourceId, UUID ownId,
                                  ConfirmationStatus confirmationStatus, @Nullable LocalDate effectiveFrom, @Nullable LocalDate effectiveTo) {

    static SourceReferenceView of(SourceReference reference) {
        return new SourceReferenceView(reference.sourceReferenceId().value(), reference.system(), reference.kind(), reference.sourceId().value(), reference.ownId().value(),
                reference.status(), reference.effectiveFrom(), reference.effectiveTo());
    }
}
