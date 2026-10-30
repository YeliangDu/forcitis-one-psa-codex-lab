package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class NodeAssignmentTest {

    private static UserTask roleNode() {
        ExtensionElement rule = new ExtensionElement();
        rule.setName(NodePolicyReader.ELEMENT);
        rule.setNamespace(NodePolicyReader.NAMESPACE);
        for (Map.Entry<String, String> attribute : Map.of("type", "ROLE", "role", "finance").entrySet()) {
            ExtensionAttribute value = new ExtensionAttribute(attribute.getKey());
            value.setValue(attribute.getValue());
            rule.addAttribute(value);
        }
        UserTask node = new UserTask();
        node.setId("approve");
        node.addExtensionElement(rule);
        return node;
    }

    @Test
    @DisplayName("P3B-L2 an initiator variable that is missing or not an employee id fails the node with an ALERT and the resolution-failed count")
    void invalidInitiatorIsAlertedAndCounted() {
        OrgDirectory org = mock(OrgDirectory.class);
        RoleDirectory roles = mock(RoleDirectory.class);
        ProcessAdministrator administrator = mock(ProcessAdministrator.class);
        AccountEmployees accounts = mock(AccountEmployees.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        NodeAssignment assignment = new NodeAssignment(org, roles, administrator, meters, accounts, java.time.Clock.fixed(java.time.Instant.parse("2026-11-02T02:00:00Z"), java.time.ZoneOffset.UTC),
                new com.forcitis.one.kernel.BusinessZone(java.time.ZoneId.of("Asia/Shanghai")));
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        Logger logger = (Logger) LoggerFactory.getLogger(NodeAssignment.class);
        logger.addAppender(logged);
        try {
            assertThatThrownBy(() -> assignment.resolve(roleNode(), "instance-1", Map.of(FlowableApprovalEngine.VARIABLE_INITIATOR, "42")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> assignment.resolve(roleNode(), "instance-2", Map.of()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("has no initiator");

            assertThat(meters.counter("forcitis.platform.approval.assignee.resolution.failed").count()).isEqualTo(2.0);
            assertThat(logged.list).hasSize(2).allSatisfy(event -> assertThat(event.getFormattedMessage()).startsWith("ALERT platform.approval"));
            verifyNoInteractions(org, roles, administrator, accounts);
        } finally {
            logger.detachAppender(logged);
        }
    }
}
