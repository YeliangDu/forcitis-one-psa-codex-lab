package com.forcitis.one.platform.file.domain;

import java.nio.file.Path;

public interface ContentChecks {

    /** Read only; anything it cannot tell for sure counts as containing macros. */
    ContentFacts inspect(Path stagedFile);
}
