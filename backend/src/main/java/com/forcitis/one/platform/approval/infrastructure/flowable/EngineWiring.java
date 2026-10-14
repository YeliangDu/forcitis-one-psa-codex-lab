package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
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
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Runs once the application is ready (BPMN deployed) and fails startup on a process that breaks the BPMN conventions. */
@Configuration
@EnableConfigurationProperties(AssignmentProperties.class)
class EngineWiring implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(EngineWiring.class);

    private final ProcessEngine engine;
    private final RepositoryService repository;
    private final TaskAssignmentListener listener;
    private final AssignmentProperties properties;
    private final ProcessAdministrator administrator;
    private final Environment environment;

    EngineWiring(ProcessEngine engine, RepositoryService repository, TaskAssignmentListener listener, AssignmentProperties properties,
            ProcessAdministrator administrator, Environment environment) {
        this.engine = engine;
        this.repository = repository;
        this.listener = listener;
        this.properties = properties;
        this.administrator = administrator;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        verifyProcessAdministrators();
        verifyDefinitions();
        engine.getProcessEngineConfiguration().getEventDispatcher().addEventListener(listener, FlowableEngineEventType.TASK_CREATED);
    }

    private void verifyProcessAdministrators() {
        if (!administrator.holders().isEmpty()) {
            return;
        }
        String message = "Nobody holds the role '" + properties.processAdministratorRole() + "' (or it does not exist): a task nobody can be found for"
                + " would have nowhere to go (D-1a-61)";
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(message);
        }
        log.warn("{}", message);
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
