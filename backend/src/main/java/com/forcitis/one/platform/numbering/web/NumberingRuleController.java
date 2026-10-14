package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.numbering.api.DocumentType;
import com.forcitis.one.platform.numbering.application.ConfigureNumberingRule;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/{version}/platform/numbering-rules")
class NumberingRuleController {

    private final ConfigureNumberingRule configure;

    NumberingRuleController(ConfigureNumberingRule configure) {
        this.configure = configure;
    }

    @PutMapping(path = "/{documentType}", version = "1")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE + "')")
    ResponseEntity<String> put(@PathVariable DocumentType documentType, @RequestBody NumberingRuleRequest request) {
        try {
            configure.execute(documentType, request.pattern(), ResetPolicy.valueOf(request.resetPolicy()), request.startValue());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException invalid) {
            return ResponseEntity.unprocessableContent().body(invalid.getMessage());
        }
    }
}
