package com.forcitis.one.platform.audit.web;

import com.forcitis.one.platform.audit.application.OwnLoginRecordView;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record OwnLoginRecordResponse(Instant occurredAt, String action, @Nullable String outcome, @Nullable String sourceAddress,
                              @Nullable String loginMethod) {

    static OwnLoginRecordResponse of(OwnLoginRecordView view) {
        return new OwnLoginRecordResponse(view.occurredAt(), view.action(), view.outcome(), view.sourceAddress(), view.loginMethod());
    }
}
