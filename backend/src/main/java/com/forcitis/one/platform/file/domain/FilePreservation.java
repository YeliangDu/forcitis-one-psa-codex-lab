package com.forcitis.one.platform.file.domain;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.file.api.PreservationCategoryCode;

/** A file keeps the category and anchor of its first link (ADR-0061 section 1). */
public record FilePreservation(PreservationCategoryCode category, DocumentReference anchor) {
}
