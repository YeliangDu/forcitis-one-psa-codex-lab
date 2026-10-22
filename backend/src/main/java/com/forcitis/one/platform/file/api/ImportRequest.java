package com.forcitis.one.platform.file.api;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.integration.api.MigrationBatchId;
import java.io.InputStream;
import java.util.Objects;

/** A file taken over from the legacy system: stored, checked and linked to its target document in one step, without events. */
public record ImportRequest(MigrationBatchId batch, InputStream content, String originalName, String declaredMediaType,
                            DocumentReference document, AccessLevel accessLevel, PersonalInformationMark personalInformation,
                            PreservationCategoryCode preservationCategory, DocumentReference preservationAnchor) {

    public ImportRequest {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(originalName, "originalName");
        Objects.requireNonNull(declaredMediaType, "declaredMediaType");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(accessLevel, "accessLevel");
        Objects.requireNonNull(personalInformation, "personalInformation");
        Objects.requireNonNull(preservationCategory, "preservationCategory");
        Objects.requireNonNull(preservationAnchor, "preservationAnchor");
    }
}
