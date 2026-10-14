package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.numbering.api.FindDocumentByLegacyNumber;
import com.forcitis.one.platform.numbering.api.FindLegacyNumbersOfDocument;
import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.kernel.PageRequest;
import java.util.List;
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
    private final FindLegacyNumbersOfDocument findLegacyNumbers;

    LegacyNumberController(FindDocumentByLegacyNumber findDocument, FindLegacyNumbersOfDocument findLegacyNumbers) {
        this.findDocument = findDocument;
        this.findLegacyNumbers = findLegacyNumbers;
    }

    @GetMapping(params = "legacyNumber")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ + "')")
    ResponseEntity<LegacyNumberResponse> byLegacyNumber(@RequestParam DocumentType documentType, @RequestParam String legacyNumber) {
        return findDocument.find(documentType, legacyNumber)
                .map(LegacyNumberResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_LEGACY_NUMBER_NOT_FOUND, ErrorKind.NOT_FOUND,
                        "no document carries " + legacyNumber));
    }

    @GetMapping(params = "documentId")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ + "')")
    CursorPage<LegacyNumberResponse> byDocument(@RequestParam DocumentType documentType, @RequestParam long documentId,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        List<LegacyNumberResponse> all = findLegacyNumbers.find(new DocumentReference(documentType, new DocumentId(Long.toString(documentId))))
                .stream().map(LegacyNumberResponse::from).toList();
        return PageRequest.of(limit, cursor, "legacy-numbers").slice(all, LegacyNumberResponse::legacyNumber, "legacy-numbers");
    }
}
