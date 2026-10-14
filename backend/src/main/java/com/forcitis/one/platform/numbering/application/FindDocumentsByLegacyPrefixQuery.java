package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.numbering.api.FindDocumentsByLegacyPrefix;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.domain.LegacyNumberRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FindDocumentsByLegacyPrefixQuery implements FindDocumentsByLegacyPrefix {
    private static final int MIN_PREFIX = 2;

    private final LegacyNumberRepository legacyNumbers;

    FindDocumentsByLegacyPrefixQuery(LegacyNumberRepository legacyNumbers) {
        this.legacyNumbers = legacyNumbers;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyNumberEntry> findByPrefix(DocumentType type, String prefix, String after, int limit) {
        if (prefix == null || prefix.strip().length() < MIN_PREFIX) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_LEGACY_PREFIX_INVALID, ErrorKind.UNPROCESSABLE,
                    "a prefix search needs at least " + MIN_PREFIX + " characters");
        }
        return legacyNumbers.findByPrefix(type, prefix.strip(), after, limit);
    }
}
