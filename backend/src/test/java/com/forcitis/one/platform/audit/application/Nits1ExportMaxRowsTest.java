package com.forcitis.one.platform.audit.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.ListLimits;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class Nits1ExportMaxRowsTest {
    private static ExportAuditEntries exporting(int maxRows) {
        return new ExportAuditEntries(Mockito.mock(AuditEntryQueries.class), Mockito.mock(AuditLog.class), Mockito.mock(CurrentUser.class), maxRows);
    }

    @Test
    @DisplayName("NITS-1 maxRows 为 1 与 EXPORT_MAX 通过，0、负数与 EXPORT_MAX+1 构造失败")
    void maxRowsMustLieBetweenOneAndTheExportLimit() {
        assertThatCode(() -> exporting(1)).doesNotThrowAnyException();
        assertThatCode(() -> exporting(ListLimits.EXPORT_MAX)).doesNotThrowAnyException();
        for (int invalid : new int[] {0, -1, Integer.MIN_VALUE, ListLimits.EXPORT_MAX + 1, Integer.MAX_VALUE}) {
            assertThatThrownBy(() -> exporting(invalid)).as("max-rows " + invalid).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("forcitis.platform.audit.export.max-rows").hasMessageContaining(String.valueOf(ListLimits.EXPORT_MAX));
        }
    }
}
