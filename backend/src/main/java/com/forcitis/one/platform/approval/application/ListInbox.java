package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.InboxMerger;
import com.forcitis.one.platform.approval.domain.LegacyInbox;
import com.forcitis.one.platform.approval.domain.LegacyTodo;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nothing is cached, so the two counts are always current. */
@Service
public class ListInbox {

    private final ApprovalEngine engine;
    private final LegacyInbox legacy;

    ListInbox(ApprovalEngine engine, LegacyInbox legacy) {
        this.engine = engine;
        this.legacy = legacy;
    }

    @Transactional(readOnly = true)
    public MergedInbox execute(UserId user) {
        List<InboxItem> items = new ArrayList<>();
        for (TaskView task : engine.findOpenTasksFor(user)) {
            items.add(new InboxItem(InboxSource.NEW, task.taskId().value(),
                    task.processKey().name() + " " + task.document().documentId().value(), Optional.of(task.nodeName()),
                    Optional.of(task.createdAt()), task.isClaimed(), Optional.empty()));
        }
        int newCount = items.size();
        LegacyInbox.LegacyTodos old = legacy.readTodos(user);
        List<LegacyTodo> merged = InboxMerger.mergeRowTasks(old.todos());
        for (LegacyTodo todo : merged) {
            items.add(new InboxItem(InboxSource.LEGACY, todo.taskKey(), todo.title(), Optional.ofNullable(todo.nodeName()),
                    Optional.ofNullable(todo.createdAt()), false, Optional.ofNullable(todo.detailUrl())));
        }
        items.sort(Comparator.comparing((InboxItem i) -> i.createdAt().orElse(Instant.MIN)).reversed());
        return new MergedInbox(items, newCount, merged.size(), newCount + merged.size(), old.available());
    }
}
