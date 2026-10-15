package com.forcitis.one.platform.integration.web;

import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.application.SourceReferenceView;
import com.forcitis.one.platform.integration.api.ConfirmationStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record SourceReferenceResponse(UUID sourceReferenceId, ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, String sourceId, UUID ownId,
                               ConfirmationStatus confirmationStatus, @Nullable LocalDate effectiveFrom, @Nullable LocalDate effectiveTo) {

    static SourceReferenceResponse of(SourceReferenceView view) {
        return new SourceReferenceResponse(view.sourceReferenceId(), view.sourceSystem(), view.sourceObjectKind(), Long.toString(view.sourceId()),
                view.ownId(), view.confirmationStatus(), view.effectiveFrom(), view.effectiveTo());
    }
}
