package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.api.ExternalFailure;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.Locator;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.OverriddenGrant;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Finding;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.SqlError;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** What one item of {@link ReadOnlyBaseline} found: confirmed and undeterminable problems with their findings, and the overridden grants of (g). */
final class Findings {

    final Set<String> confirmed = new LinkedHashSet<>();
    final Set<String> undeterminable = new LinkedHashSet<>();
    final List<Finding> confirmedFindings = new ArrayList<>();
    final List<Finding> undeterminableFindings = new ArrayList<>();
    final Set<OverriddenGrant> overridden = new LinkedHashSet<>();

    void violated(Reason reason, int count, String problem) {
        violated(reason, count, problem, List.of());
    }

    void violated(Reason reason, int count, String problem, List<? extends Locator> locators) {
        confirmed.add(problem);
        confirmedFindings.add(new Finding(reason, count, null, List.copyOf(locators)));
    }

    void unknown(Reason reason, int count, String problem) {
        undeterminable.add(problem);
        undeterminableFindings.add(new Finding(reason, count, null));
    }

    void failed(Reason reason, Throwable failure) {
        undeterminable.add("cannot be determined (" + ExternalFailure.of(failure) + ")");
        undeterminableFindings.add(new Finding(reason, 1, SqlError.of(failure).orElse(null)));
    }

    <T> Optional<T> ask(Supplier<T> query) {
        try {
            T answer = query.get();
            if (answer == null) {
                unknown(Reason.NO_ANSWER, 1, "cannot be determined (no answer)");
            }
            return Optional.ofNullable(answer);
        } catch (RuntimeException failure) {
            failed(Reason.CATALOG_QUERY_FAILED, failure);
            return Optional.empty();
        }
    }
}
