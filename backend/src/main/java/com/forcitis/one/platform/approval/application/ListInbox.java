package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalTaskQueries;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListInbox {

    private final ApprovalEngine engine;
    private final ApprovalTaskQueries tasks;
    private final BusinessTime time;

    ListInbox(ApprovalEngine engine, ApprovalTaskQueries tasks, BusinessTime time) {
        this.engine = engine;
        this.tasks = tasks;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public MergedInbox execute(UserId user, ListPage page) {
        List<InboxItem> items = new ArrayList<>();
        List<TaskView> open = engine.findOpenTasksFor(user);
        Map<ApprovalTaskId, Instant> deadlines = tasks.dueAtOf(open.stream().map(TaskView::taskId).toList());
        Instant now = time.now();
        Map<ProcessKey, String> processNames = new HashMap<>();
        for (TaskView task : open) {
            Optional<Instant> due = Optional.ofNullable(deadlines.get(task.taskId()));
            items.add(new InboxItem(task.taskId().toString(),
                    ApprovalTexts.inboxTitle(processNames.computeIfAbsent(task.processKey(), engine::processNameOf), task.document()), Optional.of(task.nodeName()),
                    Optional.of(task.createdAt()), task.isClaimed(), due, due.map(now::isAfter).orElse(false), Optional.of(task.processKey()),
                    Optional.of(task.document().documentId().value()), InboxCategory.TODO));
        }
        int newCount = items.size();
        items.sort(Comparator.comparing((InboxItem i) -> i.createdAt().orElse(Instant.MIN)).thenComparing(InboxItem::taskId).reversed());
        int notices = (int) items.stream().filter(item -> item.category() == InboxCategory.NOTICE).count();
        List<InboxItem> after = items.stream().filter(item -> page.cursor().map(position -> comesAfter(item, position)).orElse(true)).toList();
        boolean more = after.size() > page.limit();
        List<InboxItem> shown = more ? after.subList(0, page.limit()) : after;
        Optional<String> next = more ? Optional.of(ListPage.cursorAfter(shown.getLast().createdAt().orElse(Instant.MIN), shown.getLast().taskId()))
                : Optional.empty();
        return new MergedInbox(shown, newCount, notices, newCount, next);
    }

    private static boolean comesAfter(InboxItem item, ListPage.Position position) {
        int byTime = item.createdAt().orElse(Instant.MIN).compareTo(position.at());
        return byTime < 0 || (byTime == 0 && item.taskId().compareTo(position.id()) < 0);
    }
}
