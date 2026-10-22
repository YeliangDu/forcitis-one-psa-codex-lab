package com.forcitis.one.kernel;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** The legal transitions of one aggregate, declared next to it; anything else is refused. */
public final class StateMachine<S extends Enum<S>> {

    private final Class<S> type;
    private final Map<S, Set<S>> legal;

    private StateMachine(Class<S> type, Map<S, Set<S>> legal) {
        this.type = type;
        this.legal = legal;
    }

    public static <S extends Enum<S>> Builder<S> of(Class<S> type) {
        return new Builder<>(type);
    }

    public boolean allows(S from, S to) {
        return legal.get(from).contains(to);
    }

    public Set<S> successors(S from) {
        return Set.copyOf(legal.get(from));
    }

    /** The transition the module has no code of its own for: {@link KernelError#KERNEL_TRANSITION_ILLEGAL}. */
    public S require(S from, S to) {
        return require(from, to, KernelError.KERNEL_TRANSITION_ILLEGAL);
    }

    public S require(S from, S to, ErrorCode code) {
        if (!allows(from, to)) {
            throw new DomainException(code, ErrorKind.CONFLICT, type.getSimpleName() + " cannot go from " + from + " to " + to);
        }
        return to;
    }

    public static final class Builder<S extends Enum<S>> {
        private final Class<S> type;
        private final Map<S, Set<S>> legal;

        private Builder(Class<S> type) {
            this.type = type;
            this.legal = new EnumMap<>(type);
            for (S state : type.getEnumConstants()) {
                legal.put(state, EnumSet.noneOf(type));
            }
        }

        @SafeVarargs
        public final Builder<S> allow(S from, S... to) {
            legal.get(from).addAll(Arrays.asList(to));
            return this;
        }

        public StateMachine<S> build() {
            return new StateMachine<>(type, legal);
        }
    }
}
