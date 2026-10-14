package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.numbering.api.DocumentType;
import com.forcitis.one.platform.numbering.api.DocumentId;
import com.forcitis.one.platform.numbering.api.FindDocumentByLegacyNumber;
import com.forcitis.one.platform.numbering.api.FindLegacyNumbersOfDocument;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/{version}/platform/legacy-numbers")
class LegacyNumberController {
    private final FindDocumentByLegacyNumber findDocument;
    private final FindLegacyNumbersOfDocument findLegacyNumbers;

    LegacyNumberController(FindDocumentByLegacyNumber findDocument, FindLegacyNumbersOfDocument findLegacyNumbers) {
        this.findDocument = findDocument;
        this.findLegacyNumbers = findLegacyNumbers;
    }

    @GetMapping(version = "1", params = "legacyNumber")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ + "')")
    ResponseEntity<LegacyNumberResponse> byLegacyNumber(@RequestParam DocumentType documentType, @RequestParam String legacyNumber) {
        return findDocument.find(documentType, legacyNumber)
                .map(LegacyNumberResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(version = "1", params = "documentId")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ + "')")
    List<LegacyNumberResponse> byDocument(@RequestParam DocumentType documentType, @RequestParam long documentId) {
        return findLegacyNumbers.find(documentType, new DocumentId(documentId)).stream().map(LegacyNumberResponse::from).toList();
    }
}
