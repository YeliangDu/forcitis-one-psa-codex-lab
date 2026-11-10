package com.forcitis.one.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.mockito.listeners.InvocationListener;

/**
 * Test-only regression for baseline bf853c2c6ce5cb3e72172330d34aecdc75f0a331.
 *
 * No Spring context, Docker, HTTP login or database is started. The manually
 * constructed helper calls the actual AccountsAcceptanceSupport.wecomCodeFor.
 *
 * The controlled interleaving demonstrates a Mockito stubbing mechanism.
 * It does not establish the scheduling of a historical failing CI run.
 *
 * Author: one-test
 * Native thread: 01a1b614-934c-7250-a715-5cf09e9c02c2
 * Native turn: unavailable; not fabricated.
 * Author execution: not run.
 */
public class BonusLoginPreparationTest {

    private static final String ORDER_FAILURE =
            "LOGIN_STUB_PREPARATION_AFTER_ASYNC_APPROVAL_STARTED";

    private static final Path BONUS_SOURCE = Path.of(
            "src/test/java/com/forcitis/one/organization/payroll/"
                    + "BonusInputNoFallbackIT.java");

    private static final Pattern NON_CODE = Pattern.compile(
            "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"
                    + "|\"(?:\\\\.|[^\"\\\\])*\""
                    + "|'(?:\\\\.|[^'\\\\])*'");

    private static final class Fixture extends AccountsAcceptanceSupport {

        Fixture(WecomGateway gateway) {
            this.wecomGateway = gateway;
        }

        UUID employee() {
            return newEmployeeId();
        }

        String codeFor(UUID employee) throws ReflectiveOperationException {
            return wecomCodeFor(employee);
        }

        WecomUserId expectedUser(UUID employee) {
            return new WecomUserId(wecomUserIdOf(employee));
        }
    }

    @Test
    void controlledNotificationCanStealDynamicStubbingButPreparedCodeSurvives()
            throws Exception {
        AtomicReference<WecomGateway> shared = new AtomicReference<>();
        AtomicBoolean armed = new AtomicBoolean(true);
        AtomicInteger interruptions = new AtomicInteger();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();

        InvocationListener listener = report -> {
            if (!(report.getInvocation() instanceof Invocation invocation)
                    || !invocation.getMethod().getName().equals("resolveWecomUserId")
                    || !armed.compareAndSet(true, false)) {
                return;
            }

            interruptions.incrementAndGet();
            Thread sender = new Thread(() -> {
                try {
                    shared.get().send(null);
                } catch (Throwable failure) {
                    workerFailure.compareAndSet(null, failure);
                }
            }, "synthetic-wecom-notification");
            sender.setDaemon(true);
            sender.start();
            try {
                sender.join(3000);
                if (sender.isAlive()) {
                    workerFailure.compareAndSet(
                            null, new AssertionError("NOTIFICATION_WORKER_TIMEOUT"));
                    sender.interrupt();
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                workerFailure.compareAndSet(null, failure);
            }
        };

        WecomGateway gateway = mock(
                WecomGateway.class,
                withSettings().invocationListeners(listener));
        shared.set(gateway);
        Fixture fixture = new Fixture(gateway);

        UUID racedEmployee = fixture.employee();
        String racedCode = fixture.codeFor(racedEmployee);

        assertNull(workerFailure.get(), "CONTROLLED_NOTIFICATION_FAILED");
        assertEquals(1, interruptions.get(), "CONTROLLED_INTERLEAVING_NOT_REACHED");
        assertFalse(armed.get());
        assertEquals(
                Optional.empty(),
                gateway.resolveWecomUserId(racedCode, WecomEntry.MINI_PROGRAM),
                "CONTROLLED_DYNAMIC_STUB_WAS_NOT_STOLEN");

        // Same gateway, but prepare the next code before any notification.
        UUID preparedEmployee = fixture.employee();
        String preparedCode = fixture.codeFor(preparedEmployee);

        AtomicReference<Throwable> laterFailure = new AtomicReference<>();
        Thread laterSender = new Thread(() -> {
            try {
                gateway.send(null);
            } catch (Throwable failure) {
                laterFailure.set(failure);
            }
        }, "synthetic-notification-after-preparation");
        laterSender.setDaemon(true);
        laterSender.start();
        laterSender.join(3000);
        boolean timedOut = laterSender.isAlive();
        if (timedOut) {
            laterSender.interrupt();
        }
        assertFalse(timedOut, "PREPARED_NOTIFICATION_WORKER_TIMEOUT");
        assertNull(laterFailure.get(), "PREPARED_NOTIFICATION_FAILED");

        assertEquals(
                Optional.of(fixture.expectedUser(preparedEmployee)),
                gateway.resolveWecomUserId(preparedCode, WecomEntry.MINI_PROGRAM),
                "PREPARED_CODE_LOST_AFTER_NOTIFICATION");
        assertEquals(1, interruptions.get(), "LISTENER_TRIGGERED_MORE_THAN_ONCE");
    }

    @Test
    void newHolderLoginIsPreparedBeforeAsyncApprovalStarts() throws Exception {
        checkMethod(
                "withNoOtherHolderAtTheSecondStageTheFirstApprovalIsRefusedAndRolledBack",
                "newHolder",
                "claim",
                List.of(
                        "contains(\"NO_ASSIGNEE_FOUND\")",
                        "containsExactly(first)",
                        "isNotIn(200, 204)"));
    }

    @Test
    void peerLoginIsPreparedBeforeAsyncApprovalStarts() throws Exception {
        checkMethod(
                "theSecondStageGoesOnlyToHoldersOfThePayrollRole",
                "peer",
                "decide",
                List.of(
                        "contains(\"PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID\")",
                        "actOk(second, holderAccount, \"transfer\", handover(peer))",
                        "isNotIn(200, 204)"));
    }

    private static void checkMethod(
            String name,
            String employee,
            String operation,
            List<String> businessMarkers) throws Exception {
        String source = Files.readString(BONUS_SOURCE);
        String code = codeOnly(source);
        Pattern signature = Pattern.compile(
                "\\bvoid\\s+" + Pattern.quote(name)
                        + "\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\{");
        Matcher declaration = signature.matcher(code);
        assertTrue(declaration.find(), "ACTUAL_BONUS_TEST_METHOD_MISSING:" + name);

        int start = declaration.end();
        int end = methodEnd(code, start);
        String original = source.substring(start, end);
        String body = code.substring(start, end);

        Matcher approvalStart = Pattern.compile(
                "\\bDocumentReference\\s+document\\s*=\\s*start\\s*\\(")
                .matcher(body);
        assertTrue(approvalStart.find(), "ACTUAL_APPROVAL_START_MISSING");

        String prefix = body.substring(0, approvalStart.start());
        String suffix = body.substring(approvalStart.start());
        Matcher late = Pattern.compile("\\b(?:signIn|wecomCodeFor)\\s*\\(")
                .matcher(suffix);
        List<String> lateCalls = new ArrayList<>();
        while (late.find()) {
            lateCalls.add(late.group());
        }

        // Each baseline ordering test stops at this single named assertion.
        assertEquals(List.of(), lateCalls, ORDER_FAILURE + ":" + name);

        Matcher prepared = Pattern.compile(
                "\\bString\\s+(\\w+)\\s*=\\s*wecomCodeFor\\s*\\(\\s*"
                        + Pattern.quote(employee) + "\\s*\\)\\s*;")
                .matcher(prefix);
        assertTrue(prepared.find(), "TARGET_CODE_NOT_PREPARED_BEFORE_START");
        String codeVariable = prepared.group(1);
        assertNotNull(codeVariable);

        assertTrue(
                Pattern.compile(
                        "\\bsignInWith\\s*\\(\\s*" + Pattern.quote(codeVariable)
                                + "\\s*,\\s*" + Pattern.quote(employee) + "\\s*\\)")
                        .matcher(suffix).find(),
                "TARGET_REAL_LOGIN_REMOVED");

        assertTrue(
                Pattern.compile(
                        "actOk\\s*\\(\\s*second\\s*,\\s*signInWith\\s*\\([^)]*\\)"
                                + "\\s*,\\s*" + Pattern.quote("\"" + operation + "\""))
                        .matcher(original).find(),
                "TARGET_AUTHENTICATED_ACTION_REMOVED");

        for (String marker : businessMarkers) {
            assertTrue(
                    original.contains(marker),
                    "BUSINESS_ASSERTION_REMOVED:" + marker);
        }
    }

    private static int methodEnd(String code, int start) {
        int depth = 1;
        for (int position = start; position < code.length(); position++) {
            char current = code.charAt(position);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return position;
            }
        }
        throw new AssertionError("UNBALANCED_ACTUAL_TEST_METHOD");
    }

    private static String codeOnly(String source) {
        char[] result = source.toCharArray();
        Matcher matcher = NON_CODE.matcher(source);
        while (matcher.find()) {
            for (int position = matcher.start(); position < matcher.end(); position++) {
                if (result[position] != '\n' && result[position] != '\r') {
                    result[position] = ' ';
                }
            }
        }
        return new String(result);
    }
}
