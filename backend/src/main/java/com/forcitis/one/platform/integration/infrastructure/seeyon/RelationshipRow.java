package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.platform.integration.api.SourceRelationshipKind;

/** A relationship as the view has it, under the source's own ids. */
record RelationshipRow(SourceRelationshipKind kind, long unitId, long memberId, boolean memberDeleted) {
}
