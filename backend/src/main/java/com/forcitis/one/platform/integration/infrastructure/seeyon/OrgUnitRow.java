package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceRegionKind;
import java.util.List;

/**
 * A unit as the view has it, under the source's own id; only {@link SourceIdTranslation} turns that into an own id. type is the raw
 * dbo.ORG_UNIT.TYPE, checked by SeeyonOrgTree and never handed out.
 */
record OrgUnitRow(long unitId, String code, String name, String path, Integer sortOrder, SourceRecordState state,
                  List<String> emptyFlags, SourceRegionKind regionKind, String rawRegion, boolean deliveryUnit, String type) {
}
