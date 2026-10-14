package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.numbering.api.DocumentType;
import com.forcitis.one.platform.numbering.application.ConfigureNumberingRule;
import com.forcitis.one.platform.numbering.application.FindNumberingRule;
import com.forcitis.one.platform.numbering.application.ListNumberingRules;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/{version}/platform/numbering-rules")
class NumberingRuleController {

    private final ConfigureNumberingRule configure;
    private final ListNumberingRules list;
    private final FindNumberingRule find;

    NumberingRuleController(ConfigureNumberingRule configure, ListNumberingRules list, FindNumberingRule find) {
        this.configure = configure;
        this.list = list;
        this.find = find;
    }

    @GetMapping(version = "1")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_READ + "')")
    List<NumberingRuleResponse> list() {
        return list.execute().stream().map(NumberingRuleResponse::from).toList();
    }

    @GetMapping(path = "/{documentType}", version = "1")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_READ + "')")
    ResponseEntity<NumberingRuleResponse> get(@PathVariable DocumentType documentType) {
        return find.execute(documentType)
                .map(NumberingRuleResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
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
