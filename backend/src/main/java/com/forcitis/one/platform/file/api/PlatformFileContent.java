package com.forcitis.one.platform.file.api;

import com.forcitis.one.kernel.DocumentReference;
import java.io.OutputStream;

/** Platform modules only (guarded by an architecture test); business modules hand out download links instead. */
public interface PlatformFileContent {

    /**
     * Verifies the hash while it streams; a mismatch fails the read. Whenever this throws, the sink may already hold part or all of the
     * bytes and the caller discards them. A failing sink surfaces as an UncheckedIOException, never as a storage fault. requester is audited.
     */
    void readContent(FileId file, DocumentReference document, FileSubject requester, OutputStream sink);
}
