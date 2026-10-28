package org.mockito.configuration;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.time.Instant;
import org.mockito.internal.stubbing.defaultanswers.ReturnsEmptyValues;
import org.mockito.stubbing.Answer;

/**
 * Mockito's global configuration for the tests (read by Mockito from this fixed class name). A mocked AuditLog answers record(...) with a
 * fresh entry id instead of null: platform.identity writes that id into the role version it opens or closes (ADR-0034 decision 1 item 2,
 * version_audit_entry_id NOT NULL), also at context start-up when the system roles are reconciled, before any test can stub the mock.
 * Every other unstubbed call keeps Mockito's usual default (empty values); a reset keeps this default answer.
 */
public class MockitoConfiguration extends DefaultMockitoConfiguration {

    private static final Answer<Object> EMPTY_VALUES = new ReturnsEmptyValues();

    private static final Answer<Object> AUDIT_ENTRY_IDS = invocation -> invocation.getMethod().getReturnType() == AuditEntryId.class
            ? new AuditEntryId(Uuids.timeOrdered(Instant.EPOCH.plusMillis(1)))
            : EMPTY_VALUES.answer(invocation);

    @Override
    public Answer<Object> getDefaultAnswer() {
        return AUDIT_ENTRY_IDS;
    }
}
