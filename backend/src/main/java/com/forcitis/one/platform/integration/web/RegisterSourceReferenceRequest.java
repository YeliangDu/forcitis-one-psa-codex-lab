package com.forcitis.one.platform.integration.web;

import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** sourceId is a decimal string: a 64-bit id does not fit a JSON number. */
record RegisterSourceReferenceRequest(ExternalSource sourceSystem, SourceObjectKind sourceObjectKind, String sourceId, UUID ownId,
                                      @Nullable LocalDate effectiveFrom) {
}
