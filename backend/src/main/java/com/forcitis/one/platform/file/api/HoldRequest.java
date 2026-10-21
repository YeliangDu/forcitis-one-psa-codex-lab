package com.forcitis.one.platform.file.api;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import java.util.Objects;

public record HoldRequest(FileId file, DocumentReference initiator, UserId heldBy, Reason reason) {

    public HoldRequest {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(heldBy, "heldBy");
        Objects.requireNonNull(reason, "reason");
    }
}
