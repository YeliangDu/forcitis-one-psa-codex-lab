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
            items.add(new InboxItem(InboxSource.NEW, task.taskId().value(), task.processKey().name() + " " + task.businessKey(),
                    task.nodeName(), task.createdAt(), task.isClaimed(), null));
        }
        int newCount = items.size();
        LegacyInbox.LegacyTodos old = legacy.readTodos(user);
        List<LegacyTodo> merged = InboxMerger.mergeRowTasks(old.todos());
        for (LegacyTodo todo : merged) {
            items.add(new InboxItem(InboxSource.LEGACY, todo.taskKey(), todo.title(), todo.nodeName(), todo.createdAt(), false,
                    todo.detailUrl()));
        }
        items.sort(Comparator.comparing((InboxItem i) -> i.createdAt() == null ? Instant.MIN : i.createdAt()).reversed());
        return new MergedInbox(items, newCount, merged.size(), newCount + merged.size(), old.available());
    }
}
