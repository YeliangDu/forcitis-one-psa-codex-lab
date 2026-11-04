package com.forcitis.one.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyScopeTest {
    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: only a listed method and path template applies, with real ids and in the contract form; others do not")
    void onlyTheListedEndpointsApply() {
        IdempotencyScope scope = new IdempotencyScope(List.of("# comment", "", "POST /api/v1/x/{id}:go  # Y"));

        assertThat(scope.applies("POST", "/api/v1/x/0192f8a0-0000-7000-8000-000000000001:go")).isTrue();
        assertThat(scope.applies("POST", "/api/v1/x/{id}:go")).as("a template of the contract").isTrue();
        assertThat(scope.applies("PUT", "/api/v1/x/1:go")).isFalse();
        assertThat(scope.applies("POST", "/api/v1/y/1:go")).isFalse();
        assertThat(scope.applies("POST", "/api/v1/x/1:other")).isFalse();
        assertThat(scope.applies("POST", "/api/v1/platform/sessions:logout")).isFalse();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: a line that is not METHOD path is refused when the list is read")
    void badLineIsRefused() {
        assertThatThrownBy(() -> new IdempotencyScope(List.of("POST"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new IdempotencyScope(List.of("POST /a /b"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: the packaged list is read and covers the 20 endpoints but not the removed or credential ones")
    void theRealListIsRead() {
        IdempotencyScope scope = new IdempotencyScope();

        assertThat(scope.applies("POST", "/api/v1/platform/roles")).isTrue();
        assertThat(scope.applies("POST", "/api/v1/platform/approval-tasks/0192f8a0-0000-7000-8000-000000000001:decide")).isTrue();
        assertThat(scope.applies("POST", "/api/v1/organization/org-reconciliations:run")).isFalse();
        assertThat(scope.applies("POST", "/api/v1/platform/source-references")).isFalse();
        assertThat(scope.applies("POST", "/api/v1/platform/source-references/0192f8a0-0000-7000-8000-000000000001:confirm")).isFalse();
        assertThat(scope.applies("POST", "/api/v1/platform/sessions:ticket-login")).isFalse();
        assertThat(scope.applies("POST", "/api/v1/platform/login-states")).isFalse();
    }
}
