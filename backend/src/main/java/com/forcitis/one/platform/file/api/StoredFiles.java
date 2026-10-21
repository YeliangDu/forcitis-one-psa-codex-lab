package com.forcitis.one.platform.file.api;

import com.forcitis.one.kernel.DocumentReference;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Nobody can download a file before it is linked to a document, and the module owning that document checks its own permissions and data
 * scope before it asks for a link (ADR-0060 section 2).
 */
public interface StoredFiles {

    /** Scans in the request (ADR-0070 section 2); a file is created only once the scan decided. */
    StoreResult store(StoreRequest request);

    /** Repeating the same file and document is a no-op; a next version needs its previous version linked to the same document. */
    void bind(BindRequest request);

    /** Append-only and irreversible: a held file cannot be disposed of within its preservation period. */
    void hold(HoldRequest request);

    /** The default validity applies when validity is null; a longer one than the configured maximum is refused. */
    DownloadLink issueDownloadLink(FileId file, DocumentReference document, Duration validity);

    List<StoredFileSummary> listByDocument(DocumentReference document);

    /** Applies to every file not yet disposed of whose preservation anchor is the given document; returns how many. */
    int setPreservationStart(DocumentReference preservationAnchor, LocalDate startDate);

    /** Written by the contract module; the longer of the category default and the agreed years counts. Returns how many files. */
    int setAgreedPreservation(DocumentReference preservationAnchor, PreservationYears agreed);

    /** Empty while the start date is unknown or the category has no years yet. */
    Optional<LocalDate> expiryOf(FileId file);
}
