package com.forcitis.one.platform.audit.application;

import com.forcitis.one.platform.audit.domain.OwnLoginRecord;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record OwnLoginRecordView(Instant occurredAt, String action, @Nullable String outcome, @Nullable String sourceAddress,
                                 @Nullable String loginMethod) {

    static OwnLoginRecordView of(OwnLoginRecord record) {
        return new OwnLoginRecordView(record.occurredAt(), record.action(), record.outcome() == null ? null : record.outcome().name(),
                record.sourceAddress(), record.loginMethod());
    }
}
