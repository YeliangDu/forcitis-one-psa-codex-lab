package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.LegalEntityView;
import org.jspecify.annotations.Nullable;

record LegalEntityResponse(String legalEntityId, String code, String name, String status, @Nullable Long accountingOrganizationNumber) {
    static LegalEntityResponse of(LegalEntityView view) {
        return new LegalEntityResponse(String.valueOf(view.legalEntityId()), view.code(), view.name(), view.status(), view.accountingOrganizationNumber());
    }
}
