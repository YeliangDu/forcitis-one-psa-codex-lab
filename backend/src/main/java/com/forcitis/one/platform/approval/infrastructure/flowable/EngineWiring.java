package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.util.ArrayList;
import java.util.List;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
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
    private final ProcessAdministratorCheck administrators;
    private final Environment environment;

    EngineWiring(ProcessEngine engine, RepositoryService repository, TaskAssignmentListener listener, AssignmentProperties properties,
            ProcessAdministratorCheck administrators, Environment environment) {
        this.engine = engine;
        this.repository = repository;
        this.listener = listener;
        this.properties = properties;
        this.administrators = administrators;
        this.environment = environment;
    }

    /** Static, so that the engine configuration does not wait for this class, which needs the engine; without an IdGenerator bean Flowable 8.0.0 defaults to StrongUuidGenerator; this configurer runs before the engine is built and replaces it. */
    @Bean
    static EngineConfigurationConfigurer<SpringProcessEngineConfiguration> engineIdGenerator(IdGenerator ids) {
        return configuration -> configuration.setIdGenerator(() -> ids.next().toString());
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        administrators.verify();
        verifyDefinitions();
        engine.getProcessEngineConfiguration().getEventDispatcher().addEventListener(listener, FlowableEngineEventType.TASK_CREATED);
    }

    void verifyDefinitions() {
        List<String> problems = new ArrayList<>();
        List<ProcessDefinition> deployed = repository.createProcessDefinitionQuery().latestVersion().list();
        if (deployed.isEmpty()) {
            problems.add("no approval process definition is deployed");
        }
        for (ProcessDefinition definition : deployed) {
            ProcessKey key;
            try {
                key = ProcessKey.of(definition.getKey());
            } catch (IllegalArgumentException e) {
                problems.add(definition.getKey() + ": " + e.getMessage());
                continue;
            }
            BpmnModel model = repository.getBpmnModel(definition.getId());
            try {
                ProcessDocumentType.read(key, model.getMainProcess());
                ProcessDefinitionDeclarations.nameOf(key, model.getMainProcess());
                ProcessDefinitionDeclarations.routingFlagRequired(key, model.getMainProcess());
            } catch (DomainException e) {
                problems.add(key + ": " + e.getMessage());
            }
            for (UserTask task : model.getMainProcess().findFlowElementsOfType(UserTask.class)) {
                try {
                    NodePolicyReader.read(task);
                } catch (DomainException | IllegalStateException e) {
                    problems.add(key + ": " + e.getMessage());
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid approval process definitions:\n  " + String.join("\n  ", problems));
        }
    }
}
