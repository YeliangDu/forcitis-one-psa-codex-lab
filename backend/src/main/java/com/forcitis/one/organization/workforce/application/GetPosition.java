package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.domain.PositionQueries;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GetPosition {
    private final PositionQueries queries;

    GetPosition(PositionQueries queries) {
        this.queries = queries;
    }

    public Optional<PositionView> execute(PositionId position) {
        return queries.find(position).map(PositionView::of);
    }
}
