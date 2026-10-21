package com.forcitis.one.platform.file.application;

import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.HoldRequest;
import com.forcitis.one.platform.file.domain.BindingAudit;
import com.forcitis.one.platform.file.domain.FileBindingRepository;
import java.time.Clock;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** A hold is never withdrawn, so a repeat by the same document keeps the first reason and records nothing new. */
public final class HoldFile {
    private final FileBindingRepository bindings;
    private final BindingAudit audit;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public HoldFile(FileBindingRepository bindings, BindingAudit audit, Clock clock, PlatformTransactionManager transactions) {
        this.bindings = bindings;
        this.audit = audit;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    public void hold(HoldRequest request) {
        StoreContent.requireSubject(request.heldBy());
        transaction.executeWithoutResult(tx -> {
            bindings.lockForBinding(request.file());
            if (bindings.classification(request.file(), request.initiator()).isEmpty()) {
                throw FileError.PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT.refusal("the file is not linked to the document that holds it");
            }
            if (bindings.holdReason(request.file(), request.initiator()).isPresent()) {
                return;
            }
            bindings.insertHold(request.file(), request.initiator(), request.heldBy(), request.reason(), clock.instant());
            audit.held(request.file(), request.initiator(), request.heldBy());
        });
    }
}
