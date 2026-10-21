package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.integration.api.ExternalSource;
import java.util.Optional;

public interface FindDocumentByLegacyNumber {
    Optional<LegacyNumberEntry> find(ExternalSource sourceSystem, DocumentTypeCode type, String legacyNumber);

}
