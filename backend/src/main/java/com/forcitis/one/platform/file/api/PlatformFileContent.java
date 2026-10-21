package com.forcitis.one.platform.file.api;

import com.forcitis.one.kernel.DocumentReference;
import java.io.OutputStream;

/** Platform modules only (guarded by an architecture test); business modules hand out download links instead. */
public interface PlatformFileContent {

    /** Verifies the hash while it streams; a mismatch fails the read. requester is audited. */
    void readContent(FileId file, DocumentReference document, FileSubject requester, OutputStream sink);
}
