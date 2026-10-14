package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.numbering.api.ConfirmationStatus;
import com.forcitis.one.platform.numbering.api.NumberingRuleView;
import com.forcitis.one.platform.numbering.api.NumberingTemplateView;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * pattern, resetPolicy, startValue and capacity are those of the version in force, or of the version waiting while none is in force
 * (confirmationStatus tells which); pending is the version waiting for confirmation. Ids are strings, as everywhere.
 */
record NumberingRuleResponse(DocumentType documentType, @Nullable String documentTypeCode, long version, String pattern, ResetPolicy resetPolicy, long startValue,
        long capacity, ConfirmationStatus confirmationStatus, int templateVersion, LocalDate effectiveFrom, boolean requiresFinanceConfirmation, @Nullable PendingTemplate pending) {

    record PendingTemplate(int templateVersion, String pattern, ResetPolicy resetPolicy, long startValue, LocalDate effectiveFrom, long capacity, @Nullable String authorId,
            Instant authoredAt, @Nullable String confirmedBy, @Nullable String financeConfirmedBy) {

        static PendingTemplate of(NumberingTemplateView template) {
            return new PendingTemplate(template.versionNumber(), template.pattern(), template.resetPolicy(), template.startValue(),
                    template.effectiveFrom(), template.capacity(), text(template.author()), template.authoredAt(), text(template.confirmer()),
                    text(template.financeConfirmer()));
        }
    }

    static NumberingRuleResponse from(NumberingRuleView rule) {
        NumberingTemplateView template = rule.template();
        return new NumberingRuleResponse(rule.documentType(), rule.typeCode().orElse(null), rule.version(), template.pattern(),
                template.resetPolicy(), template.startValue(), template.capacity(), rule.confirmation(), template.versionNumber(),
                template.effectiveFrom(), rule.requiresFinanceConfirmation(), rule.pending().map(PendingTemplate::of).orElse(null));
    }

    private static String text(Optional<UserId> id) {
        return id.map(user -> String.valueOf(user.value())).orElse(null);
    }
}
