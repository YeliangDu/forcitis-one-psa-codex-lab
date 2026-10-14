package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.numbering.api.FindDocumentByLegacyNumber;
import com.forcitis.one.platform.numbering.api.FindDocumentsByLegacyPrefix;
import com.forcitis.one.platform.numbering.api.FindLegacyNumbersOfDocument;
import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.kernel.PageRequest;
import java.util.List;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/legacy-numbers")
class LegacyNumberController {
    private final FindDocumentByLegacyNumber findDocument;
    private final FindDocumentsByLegacyPrefix findByPrefix;
    private final FindLegacyNumbersOfDocument findLegacyNumbers;

    LegacyNumberController(FindDocumentByLegacyNumber findDocument, FindDocumentsByLegacyPrefix findByPrefix, FindLegacyNumbersOfDocument findLegacyNumbers) {
        this.findDocument = findDocument;
        this.findByPrefix = findByPrefix;
        this.findLegacyNumbers = findLegacyNumbers;
    }

    @GetMapping(params = "legacyNumber")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ + "')")
    ResponseEntity<LegacyNumberResponse> byLegacyNumber(@RequestParam DocumentType documentType,
            @Parameter(description = "Exactly one of legacyNumber, legacyNumberPrefix and documentId is given") @RequestParam(required = false) String legacyNumber) {
        return findDocument.find(documentType, legacyNumber)
                .map(LegacyNumberResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_LEGACY_NUMBER_NOT_FOUND, ErrorKind.NOT_FOUND,
                        "no document carries " + legacyNumber));
    }

    /** Original numbers by their beginning (at least 2 characters), for the person who remembers only the start of a number. */
    @GetMapping(params = "legacyNumberPrefix")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ + "')")
    CursorPage<LegacyNumberResponse> byPrefix(@RequestParam DocumentType documentType, @Parameter(description = "Exactly one of legacyNumber, legacyNumberPrefix and documentId is given") @RequestParam(required = false) String legacyNumberPrefix,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        PageRequest page = PageRequest.of(limit, cursor, "legacy-number-prefix");
        List<LegacyNumberResponse> found = findByPrefix.findByPrefix(documentType, legacyNumberPrefix, page.cursorValue(), page.limit() + 1)
                .stream().map(LegacyNumberResponse::from).toList();
        List<LegacyNumberResponse> items = found.stream().limit(page.limit()).toList();
        return CursorPage.of(items, found.size() > items.size() ? PageRequest.encode("legacy-number-prefix", items.getLast().legacyNumber()) : null);
    }

    @GetMapping(params = "documentId")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ + "')")
    CursorPage<LegacyNumberResponse> byDocument(@RequestParam DocumentType documentType, @Parameter(description = "Exactly one of legacyNumber, legacyNumberPrefix and documentId is given") @RequestParam(required = false) Long documentId,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        List<LegacyNumberResponse> all = findLegacyNumbers.find(new DocumentReference(documentType, new DocumentId(Long.toString(documentId))))
                .stream().map(LegacyNumberResponse::from).toList();
        return PageRequest.of(limit, cursor, "legacy-numbers").slice(all, LegacyNumberResponse::legacyNumber, "legacy-numbers");
    }
}
