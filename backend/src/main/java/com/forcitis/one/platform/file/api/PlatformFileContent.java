package com.forcitis.one.platform.file.api;

import com.forcitis.one.kernel.DocumentReference;
import java.io.OutputStream;

/** Platform modules and the migration only (guarded by an architecture test); business modules hand out download links instead. */
public interface PlatformFileContent {

    /** Verifies the hash while it streams; a mismatch fails the read. */
    void readContent(FileId file, DocumentReference document, OutputStream sink);

    ImportResult importFile(ImportRequest request);
}
