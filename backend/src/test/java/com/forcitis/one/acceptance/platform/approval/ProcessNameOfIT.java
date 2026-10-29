package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * IMP-1023-NX5-01 目标②（#644 opus 首轮 #8、#10、#11）与完成标准第 2、3 条：引擎端口的 processNameOf 返回 ProcessName 值对象；
 * 同一流程键重复定义（多次部署）取最新的定义；未知流程键返回错误码登记表里登记的错误码，不再是未包装的 IllegalStateException（HTTP 500）。
 * 经反射调用 processNameOf，使返回类型尚未改时本类仍可编译，红在断言上。
 */
class ProcessNameOfIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(ProcessNameOfIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    ApprovalEngine engine;

    @Autowired
    RepositoryService repository;

    private static final String DEFINITION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                         xmlns:forcitis="https://forcitis.com/psa/approval/extension" targetNamespace="https://forcitis.com/psa/approval">
              <process id="%s" name="%s" isExecutable="true">
                <extensionElements>
                  <forcitis:documentType code="TIMESHEET"/>
                  <forcitis:routingFlagRequired value="false"/>
                </extensionElements>
                <startEvent id="start"/>
                <sequenceFlow id="toReview" sourceRef="start" targetRef="review"/>
                <userTask id="review" name="复核">
                  <extensionElements>
                    <forcitis:assignmentRule type="FORM_FIELD_USERS" field="projectManagerId"/>
                  </extensionElements>
                </userTask>
                <sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;

    private String deploy(String key, String name) {
        return repository.createDeployment().addString(key + ".bpmn20.xml", DEFINITION.formatted(key, name)).deploy().getId();
    }

    private Object processNameOf(ProcessKey key) throws Throwable {
        Method method = ApprovalEngine.class.getMethod("processNameOf", ProcessKey.class);
        try {
            return method.invoke(engine, key);
        } catch (InvocationTargetException refused) {
            throw refused.getCause();
        }
    }

    private static String textOf(Object name) {
        return name instanceof ProcessName processName ? processName.value() : String.valueOf(name);
    }

    @Test
    @DisplayName("IMP-1023-NX5-01 目标②（#8）：processNameOf 的返回类型是 ProcessName，值取自流程定义的 name")
    void processNameOfReturnsAProcessName() throws Throwable {
        assertThat(ApprovalEngine.class.getMethod("processNameOf", ProcessKey.class).getReturnType()).isEqualTo(ProcessName.class);

        assertThat(processNameOf(TimesheetApprovalProcess.KEY)).isEqualTo(new ProcessName("工时审批"));
    }

    @Test
    @DisplayName("IMP-1023-NX5-01 完成标准第 3 条（#10，调度 10-28）：同一流程键重复定义时取最新的定义")
    void theLatestDefinitionOfTheSameKeyWins() throws Throwable {
        ProcessKey key = ProcessKey.of("cost_demo_renamed");
        String first = deploy(key.key(), "演示旧名");
        String second = null;
        try {
            assertThat(textOf(processNameOf(key))).isEqualTo("演示旧名");

            second = deploy(key.key(), "演示新名");

            assertThat(textOf(processNameOf(key))).isEqualTo("演示新名");
        } finally {
            if (second != null) {
                repository.deleteDeployment(second, true);
            }
            repository.deleteDeployment(first, true);
        }
    }

    @Test
    @DisplayName("IMP-1023-NX5-01 完成标准第 2 条（#11，调度 10-28）：未知流程键返回错误码登记表里登记的错误码，不抛未包装的 IllegalStateException")
    void anUnknownProcessKeyIsARegisteredBusinessError() {
        ProcessKey unknown = ProcessKey.of("cost_demo_never_deployed");

        assertThatThrownBy(() -> processNameOf(unknown)).isInstanceOfSatisfying(DomainException.class, refused -> {
            assertThat(refused.errorCode()).isInstanceOf(ApprovalError.class);
            assertThat(registeredErrorCodes()).as("错误码 %s 须在 glossary 登记（error__<code> 条目）", refused.errorCode().code())
                    .contains("error__" + refused.errorCode().code().toLowerCase(Locale.ROOT));
        });
    }

    /** The error code terms of the module glossary (backend/glossary): the registry the card means by 「登记」. */
    private static List<String> registeredErrorCodes() {
        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("glossary"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (line.startsWith("- id: error__") || line.startsWith("  - id: error__")) {
                        ids.add(line.substring(line.indexOf("error__")).trim());
                    }
                }
            }
        } catch (IOException unreadable) {
            throw new AssertionError(unreadable);
        }
        return ids;
    }
}
