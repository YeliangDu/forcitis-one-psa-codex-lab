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

    /**
     * A file has one preservation category and anchor: the first link sets them and a later link with others is refused, as is a repeat of
     * the same file and document with another classification (an identical repeat is a no-op). A next version needs its previous version
     * linked to the same document. Only the uploader or a platform module may link it; for anyone else
     * the file does not exist. The first link copies the file in the bucket, so call it outside a transaction of your own.
     */
    void bind(BindRequest request);

    /** Append-only and irreversible: a held file cannot be disposed of within its preservation period. */
    void hold(HoldRequest request);

    /** The default validity applies when validity is null; a longer one than the configured maximum is refused. requester is audited. */
    DownloadLink issueDownloadLink(FileId file, DocumentReference document, FileSubject requester, Duration validity);

    List<StoredFileSummary> listByDocument(DocumentReference document);

    /** Applies to every file not yet disposed of whose preservation anchor is the given document; returns how many. */
    int setPreservationStart(DocumentReference preservationAnchor, LocalDate startDate);

    /** Written by the module that holds the agreed years; the longer of the category default and the agreed years counts. Returns how many files. */
    int setAgreedPreservation(DocumentReference preservationAnchor, PreservationYears agreed);

    /** One date per file, since a file has one category and anchor; empty while the start date is unknown or the category has no years yet. */
    Optional<LocalDate> expiryOf(FileId file);
}
