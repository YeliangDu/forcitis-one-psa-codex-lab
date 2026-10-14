package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.LegalEntityView;

record LegalEntityResponse(String legalEntityId, String code, String name, String status) {
    static LegalEntityResponse of(LegalEntityView view) {
        return new LegalEntityResponse(String.valueOf(view.legalEntityId()), view.code(), view.name(), view.status());
    }
}
