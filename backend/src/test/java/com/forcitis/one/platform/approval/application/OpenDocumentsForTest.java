package com.forcitis.one.platform.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenDocumentsForTest {

    private static TaskView task(String process, DocumentReference document) {
        TaskView task = mock(TaskView.class);
        when(task.processKey()).thenReturn(ProcessKey.of(process));
        when(task.document()).thenReturn(document);
        return task;
    }

    @Test
    @DisplayName("#1452 the open documents of a user are those of tasks of the asked process only")
    void onlyTheAskedProcess() {
        DocumentReference wanted = new DocumentReference(new DocumentTypeCode("A_DOC"), new DocumentId(UUID.randomUUID()));
        DocumentReference unwanted = new DocumentReference(new DocumentTypeCode("B_DOC"), new DocumentId(UUID.randomUUID()));
        ApprovalEngine engine = mock(ApprovalEngine.class);
        UserId user = new UserId(UUID.randomUUID());
        List<TaskView> tasks = List.of(task("wanted_process_x", wanted), task("other_process_x", unwanted));
        when(engine.findOpenTasksFor(user)).thenReturn(tasks);
        ApprovalProcessesFacade facade = new ApprovalProcessesFacade(null, null, null, null, null, engine);

        assertThat(facade.openDocumentsFor(ProcessKey.of("wanted_process_x"), user)).isEqualTo(Set.of(wanted));
    }
}
