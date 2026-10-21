package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.numbering.api.FindDocumentByLegacyNumber;
import com.forcitis.one.platform.numbering.api.FindDocumentsByLegacyPrefix;
import com.forcitis.one.platform.numbering.api.FindLegacyNumbersOfDocument;
import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.kernel.PageRequest;
import java.util.List;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
class LegacyNumberController {
    private final FindDocumentByLegacyNumber findDocument;
    private final FindDocumentsByLegacyPrefix findByPrefix;
    private final FindLegacyNumbersOfDocument findLegacyNumbers;

    LegacyNumberController(FindDocumentByLegacyNumber findDocument, FindDocumentsByLegacyPrefix findByPrefix, FindLegacyNumbersOfDocument findLegacyNumbers) {
        this.findDocument = findDocument;
        this.findByPrefix = findByPrefix;
        this.findLegacyNumbers = findLegacyNumbers;
    }

    /** The document that carries exactly this original number. */
    @GetMapping("/legacy-numbers:resolve")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ + "')")
    ResponseEntity<LegacyNumberResponse> resolve(@RequestParam ExternalSource sourceSystem, @RequestParam DocumentTypeCode documentType, @RequestParam String legacyNumber) {
        return findDocument.find(sourceSystem, documentType, legacyNumber)
                .map(LegacyNumberResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_LEGACY_NUMBER_NOT_FOUND, ErrorKind.NOT_FOUND,
                        "no document carries " + legacyNumber));
    }

    /**
     * Original numbers of one document type, either by their beginning (at least 2 characters, for the person who remembers only the start
     * of a number) or by the document that carries them. Exactly one of the two is given.
     */
    @GetMapping("/legacy-numbers")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ + "')")
    CursorPage<LegacyNumberResponse> list(@RequestParam DocumentTypeCode documentType,
            @Parameter(description = "Required with legacyNumberPrefix: original numbers are unique per source system") @RequestParam(required = false) ExternalSource sourceSystem,
            @Parameter(description = "Exactly one of legacyNumberPrefix and documentId is given") @RequestParam(required = false) String legacyNumberPrefix,
            @Parameter(description = "Exactly one of legacyNumberPrefix and documentId is given") @RequestParam(required = false) UUID documentId,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        if ((legacyNumberPrefix == null) == (documentId == null)) {
            throw new InvalidRequestException("give exactly one of legacyNumberPrefix and documentId");
        }
        if (documentId != null) {
            List<LegacyNumberResponse> all = findLegacyNumbers.find(new DocumentReference(documentType, new DocumentId(documentId)))
                    .stream().map(LegacyNumberResponse::from).toList();
            return PageRequest.of(limit, cursor, "legacy-numbers").slice(all, LegacyNumberResponse::legacyNumber, "legacy-numbers");
        }
        if (sourceSystem == null) {
            throw new InvalidRequestException("a prefix search names the sourceSystem whose original numbers it searches");
        }
        PageRequest page = PageRequest.of(limit, cursor, "legacy-number-prefix");
        List<LegacyNumberResponse> found = findByPrefix.findByPrefix(sourceSystem, documentType, legacyNumberPrefix, page.cursorValue(), page.limit() + 1)
                .stream().map(LegacyNumberResponse::from).toList();
        List<LegacyNumberResponse> items = found.stream().limit(page.limit()).toList();
        return CursorPage.of(items, found.size() > items.size() ? PageRequest.encode("legacy-number-prefix", items.getLast().legacyNumber()) : null);
    }
}
