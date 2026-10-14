package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import com.forcitis.one.platform.numbering.api.RuleVersion;
import com.forcitis.one.platform.numbering.application.ConfigureNumberingRule;
import com.forcitis.one.platform.numbering.application.ConfirmNumberingRule;
import com.forcitis.one.platform.numbering.application.ConfirmNumberingRuleByFinance;
import com.forcitis.one.platform.numbering.application.FindNumberingRule;
import com.forcitis.one.platform.numbering.application.ListNumberingRules;
import java.security.Principal;
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
@RequestMapping("/api/v1/platform/numbering-rules")
class NumberingRuleController {

    private final ConfigureNumberingRule configure;
    private final ConfirmNumberingRule confirm;
    private final ConfirmNumberingRuleByFinance confirmByFinance;
    private final ListNumberingRules list;
    private final FindNumberingRule find;

    NumberingRuleController(ConfigureNumberingRule configure, ConfirmNumberingRule confirm, ConfirmNumberingRuleByFinance confirmByFinance,
            ListNumberingRules list, FindNumberingRule find) {
        this.configure = configure;
        this.confirm = confirm;
        this.confirmByFinance = confirmByFinance;
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
    ResponseEntity<Void> put(@PathVariable DocumentType documentType, @RequestBody NumberingRuleRequest request, Principal caller) {
        configure.execute(documentType, request.pattern(), parse(request.resetPolicy()), request.startValue(), required(request.effectiveFrom()), user(caller));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{documentType}:confirm")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE + "')")
    NumberingRuleResponse confirm(@PathVariable DocumentType documentType, @RequestBody ConfirmNumberingRuleRequest request, Principal caller) {
        return NumberingRuleResponse.from(confirm.execute(documentType, user(caller), seen(request)));
    }

    @PostMapping(path = "/{documentType}:confirm-finance")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_VOUCHER_RULE_APPROVE + "')")
    NumberingRuleResponse confirmByFinance(@PathVariable DocumentType documentType, @RequestBody ConfirmNumberingRuleRequest request, Principal caller) {
        return NumberingRuleResponse.from(confirmByFinance.execute(documentType, user(caller), seen(request)));
    }

    private static RuleVersion seen(ConfirmNumberingRuleRequest request) {
        if (request == null || request.templateVersion() == null || request.templateVersion() < 1) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_INVALID, ErrorKind.UNPROCESSABLE, "templateVersion, the version you looked at, is required");
        }
        return new RuleVersion(request.templateVersion());
    }

    private static java.time.LocalDate required(java.time.LocalDate effectiveFrom) {
        if (effectiveFrom == null) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_INVALID, ErrorKind.UNPROCESSABLE, "effectiveFrom, the day the template starts, is required");
        }
        return effectiveFrom;
    }

    private static UserId user(Principal caller) {
        return new UserId(Long.parseLong(caller.getName()));
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
