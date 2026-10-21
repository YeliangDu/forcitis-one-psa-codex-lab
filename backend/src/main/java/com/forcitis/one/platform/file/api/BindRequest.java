package com.forcitis.one.platform.file.api;

import com.forcitis.one.kernel.DocumentReference;
import java.util.Objects;

/**
 * The owning module classifies the file when it links it; preservationAnchor is the document whose event starts the preservation period (the
 * document itself when it has none of its own, ADR-0061 section 1).
 */
public record BindRequest(FileId file, DocumentReference document, AccessLevel accessLevel, PersonalInfoMark personalInfo,
                          PreservationCategoryCode preservationCategory, DocumentReference preservationAnchor, FileSubject boundBy) {

    public BindRequest {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(accessLevel, "accessLevel");
        Objects.requireNonNull(personalInfo, "personalInfo");
        Objects.requireNonNull(preservationCategory, "preservationCategory");
        Objects.requireNonNull(preservationAnchor, "preservationAnchor");
        Objects.requireNonNull(boundBy, "boundBy");
    }
}
