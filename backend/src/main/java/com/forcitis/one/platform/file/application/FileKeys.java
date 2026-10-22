package com.forcitis.one.platform.file.application;

import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.integration.api.StoredObjectKey;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Object keys carry no file name, document number or person name (ADR-0060 section 3); the staging key follows from id and source. */
public record FileKeys(String environmentPrefix) {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    public FileKeys {
        if (environmentPrefix == null || !environmentPrefix.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("the environment prefix is lower-case letters, digits and hyphens");
        }
    }

    public StoredObjectKey formal(FileId id, Instant storedAt) {
        return new StoredObjectKey(environmentPrefix + "/files/" + MONTH.format(storedAt) + "/" + id);
    }

    public StoredObjectKey staging(FileId id, FileSubjectKind source) {
        return new StoredObjectKey(environmentPrefix + "/staging/" + source.name().toLowerCase(Locale.ROOT) + "/" + id);
    }

    public StoredObjectKey quarantine(FileId id) {
        return new StoredObjectKey(environmentPrefix + "/quarantine/" + id);
    }
}
