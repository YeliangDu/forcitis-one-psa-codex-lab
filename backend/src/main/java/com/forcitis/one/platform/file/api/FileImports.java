package com.forcitis.one.platform.file.api;

/** The migration orchestration only (guarded by an architecture test, ADR-0060 section 7 and ADR-0066 section 8). */
public interface FileImports {

    ImportResult importFile(ImportRequest request);
}
