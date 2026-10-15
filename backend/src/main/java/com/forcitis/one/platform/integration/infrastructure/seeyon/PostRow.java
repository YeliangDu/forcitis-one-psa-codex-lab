package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.platform.integration.api.SourceRecordState;

/** A position as the view has it, under the source's own id. */
record PostRow(long postId, String code, String name, Integer sortOrder, SourceRecordState state) {
}
