package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.platform.approval.api.ProcessKey;
import java.util.ArrayList;
import java.util.List;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

/** Runs once the application is ready (BPMN deployed) and fails startup on a process that breaks the BPMN conventions. */
@Configuration
@EnableConfigurationProperties(AssignmentProperties.class)
class EngineWiring implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(EngineWiring.class);

    private final ProcessEngine engine;
    private final RepositoryService repository;
    private final TaskAssignmentListener listener;
    private final AssignmentProperties properties;

    EngineWiring(ProcessEngine engine, RepositoryService repository, TaskAssignmentListener listener, AssignmentProperties properties) {
        this.engine = engine;
        this.repository = repository;
        this.listener = listener;
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (properties.processAdministratorUserId() == null) {
            log.warn("forcitis.platform.approval.assignment.process-administrator-user-id is not set: a task nobody can be found for "
                    + "will fail its approval instead of going to the process administrator (RULE-250/254)");
        }
        verifyDefinitions();
        engine.getProcessEngineConfiguration().getEventDispatcher().addEventListener(listener, FlowableEngineEventType.TASK_CREATED);
    }

    private void verifyDefinitions() {
        List<String> problems = new ArrayList<>();
        for (ProcessKey key : ProcessKey.values()) {
            ProcessDefinition definition = repository.createProcessDefinitionQuery().processDefinitionKey(key.key())
                    .latestVersion().singleResult();
            if (definition == null) {
                problems.add(key + ": no deployed process definition " + key.key());
                continue;
            }
            BpmnModel model = repository.getBpmnModel(definition.getId());
            for (UserTask task : model.getMainProcess().findFlowElementsOfType(UserTask.class)) {
                try {
                    NodePolicyReader.read(task);
                } catch (IllegalStateException e) {
                    problems.add(key + ": " + e.getMessage());
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid approval process definitions:\n  " + String.join("\n  ", problems));
        }
    }
}
