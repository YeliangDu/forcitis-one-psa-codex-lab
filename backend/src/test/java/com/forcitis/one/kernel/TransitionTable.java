package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Exhausts a state machine: every domain operation is tried from every state. The expected table says which (from, to) pairs are
 * legal for an operation; every other state must refuse with a DomainException and leave the status untouched.
 */
public final class TransitionTable<S extends Enum<S>, A> {

    private final Class<S> states;
    private final Function<S, A> at;
    private final Function<A, S> statusOf;
    private final Map<String, Consumer<A>> operations = new java.util.LinkedHashMap<>();
    private final Map<String, Map<S, S>> legal = new java.util.LinkedHashMap<>();

    public TransitionTable(Class<S> states, Function<S, A> at, Function<A, S> statusOf) {
        this.states = states;
        this.at = at;
        this.statusOf = statusOf;
    }

    /** An operation and the transitions it may make, as from-state to to-state pairs. */
    public TransitionTable<S, A> operation(String name, Consumer<A> call, Object... fromToPairs) {
        operations.put(name, call);
        Map<S, S> pairs = new EnumMap<>(states);
        for (int i = 0; i < fromToPairs.length; i += 2) {
            pairs.put(states.cast(fromToPairs[i]), states.cast(fromToPairs[i + 1]));
        }
        legal.put(name, pairs);
        return this;
    }

    public List<String> verify() {
        List<String> tried = new ArrayList<>();
        for (Map.Entry<String, Consumer<A>> operation : operations.entrySet()) {
            Map<S, S> pairs = legal.get(operation.getKey());
            for (S from : states.getEnumConstants()) {
                A aggregate = at.apply(from);
                String where = operation.getKey() + " from " + from;
                if (pairs.containsKey(from)) {
                    operation.getValue().accept(aggregate);
                    assertThat(statusOf.apply(aggregate)).as(where).isEqualTo(pairs.get(from));
                } else {
                    assertThatThrownBy(() -> operation.getValue().accept(aggregate)).as(where).isInstanceOf(DomainException.class)
                            .satisfies(e -> assertThat(((DomainException) e).kind()).isEqualTo(ErrorKind.CONFLICT));
                    assertThat(statusOf.apply(aggregate)).as(where + " leaves the status").isEqualTo(from);
                }
                tried.add(where);
            }
        }
        assertThat(tried).hasSize(operations.size() * states.getEnumConstants().length);
        return tried;
    }
}
