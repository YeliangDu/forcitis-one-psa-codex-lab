package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The engine keeps ids as text; ours are the canonical text of a UUID, so anything else means the engine is not set up to use the kernel's id generator. */
final class EngineIds {

    private static final Logger log = LoggerFactory.getLogger(EngineIds.class);

    private EngineIds() {
    }

    static ApprovalTaskId task(String raw) {
        return new ApprovalTaskId(parse(raw, "task id"));
    }

    static ApprovalInstanceId instance(String raw) {
        return new ApprovalInstanceId(parse(raw, "process instance id"));
    }

    static DocumentId document(String raw) {
        return new DocumentId(parse(raw, "document id variable"));
    }

    private static java.util.UUID parse(String raw, String what) {
        try {
            return Uuids.parseCanonical(raw);
        } catch (IllegalArgumentException | NullPointerException broken) {
            log.error("ALERT platform.approval the engine produced the {} {}, which is not a canonical UUID: its id generator is not the kernel's", what, raw);
            throw new IllegalStateException("the engine's " + what + " is not a canonical UUID", broken);
        }
    }
}
