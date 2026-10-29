package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.LegalEntityId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record CounterScope(CounterScopeKind kind, @Nullable LegalEntityId legalEntity) {

    public static final CounterScope GLOBAL = new CounterScope(CounterScopeKind.GLOBAL, null);

    public CounterScope {
        Objects.requireNonNull(kind, "kind");
        if ((kind == CounterScopeKind.GLOBAL) != (legalEntity == null)) {
            throw new IllegalArgumentException("a counter scope names a legal entity exactly when it is per legal entity");
        }
    }

    public static CounterScope ofLegalEntity(LegalEntityId legalEntity) {
        return new CounterScope(CounterScopeKind.LEGAL_ENTITY, Objects.requireNonNull(legalEntity, "legalEntity"));
    }
}
