package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.LegalEntityQueries;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListLegalEntities {
    private final LegalEntityQueries legalEntities;

    ListLegalEntities(LegalEntityQueries legalEntities) {
        this.legalEntities = legalEntities;
    }

    public List<LegalEntityView> execute() {
        return legalEntities.findAll().stream().map(LegalEntityView::of).toList();
    }
}
