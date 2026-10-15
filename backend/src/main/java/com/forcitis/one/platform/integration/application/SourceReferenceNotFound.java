package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.integration.api.IntegrationError;
import java.util.UUID;

final class SourceReferenceNotFound extends DomainException {

    SourceReferenceNotFound(UUID sourceReferenceId) {
        super(IntegrationError.PLATFORM_INTEGRATION_SOURCE_REFERENCE_NOT_FOUND, ErrorKind.NOT_FOUND, "no source reference " + sourceReferenceId);
    }
}
