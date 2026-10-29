package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.NumberingRuleView;
import com.forcitis.one.platform.numbering.api.RuleVersion;
import com.forcitis.one.platform.numbering.application.ConfigureNumberingRule;
import com.forcitis.one.platform.numbering.application.ConfirmNumberingRule;
import com.forcitis.one.platform.numbering.application.ConfirmNumberingRuleByFinance;
import com.forcitis.one.platform.numbering.application.FindNumberingRule;
import com.forcitis.one.platform.numbering.application.ListNumberingRules;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/numbering-rules")
class NumberingRuleController {

    private final ConfigureNumberingRule configure;
    private final ConfirmNumberingRule confirm;
    private final ConfirmNumberingRuleByFinance confirmByFinance;
    private final ListNumberingRules list;
    private final FindNumberingRule find;
    private final CurrentUser currentUser;

    NumberingRuleController(ConfigureNumberingRule configure, ConfirmNumberingRule confirm, ConfirmNumberingRuleByFinance confirmByFinance,
            ListNumberingRules list, FindNumberingRule find, CurrentUser currentUser) {
        this.configure = configure;
        this.confirm = confirm;
        this.confirmByFinance = confirmByFinance;
        this.list = list;
        this.find = find;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_READ + "')")
    CursorPage<NumberingRuleResponse> list(@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        CursorPage<NumberingRuleView> page = list.execute(PageRequest.of(limit, cursor, "numbering-rules"));
        return CursorPage.of(page.items().stream().map(NumberingRuleResponse::from).toList(), page.nextCursor());
    }

    @GetMapping(path = "/{documentType}")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_READ + "')")
    ResponseEntity<NumberingRuleResponse> get(@PathVariable DocumentTypeCode documentType) {
        NumberingRuleView rule = find.execute(documentType).orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_NOT_FOUND,
                ErrorKind.NOT_FOUND, "no numbering rule for " + documentType));
        return answer(rule);
    }

    @PutMapping(path = "/{documentType}")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE + "')")
    ResponseEntity<NumberingRuleResponse> put(@PathVariable DocumentTypeCode documentType, @RequestBody NumberingRuleRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        if (request.resetPolicy() == null) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_INVALID, ErrorKind.UNPROCESSABLE, "the reset policy is missing");
        }
        return answer(configure.execute(documentType, ExpectedVersion.fromHeader(ifMatch), request.pattern(), request.resetPolicy(),
                request.startValue(), required(request.effectiveFrom()), currentUser.userId()));
    }

    @PostMapping(path = "/{documentType}:confirm")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE + "')")
    ResponseEntity<NumberingRuleResponse> confirm(@PathVariable DocumentTypeCode documentType, @RequestBody ConfirmNumberingRuleRequest request) {
        return answer(confirm.execute(documentType, currentUser.userId(), seen(request)));
    }

    @PostMapping(path = "/{documentType}:confirm-finance")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_VOUCHER_RULE_APPROVE + "')")
    ResponseEntity<NumberingRuleResponse> confirmByFinance(@PathVariable DocumentTypeCode documentType, @RequestBody ConfirmNumberingRuleRequest request) {
        return answer(confirmByFinance.execute(documentType, currentUser.userId(), seen(request)));
    }

    private static RuleVersion seen(ConfirmNumberingRuleRequest request) {
        if (request == null || request.templateVersion() == null || request.templateVersion() < 1) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_INVALID, ErrorKind.UNPROCESSABLE, "templateVersion, the version you looked at, is required");
        }
        return new RuleVersion(request.templateVersion());
    }

    private static LocalDate required(LocalDate effectiveFrom) {
        if (effectiveFrom == null) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_INVALID, ErrorKind.UNPROCESSABLE, "effectiveFrom, the day the template starts, is required");
        }
        return effectiveFrom;
    }


    private static ResponseEntity<NumberingRuleResponse> answer(NumberingRuleView rule) {
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(rule.version())).body(NumberingRuleResponse.from(rule));
    }
}
