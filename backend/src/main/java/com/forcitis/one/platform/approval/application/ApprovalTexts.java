package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.EscalationTarget;
import java.util.List;

/**
 * Every word the approval module shows a person outside the screens: card titles and bodies, the inbox title of a task. They live
 * here and nowhere else, so that the wording guards read one place and no raw enum or node key reaches a person (review M-16).
 */
public final class ApprovalTexts {

    private ApprovalTexts() {
    }

    public static String documentName(DocumentReference document) {
        return document.documentId().value().toString();
    }

    public static String inboxTitle(String processName, DocumentReference document) {
        return processName + " " + document.documentId().value();
    }

    public static String assignedTitle(String nodeName) {
        return "审批待办：" + nodeName;
    }

    public static String transferredTitle(DocumentReference document) {
        return "审批待办（转交）：" + documentName(document);
    }

    public static String transferredBody(String fromName, String reason) {
        return "由 " + fromName + " 转交给你：" + reason;
    }

    public static String reassignedTitle(DocumentReference document) {
        return "审批待办（改派）：" + documentName(document);
    }

    public static String reassignedBody(String reason) {
        return "流程管理员改派给你：" + reason;
    }

    public static String overdueTitle(DocumentReference document) {
        return "审批已超时：" + documentName(document);
    }

    public static String overdueBody(String dueAt) {
        return "审批已超过时限（" + dueAt + "），请尽快处理";
    }

    public static String escalatedTitle(DocumentReference document) {
        return "审批升级：" + documentName(document);
    }

    public static String escalatedBody(EscalationTarget target) {
        return "审批超时未处理，已升级给你" + (target == EscalationTarget.PROCESS_ADMINISTRATOR ? "（流程管理员）" : "");
    }

    public static String completedTitle(ApprovalOutcome outcome) {
        return "审批完成：" + switch (outcome) {
            case APPROVED -> "已通过";
            case REJECTED -> "已驳回";
            case RETURNED -> "已退回";
            case CANCELLED -> "已撤销";
        };
    }

    public static String completedBody(String processName, DocumentReference document, int round, String reason) {
        return processName + " " + documentName(document) + " 第 " + round + " 轮" + (reason == null ? "" : "：" + reason);
    }

    public static String startRefused(String errorCode) {
        String why = switch (errorCode) {
            case "PLATFORM_APPROVAL_NO_ASSIGNEE_FOUND" -> "找不到可以处理的审批人";
            case "PLATFORM_APPROVAL_NO_ROUTE", "PLATFORM_APPROVAL_ROUTING_FLAG_UNKNOWN", "PLATFORM_APPROVAL_FORM_FIELD_UNKNOWN" -> "没有适用的审批路径";
            default -> "审批规则不允许发起";
        };
        return "审批没有发起，已退回给你：" + why + "（" + errorCode + "）";
    }

    public static String handledNote(String name) {
        return "已由 " + name + " 处理";
    }

    public static String escalatedNote(List<String> names) {
        return "已超时，已升级给 " + String.join("、", names);
    }

    public static String releasedNote(String name) {
        return name + " 已释放，等待重新认领";
    }

    public static String closedNote() {
        return "审批已结束，无需处理";
    }

    public static String aColleague() {
        return "同事";
    }

    public static String assignedBody(String processName, String summary, String initiator, String department, String startedAt, String dueAt) {
        StringBuilder text = new StringBuilder(processName).append(' ').append(summary);
        if (initiator != null) {
            text.append("，发起人 ").append(initiator);
        }
        if (department != null && !department.isEmpty()) {
            text.append("（").append(department).append("）");
        }
        text.append("，发起时间 ").append(startedAt);
        if (dueAt != null) {
            text.append("，截止 ").append(dueAt);
        }
        return text.toString();
    }
}
