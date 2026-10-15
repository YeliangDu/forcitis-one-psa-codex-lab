package com.forcitis.one.platform.integration.infrastructure.seeyon;

/** A unit with its parent and, when another unit holds the same place in the tree, that unit; all under the source's own ids. */
record PlacedUnit(OrgUnitRow row, Long parentId, Long duplicateOfId) {
}
