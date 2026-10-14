package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.NumberingRuleView;
import com.forcitis.one.platform.numbering.application.ConfigureNumberingRule;
import com.forcitis.one.platform.numbering.application.FindNumberingRule;
import com.forcitis.one.platform.numbering.application.ListNumberingRules;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final ListNumberingRules list;
    private final FindNumberingRule find;

    NumberingRuleController(ConfigureNumberingRule configure, ListNumberingRules list, FindNumberingRule find) {
        this.configure = configure;
        this.list = list;
        this.find = find;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_READ + "')")
    CursorPage<NumberingRuleResponse> list(@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        CursorPage<NumberingRuleView> page = list.execute(PageRequest.of(limit, cursor, "numbering-rules"));
        return CursorPage.of(page.items().stream().map(NumberingRuleResponse::from).toList(), page.nextCursor());
    }

    @GetMapping(path = "/{documentType}")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_READ + "')")
    ResponseEntity<NumberingRuleResponse> get(@PathVariable DocumentType documentType) {
        NumberingRuleView rule = find.execute(documentType).orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_NOT_FOUND,
                ErrorKind.NOT_FOUND, "no numbering rule for " + documentType));
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(rule.version())).body(NumberingRuleResponse.from(rule));
    }

    @PutMapping(path = "/{documentType}")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE + "')")
    ResponseEntity<NumberingRuleResponse> put(@PathVariable DocumentType documentType, @RequestBody NumberingRuleRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        if (request.resetPolicy() == null) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_INVALID, ErrorKind.UNPROCESSABLE, "the reset policy is missing");
        }
        NumberingRuleView rule = configure.execute(documentType, ExpectedVersion.fromHeader(ifMatch), request.pattern(),
                request.resetPolicy(), request.startValue());
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(rule.version())).body(NumberingRuleResponse.from(rule));
    }
}
