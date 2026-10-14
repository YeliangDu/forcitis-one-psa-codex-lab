package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.time.LocalDate;

/** submittedOn is the business date of the round start; every node deadline counts from it. */
public record InstanceStart(ProcessKey processKey, DocumentReference document, LegalEntityId legalEntityId, UserId initiator,
                            LocalDate submittedOn, String contentDigest, ProcessVariables variables) {
}
