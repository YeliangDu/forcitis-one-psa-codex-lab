package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.CreateLegalEntity;
import com.forcitis.one.organization.workforce.application.ListLegalEntities;
import com.forcitis.one.organization.workforce.application.UpdateLegalEntity;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/legal-entities")
class LegalEntityController {
    private final ListLegalEntities list;
    private final CreateLegalEntity create;
    private final UpdateLegalEntity update;

    LegalEntityController(ListLegalEntities list, CreateLegalEntity create, UpdateLegalEntity update) {
        this.list = list;
        this.create = create;
        this.update = update;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    List<LegalEntityResponse> list() {
        return list.execute().stream().map(LegalEntityResponse::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<LegalEntityResponse> create(@RequestBody CreateLegalEntityRequest request) {
        LegalEntityResponse body = LegalEntityResponse.of(create.execute(request.code(), request.name()));
        return ResponseEntity.created(URI.create("/api/v1/organization/legal-entities/" + body.legalEntityId())).body(body);
    }

    @PutMapping(path = "/{legalEntityId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    LegalEntityResponse update(@PathVariable long legalEntityId, @RequestBody UpdateLegalEntityRequest request) {
        return LegalEntityResponse.of(update.execute(legalEntityId, request.code(), request.name(), request.status()));
    }
}
