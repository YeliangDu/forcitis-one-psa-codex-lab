package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.PositionLookup;
import com.forcitis.one.organization.workforce.domain.PositionQueries;
import org.springframework.stereotype.Component;

@Component
class PositionLookupService implements PositionLookup {
    private final PositionQueries queries;

    PositionLookupService(PositionQueries queries) {
        this.queries = queries;
    }

    @Override
    public boolean isActive(PositionId positionId) {
        return queries.isActive(positionId);
    }
}
