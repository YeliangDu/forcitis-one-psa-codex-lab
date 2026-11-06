package com.forcitis.one;

import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.TimeOrderedIds;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalRequestId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

/** Readable names for the ids of approval tests: the same name always gives the same id. */
public final class TestApprovalIds {

    private TestApprovalIds() {
    }

    public static IdGenerator generator() {
        return new TimeOrderedIds(new com.forcitis.one.fixture.doubles.SteppingClock());
    }

    public static ApprovalRequestId requestId() {
        return new ApprovalRequestId(generator().next());
    }

    public static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    public static ApprovalTaskId task(String name) {
        return new ApprovalTaskId(uuid(name));
    }

    public static ApprovalInstanceId instance(String name) {
        return new ApprovalInstanceId(uuid(name));
    }

    public static DocumentId document(String name) {
        return new DocumentId(uuid(name));
    }
}
