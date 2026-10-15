package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.LegalEntityView;
import java.util.UUID;

record LegalEntityResponse(UUID legalEntityId, String code, String name, String status) {
    static LegalEntityResponse of(LegalEntityView view) {
        return new LegalEntityResponse(view.legalEntityId().value(), view.code(), view.name(), view.status());
    }
}
