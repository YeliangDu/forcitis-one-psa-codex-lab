package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceRegionKind;
import com.forcitis.one.platform.integration.api.SourceUnitId;
import java.util.List;

record OrgUnitRow(SourceUnitId unitId, String code, String name, String path, Integer sortOrder, SourceRecordState state,
                  List<String> emptyFlags, SourceRegionKind regionKind, String rawRegion, boolean deliveryUnit) {
}
