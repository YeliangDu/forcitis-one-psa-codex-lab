package com.forcitis.one.platform.numbering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.LegalEntityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CounterScopeTest {

    static final LegalEntityId LEGAL_ENTITY = new LegalEntityId(TestIds.uuid(1));

    @Test
    @DisplayName("a global counter scope names no legal entity")
    void globalScopeHasNoLegalEntity() {
        assertThat(CounterScope.GLOBAL.kind()).isEqualTo(CounterScopeKind.GLOBAL);
        assertThat(CounterScope.GLOBAL.legalEntity()).isNull();
        assertThat(new CounterScope(CounterScopeKind.GLOBAL, null)).isEqualTo(CounterScope.GLOBAL);
    }

    @Test
    @DisplayName("a per-legal-entity counter scope names its legal entity")
    void legalEntityScopeNamesItsLegalEntity() {
        CounterScope scope = CounterScope.ofLegalEntity(LEGAL_ENTITY);

        assertThat(scope.kind()).isEqualTo(CounterScopeKind.LEGAL_ENTITY);
        assertThat(scope.legalEntity()).isEqualTo(LEGAL_ENTITY);
    }

    @Test
    @DisplayName("a global scope with a legal entity is refused")
    void globalScopeWithLegalEntityIsRefused() {
        assertThatThrownBy(() -> new CounterScope(CounterScopeKind.GLOBAL, LEGAL_ENTITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a per-legal-entity scope without a legal entity is refused")
    void legalEntityScopeWithoutLegalEntityIsRefused() {
        assertThatThrownBy(() -> new CounterScope(CounterScopeKind.LEGAL_ENTITY, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a missing kind, or a missing legal entity for the per-legal-entity factory, is a null pointer")
    void missingPartsAreNullPointers() {
        assertThatThrownBy(() -> new CounterScope(null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CounterScope.ofLegalEntity(null)).isInstanceOf(NullPointerException.class);
    }
}
