package com.forcitis.one.platform.file.application;

import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.HoldRequest;
import com.forcitis.one.platform.file.domain.BindingAudit;
import com.forcitis.one.platform.file.domain.FileBindingRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** A hold is never withdrawn, so a repeat by the same document keeps the first reason and records nothing new. */
public final class HoldFile {
    private static final Logger LOG = LoggerFactory.getLogger(HoldFile.class);
    static final int REASON_LIMIT = 500;

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
        if (request.reason().value().length() > REASON_LIMIT) {
            throw new IllegalArgumentException("a hold reason is at most " + REASON_LIMIT + " characters");
        }
        transaction.executeWithoutResult(tx -> {
            bindings.lockForBinding(request.file());
            if (bindings.classification(request.file(), request.initiator()).isEmpty()) {
                throw FileError.PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT.refusal("the file is not linked to the document that holds it");
            }
            if (bindings.holdReason(request.file(), request.initiator()).isPresent()) {
                LOG.info("file {} is already held by this document; the first reason stands", request.file());
                return;
            }
            bindings.insertHold(request.file(), request.initiator(), request.heldBy(), request.reason(), clock.instant());
            audit.held(request.file(), request.initiator(), request.heldBy());
        });
    }
}
