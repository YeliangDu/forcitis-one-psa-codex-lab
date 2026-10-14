package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.platform.numbering.api.ConfirmationKind;
import com.forcitis.one.platform.numbering.api.ConfirmationStatus;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.RuleVersion;
import com.forcitis.one.platform.numbering.events.NumberingRuleConfirmationGiven;
import com.forcitis.one.platform.numbering.events.NumberingRuleConfirmed;
import com.forcitis.one.platform.numbering.events.NumberingRuleReconfigured;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The template of one document type, kept as saved versions (ADR-0027 section 2). A version is never edited: changing the template
 * saves a new one, which waits for confirmation while the confirmed version keeps issuing numbers. The author of a version cannot
 * confirm it (four eyes), and a voucher template also needs the finance confirmation. A rule with nothing confirmed issues nothing.
 */
public final class NumberingRule extends AggregateRoot {
    public static final Set<DocumentType> NEEDING_FINANCE_CONFIRMATION = Set.of(DocumentType.JOURNAL_VOUCHER);

    public static final StateMachine<VersionStatus> MACHINE = StateMachine.of(VersionStatus.class)
            .allow(VersionStatus.PENDING_CONFIRMATION, VersionStatus.CONFIRMED, VersionStatus.SUPERSEDED)
            .build();

    private final DocumentType documentType;
    private final List<TemplateVersion> loaded;
    private List<TemplateVersion> versions;

    private NumberingRule(DocumentType documentType, List<TemplateVersion> versions, long version, long eventSequence) {
        super(version, eventSequence);
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("a numbering rule has at least its seeded version");
        }
        this.versions = versions.stream().sorted(Comparator.comparingInt(TemplateVersion::number)).toList();
        this.loaded = this.versions;
    }

    public static NumberingRule restore(DocumentType documentType, List<TemplateVersion> versions, long version, long eventSequence) {
        return new NumberingRule(documentType, versions, version, eventSequence);
    }

    public DocumentType documentType() {
        return documentType;
    }

    public List<TemplateVersion> versions() {
        return versions;
    }

    public List<TemplateVersion> changedVersions() {
        return versions.stream().filter(candidate -> !loaded.contains(candidate)).toList();
    }

    public int loadedVersionCount() {
        return loaded.size();
    }

    public VersionStatus loadedStatusOf(int number) {
        return loaded.stream().filter(v -> v.number() == number).findFirst().orElseThrow().status();
    }

    public Optional<TemplateVersion> inForce() {
        return versions.stream().filter(TemplateVersion::isInForce).max(Comparator.comparingInt(TemplateVersion::number));
    }

    public Optional<TemplateVersion> pending() {
        return versions.stream().filter(v -> v.status() == VersionStatus.PENDING_CONFIRMATION).max(Comparator.comparingInt(TemplateVersion::number));
    }

    /** The version in force, or the one waiting for confirmation while none is in force. */
    public TemplateVersion current() {
        return inForce().or(this::pending).orElseThrow(() -> new IllegalStateException("rule " + documentType + " has no usable version"));
    }

    public ConfirmationStatus confirmation() {
        return inForce().isPresent() ? ConfirmationStatus.CONFIRMED : ConfirmationStatus.PENDING_CONFIRMATION;
    }

    public boolean requiresFinanceConfirmation() {
        return NEEDING_FINANCE_CONFIRMATION.contains(documentType);
    }

    public void propose(NumberingPattern pattern, long startValue, UserId author, Instant at) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(author, "author");
        if (startValue < 1) {
            throw new IllegalArgumentException("startValue must be >= 1");
        }
        if (pattern.usesTypeCode() && documentType.code().isEmpty()) {
            throw new IllegalArgumentException("Document type " + documentType
                    + " has no registered code, so its pattern cannot use {" + NumberingPattern.TYPE_TOKEN + "}");
        }
        List<TemplateVersion> next = new ArrayList<>();
        for (TemplateVersion existing : versions) {
            next.add(existing.status() == VersionStatus.PENDING_CONFIRMATION ? withStatus(existing, VersionStatus.SUPERSEDED) : existing);
        }
        int number = versions.getLast().number() + 1;
        next.add(new TemplateVersion(number, pattern, startValue, author, at, VersionStatus.PENDING_CONFIRMATION, null, null, null, null));
        this.versions = List.copyOf(next);
        record((envelopes, version) -> new NumberingRuleReconfigured(envelopes.wrap(draft("NumberingRuleReconfigured", version)), documentType,
                new RuleVersion(number)));
    }

    public void confirm(UserId by, Instant at) {
        TemplateVersion target = confirmable(by);
        if (target.confirmedBy() != null) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL, ErrorKind.CONFLICT,
                    "version " + target.number() + " already has its confirmation");
        }
        if (by.equals(target.financeConfirmedBy())) {
            throw repeated(by);
        }
        replace(new TemplateVersion(target.number(), target.pattern(), target.startValue(), target.author(), target.authoredAt(), target.status(), by, at,
                target.financeConfirmedBy(), target.financeConfirmedAt()));
        recordGiven(target.number(), ConfirmationKind.BUSINESS);
        completeIfAllGiven(target.number());
    }

    public void confirmByFinance(UserId by, Instant at) {
        if (!requiresFinanceConfirmation()) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_FINANCE_CONFIRMATION_NOT_REQUIRED, ErrorKind.CONFLICT,
                    documentType + " numbers need no finance confirmation");
        }
        TemplateVersion target = confirmable(by);
        if (target.financeConfirmedBy() != null) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL, ErrorKind.CONFLICT,
                    "version " + target.number() + " already has its finance confirmation");
        }
        if (by.equals(target.confirmedBy())) {
            throw repeated(by);
        }
        replace(new TemplateVersion(target.number(), target.pattern(), target.startValue(), target.author(), target.authoredAt(), target.status(),
                target.confirmedBy(), target.confirmedAt(), by, at));
        recordGiven(target.number(), ConfirmationKind.FINANCE);
        completeIfAllGiven(target.number());
    }

    private TemplateVersion confirmable(UserId by) {
        Objects.requireNonNull(by, "by");
        TemplateVersion target = pending().orElseThrow(() -> new DomainException(NumberingError.PLATFORM_NUMBERING_NOTHING_TO_CONFIRM,
                ErrorKind.CONFLICT, "no version of " + documentType + " waits for confirmation"));
        if (by.equals(target.author())) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR, ErrorKind.FORBIDDEN,
                    "the author of a version cannot confirm it");
        }
        return target;
    }

    private static DomainException repeated(UserId by) {
        return new DomainException(NumberingError.PLATFORM_NUMBERING_CONFIRMATION_REPEATED, ErrorKind.FORBIDDEN,
                "user " + by.value() + " already confirmed this version; the second confirmation must come from someone else");
    }

    private void completeIfAllGiven(int number) {
        TemplateVersion target = versions.stream().filter(v -> v.number() == number).findFirst().orElseThrow();
        boolean financeDone = !requiresFinanceConfirmation() || target.financeConfirmedBy() != null;
        if (target.confirmedBy() != null && financeDone) {
            replace(withStatus(target, VersionStatus.CONFIRMED));
            record((envelopes, version) -> new NumberingRuleConfirmed(envelopes.wrap(draft("NumberingRuleConfirmed", version)), documentType, new RuleVersion(number)));
        }
    }

    private void recordGiven(int number, ConfirmationKind kind) {
        record((envelopes, version) -> new NumberingRuleConfirmationGiven(envelopes.wrap(draft("NumberingRuleConfirmationGiven", version)),
                documentType, new RuleVersion(number), kind));
    }

    private TemplateVersion withStatus(TemplateVersion source, VersionStatus target) {
        MACHINE.require(source.status(), target, NumberingError.PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL);
        return new TemplateVersion(source.number(), source.pattern(), source.startValue(), source.author(), source.authoredAt(), target,
                source.confirmedBy(), source.confirmedAt(), source.financeConfirmedBy(), source.financeConfirmedAt());
    }

    private void replace(TemplateVersion changed) {
        this.versions = versions.stream().map(v -> v.number() == changed.number() ? changed : v).toList();
    }

    private EventDraft draft(String eventName, long version) {
        return new EventDraft("platform.numbering." + eventName, 1, "NumberingRule", documentType.name(), version, null, null,
                new EventSource(SourceSystem.ONE, null), List.of());
    }
}
