package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.domain.AssigneeResolver;
import com.forcitis.one.platform.approval.domain.NodePolicy;
import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.Resolution;
import com.forcitis.one.platform.approval.domain.ResolutionKind;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import org.flowable.bpmn.model.UserTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Who a node is for: the node's own typed rule decides, and the node's skipWhenEmpty attribute decides whether finding nobody may skip
 * it. Nobody found without permission to skip goes to the process administrator; a missing or invalid input, or no administrator, fails.
 */
@Component
class NodeAssignment {

    private static final Logger log = LoggerFactory.getLogger(NodeAssignment.class);

    private final OrgDirectory org;
    private final RoleDirectory roles;
    private final ProcessAdministrator administrator;
    private final MeterRegistry meters;
    private final AccountEmployees accountEmployees;
    private final Clock clock;
    private final BusinessZone zone;

    NodeAssignment(OrgDirectory org, RoleDirectory roles, ProcessAdministrator administrator, MeterRegistry meters,
            AccountEmployees accountEmployees, Clock clock, BusinessZone zone) {
        this.clock = clock;
        this.zone = zone;
        this.accountEmployees = accountEmployees;
        this.org = org;
        this.roles = roles;
        this.administrator = administrator;
        this.meters = meters;
    }

    /** SKIPPED, or a non-empty candidate set; every other outcome that cannot be assigned throws. */
    Resolution resolve(UserTask node, String instanceId, Map<String, Object> engineVariables) {
        NodePolicy policy = NodePolicyReader.read(node);
        ProcessVariables variables = EngineVariables.fromEngine(engineVariables);
        Resolution resolution;
        try {
            EmployeeId initiator = FlowableApprovalEngine.initiatorOf(variables, instanceId);
            resolution = new AssigneeResolver(org, roles, administrator.holders(), accountEmployees, zone.today(clock))
                    .resolve(policy.rule(), policy.selfApprovalAllowed(), policy.skipWhenEmpty(), policy.noFallback(), initiator, variables);
        } catch (RuntimeException failed) {
            log.error("ALERT platform.approval resolving the assignees of node {} (instance {}) failed: {}", node.getId(), instanceId, failed.toString());
            meters.counter("forcitis.platform.approval.assignee.resolution.failed").increment();
            throw failed;
        }
        switch (resolution.kind()) {
            case SKIPPED -> {
                log.info("Node {} (instance {}) has nobody to assign and is skipped", node.getId(), instanceId);
                meters.counter("forcitis.platform.approval.assignee.skipped").increment();
            }
            case INVALID_INPUT -> {
                log.error("Node {} (instance {}) may be skipped when nobody is found, but its input is missing or invalid: not skipped, "
                        + "the approval fails", node.getId(), instanceId);
                meters.counter("forcitis.platform.approval.assignee.invalid.input").increment();
                throw ApprovalError.PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID.refusal("The approver field read by node " + node.getId()
                        + " is not an employed person");
            }
            case UNRESOLVED -> {
                log.error("ALERT platform.approval nobody can handle node {} (instance {}): the step that led here is refused and stays where it was", node.getId(), instanceId);
                meters.counter("forcitis.platform.approval.assignee.unresolved").increment();
                throw ApprovalError.PLATFORM_APPROVAL_NO_ASSIGNEE_FOUND.refusal("Nobody can handle node " + node.getId()
                        + " of instance " + instanceId + (policy.noFallback() ? " and the node takes no fallback" : " and nobody holds the process administrator role"));
            }
            case RESOLVED -> {
            }
            default -> {
                log.warn("Assignment of node {} (instance {}) is {}: candidates {}", node.getId(), instanceId, resolution.kind(),
                        resolution.candidates());
                meters.counter("forcitis.platform.approval.assignee.fallback", "kind", resolution.kind().name().toLowerCase(Locale.ROOT)).increment();
            }
        }
        return resolution;
    }

    /**
     * Who the node's rule names now, with no side effect (no alert, no metric): the candidates the resolution holds, which is exactly who the node would assign
     * the task to now (a node that takes no fallback resolves to nobody rather than to a supervisor or an administrator, so the set is empty then). A node that
     * takes no fallback hands its task over only to these people.
     */
    Set<UserId> namedNow(UserTask node, String instanceId, Map<String, Object> engineVariables) {
        NodePolicy policy = NodePolicyReader.read(node);
        ProcessVariables variables = EngineVariables.fromEngine(engineVariables);
        EmployeeId initiator = FlowableApprovalEngine.initiatorOf(variables, instanceId);
        Resolution resolution = new AssigneeResolver(org, roles, administrator.holders(), accountEmployees, zone.today(clock))
                .resolve(policy.rule(), policy.selfApprovalAllowed(), policy.skipWhenEmpty(), policy.noFallback(), initiator, variables);
        return resolution.candidates();
    }

    static boolean isSkipped(Resolution resolution) {
        return resolution.kind() == ResolutionKind.SKIPPED;
    }
}
