package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.numbering.api.NumberingError;
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
@RequestMapping("/api/v1/platform/numbering-rules")
class NumberingRuleController {

    private final ConfigureNumberingRule configure;
    private final ListNumberingRules list;
    private final FindNumberingRule find;

    NumberingRuleController(ConfigureNumberingRule configure, ListNumberingRules list, FindNumberingRule find) {
        this.configure = configure;
        this.list = list;
        this.find = find;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_READ + "')")
    List<NumberingRuleResponse> list() {
        return list.execute().stream().map(NumberingRuleResponse::from).toList();
    }

    @GetMapping(path = "/{documentType}")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_READ + "')")
    ResponseEntity<NumberingRuleResponse> get(@PathVariable DocumentType documentType) {
        return find.execute(documentType)
                .map(NumberingRuleResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(path = "/{documentType}")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE + "')")
    ResponseEntity<Void> put(@PathVariable DocumentType documentType, @RequestBody NumberingRuleRequest request) {
        configure.execute(documentType, request.pattern(), parse(request.resetPolicy()), request.startValue());
        return ResponseEntity.noContent().build();
    }

    private static ResetPolicy parse(String resetPolicy) {
        try {
            return ResetPolicy.valueOf(resetPolicy);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_INVALID, ErrorKind.UNPROCESSABLE,
                    "reset policy must be one of " + java.util.Arrays.toString(ResetPolicy.values()));
        }
    }
}
