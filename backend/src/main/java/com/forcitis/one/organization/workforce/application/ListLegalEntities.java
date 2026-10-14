package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListLegalEntities {
    private final LegalEntityQueries legalEntities;

    ListLegalEntities(LegalEntityQueries legalEntities) {
        this.legalEntities = legalEntities;
    }

    /** Ordered by code; the cursor is the code the previous page ended with. */
    @Transactional(readOnly = true)
    public CursorPage<LegalEntityView> execute(PageRequest page) {
        List<LegalEntityView> after = legalEntities.findAll().stream().map(LegalEntityView::of)
                .filter(e -> page.cursorValue() == null || e.code().compareTo(page.cursorValue()) > 0).toList();
        List<LegalEntityView> items = after.stream().limit(page.limit()).toList();
        return CursorPage.of(items, after.size() > items.size() ? PageRequest.encode("legal-entities", items.getLast().code()) : null);
    }
}
