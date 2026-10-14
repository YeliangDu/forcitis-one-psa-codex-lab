package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.ProcessKey;

/** instanceId is chosen by the approval module before the engine starts, so the request already knows its round while tasks are created. */
public record InstanceStart(ApprovalInstanceId instanceId, ProcessKey processKey, DocumentReference document, LegalEntityId legalEntityId,
                            UserId initiator, ContentHash contentHash, ProcessVariables variables) {
}
