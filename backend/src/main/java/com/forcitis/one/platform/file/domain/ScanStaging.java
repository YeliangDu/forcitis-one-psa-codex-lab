package com.forcitis.one.platform.file.domain;

import java.io.InputStream;

/** The local scan staging directory of this instance (ADR-0070 section 3); no link or public path ever points into it. */
public interface ScanStaging {

    /** Writes the stream once while hashing it, then syncs it to disk; more than the size limit throws TooLarge and leaves nothing. */
    StagedContent receive(InputStream content) throws TooLarge;

    /** Every exit of an upload, failures included, ends here. */
    void delete(StagedContent staged);

    final class TooLarge extends Exception {
        public TooLarge(long limit) {
            super("the content is larger than " + limit + " bytes");
        }
    }
}
