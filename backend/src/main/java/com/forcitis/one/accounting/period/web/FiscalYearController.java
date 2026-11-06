package com.forcitis.one.accounting.period.web;

import com.forcitis.one.accounting.period.application.OpenFiscalYear;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounting/fiscal-years")
class FiscalYearController {
    private final OpenFiscalYear open;

    FiscalYearController(OpenFiscalYear open) {
        this.open = open;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + AccountingPeriodPermissions.FISCAL_YEAR_CREATE + "')")
    OpenedFiscalYearResponse open(@RequestBody OpenFiscalYearRequest request) {
        return OpenedFiscalYearResponse.of(open.execute(request.requiredLegalEntityId(), request.requiredFiscalYear()));
    }
}
