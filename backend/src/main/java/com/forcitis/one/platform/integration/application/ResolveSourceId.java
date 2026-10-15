package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.IntegrationError;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.domain.SourceReferenceQueries;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fails and alerts when no confirmed reference is in effect on the day: nothing is sent to an external system without its own id for the object. */
@Service
class ResolveSourceId {
    private static final Logger LOG = LoggerFactory.getLogger(ResolveSourceId.class);

    private final SourceReferenceQueries queries;

    ResolveSourceId(SourceReferenceQueries queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public SourceObjectId execute(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, OwnObjectId ownId, LocalDate on) {
        return queries.findSourceId(sourceSystem, sourceObjectKind, ownId, on).orElseThrow(() -> {
            LOG.error("ALERT platform.integration no confirmed {} reference of {} for {} in effect on {}: what concerns it is not sent",
                    sourceObjectKind, sourceSystem, ownId, on);
            return new DomainException(IntegrationError.PLATFORM_INTEGRATION_SOURCE_REFERENCE_NOT_FOUND, ErrorKind.NOT_FOUND,
                    "no confirmed " + sourceObjectKind + " reference of " + sourceSystem + " in effect on " + on);
        });
    }
}
