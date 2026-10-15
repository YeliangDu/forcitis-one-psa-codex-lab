package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalTaskQueries;
import com.forcitis.one.platform.approval.domain.InboxMerger;
import com.forcitis.one.platform.approval.domain.LegacyInbox;
import com.forcitis.one.platform.approval.domain.LegacyTodo;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nothing is cached, so the two counts are always current. */
@Service
public class ListInbox {

    private final ApprovalEngine engine;
    private final ApprovalTaskQueries tasks;
    private final LegacyInbox legacy;
    private final BusinessTime time;
    private final AccountEmployees accountEmployees;

    ListInbox(ApprovalEngine engine, ApprovalTaskQueries tasks, LegacyInbox legacy, BusinessTime time, AccountEmployees accountEmployees) {
        this.accountEmployees = accountEmployees;
        this.engine = engine;
        this.tasks = tasks;
        this.legacy = legacy;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public MergedInbox execute(UserId user, ListPage page) {
        List<InboxItem> items = new ArrayList<>();
        List<TaskView> open = engine.findOpenTasksFor(user);
        Map<ApprovalTaskId, Instant> deadlines = tasks.dueAtOf(open.stream().map(TaskView::taskId).toList());
        Instant now = time.now();
        for (TaskView task : open) {
            Optional<Instant> due = Optional.ofNullable(deadlines.get(task.taskId()));
            items.add(new InboxItem(InboxSource.NEW, task.taskId().value(),
                    ApprovalTexts.inboxTitle(task.processKey(), task.document()), Optional.of(task.nodeName()),
                    Optional.of(task.createdAt()), task.isClaimed(), Optional.empty(), due, due.map(now::isAfter).orElse(false), Optional.of(task.processKey()),
                    Optional.of(task.document().documentId().value()), InboxCategory.TODO));
        }
        int newCount = items.size();
        LegacyInbox.LegacyTodos old = accountEmployees.employeeOf(user).map(legacy::readTodos).orElseGet(LegacyInbox.LegacyTodos::unavailable);
        List<LegacyTodo> merged = InboxMerger.mergeRowTasks(old.todos());
        for (LegacyTodo todo : merged) {
            items.add(new InboxItem(InboxSource.LEGACY, todo.taskKey(), todo.title(), Optional.ofNullable(todo.nodeName()),
                    Optional.ofNullable(todo.createdAt()), false, Optional.ofNullable(todo.detailUrl()), Optional.empty(), false, Optional.empty(), Optional.empty(), InboxCategory.TODO));
        }
        items.sort(Comparator.comparing((InboxItem i) -> i.createdAt().orElse(Instant.MIN)).thenComparing(InboxItem::taskId).reversed());
        int notices = (int) items.stream().filter(item -> item.category() == InboxCategory.NOTICE).count();
        List<InboxItem> after = items.stream().filter(item -> page.cursor().map(position -> comesAfter(item, position)).orElse(true)).toList();
        boolean more = after.size() > page.limit();
        List<InboxItem> shown = more ? after.subList(0, page.limit()) : after;
        Optional<String> next = more ? Optional.of(ListPage.cursorAfter(shown.getLast().createdAt().orElse(Instant.MIN), shown.getLast().taskId()))
                : Optional.empty();
        return new MergedInbox(shown, newCount, merged.size(), notices, newCount + merged.size(), old.available(), next);
    }

    private static boolean comesAfter(InboxItem item, ListPage.Position position) {
        int byTime = item.createdAt().orElse(Instant.MIN).compareTo(position.at());
        return byTime < 0 || (byTime == 0 && item.taskId().compareTo(position.id()) < 0);
    }
}
