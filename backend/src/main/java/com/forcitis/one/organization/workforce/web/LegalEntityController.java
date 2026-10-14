package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.CreateLegalEntity;
import com.forcitis.one.organization.workforce.application.LegalEntityOutcome;
import com.forcitis.one.organization.workforce.application.ListLegalEntities;
import com.forcitis.one.organization.workforce.application.UpdateLegalEntity;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
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
    ResponseEntity<?> create(@RequestBody CreateLegalEntityRequest request, Principal caller) {
        return respond(create.execute(request.code(), request.name(), Long.parseLong(caller.getName())), true);
    }

    @PutMapping(path = "/{legalEntityId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<?> update(@PathVariable long legalEntityId, @RequestBody UpdateLegalEntityRequest request, Principal caller) {
        return respond(update.execute(legalEntityId, request.name(), request.status(), Long.parseLong(caller.getName())), false);
    }

    private static ResponseEntity<?> respond(LegalEntityOutcome outcome, boolean created) {
        return switch (outcome) {
            case LegalEntityOutcome.Saved saved -> {
                LegalEntityResponse body = LegalEntityResponse.of(saved.legalEntity());
                yield created ? ResponseEntity.created(URI.create("/api/v1/organization/legal-entities/" + body.legalEntityId())).body(body)
                        : ResponseEntity.ok(body);
            }
            case LegalEntityOutcome.NotFound notFound -> ResponseEntity.notFound().build();
            case LegalEntityOutcome.CodeTaken taken -> ResponseEntity.status(HttpStatus.CONFLICT).body("the legal entity code is already used");
            case LegalEntityOutcome.Rejected rejected -> ResponseEntity.unprocessableContent().body(rejected.reason());
        };
    }
}
