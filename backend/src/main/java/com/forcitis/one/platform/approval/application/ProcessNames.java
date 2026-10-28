package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The names a person reads for the processes of what one read shows, each key asked of the engine once. A decided task outlives the
 * deployment of its process, so a key whose definition is no longer deployed has no name: the read shows it without one and does not
 * fail; any other refusal of the engine is passed on.
 */
final class ProcessNames {

    private static final Logger log = LoggerFactory.getLogger(ProcessNames.class);

    private final ApprovalEngine engine;
    private final Map<ProcessKey, Optional<ProcessName>> known = new HashMap<>();

    ProcessNames(ApprovalEngine engine) {
        this.engine = engine;
    }

    Optional<ProcessName> of(ProcessKey processKey) {
        return known.computeIfAbsent(processKey, this::lookUp);
    }

    private Optional<ProcessName> lookUp(ProcessKey processKey) {
        try {
            return Optional.of(engine.processNameOf(processKey));
        } catch (DomainException refused) {
            if (refused.errorCode() != ApprovalError.PLATFORM_APPROVAL_PROCESS_NOT_DEPLOYED) {
                throw refused;
            }
            log.warn("platform.approval process {} is no longer deployed: its tasks are shown without a process name", processKey.key());
            return Optional.empty();
        }
    }
}
