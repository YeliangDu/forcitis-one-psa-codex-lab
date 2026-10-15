package com.forcitis.one.platform.eventing.infrastructure;

import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvent;
import java.time.Clock;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * A scheduled job starts a correlation of its own and acts under its lock name; an event handler continues the correlation of the event it
 * handles and acts under the name its class declares. Both names are declared in architecture/system-actors.txt (RULING-P2-C5).
 */
@Aspect
@Component
class UnitOfWorkAspect {

    private final Clock clock;

    UnitOfWorkAspect(Clock clock) {
        this.clock = clock;
    }

    @Around("@annotation(org.springframework.modulith.events.ApplicationModuleListener)")
    Object handleEvent(ProceedingJoinPoint call) throws Throwable {
        MethodSignature method = (MethodSignature) call.getSignature();
        SystemActorName declared = ((Class<?>) method.getDeclaringType()).getAnnotation(SystemActorName.class);
        if (declared == null) {
            throw new IllegalStateException("event listener " + method.getDeclaringType().getName() + " declares no @SystemActorName");
        }
        String handler = declared.value();
        Object[] args = call.getArgs();
        UUID correlationId;
        UUID causationId = null;
        if (args.length > 0 && args[0] instanceof DomainEvent event) {
            correlationId = event.envelope().correlationId();
            causationId = event.envelope().eventId();
        } else {
            correlationId = newCorrelation();
        }
        try (WorkContext.Scope scope = WorkContext.enter(correlationId, causationId, handler)) {
            return call.proceed();
        }
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    Object runJob(ProceedingJoinPoint call) throws Throwable {
        MethodSignature method = (MethodSignature) call.getSignature();
        SchedulerLock lock = method.getMethod().getAnnotation(SchedulerLock.class);
        if (lock == null) {
            throw new IllegalStateException("scheduled job " + method.getDeclaringType().getName() + "." + method.getName() + " has no @SchedulerLock name");
        }
        String job = lock.name();
        try (WorkContext.Scope scope = WorkContext.enter(newCorrelation(), null, job)) {
            return call.proceed();
        }
    }

    private UUID newCorrelation() {
        return Uuids.timeOrdered(clock);
    }
}
