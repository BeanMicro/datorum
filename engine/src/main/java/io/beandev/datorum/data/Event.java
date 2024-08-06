package io.beandev.datorum.data;

import io.beandev.datorum.schema.Aggregate;

import static java.lang.System.out;

public record Event(BigId id,
                    BigId correlationId,
                    Causation causation,
                    Operation[] operations) {
    public Event {
        // TODO: validate operations
        out.println("Event.Event");
    }

    public Event(BigId id, Operation[] operations) {
        this(id, id, null, operations);
        out.println("Event.Event[2]");
    }

    public record Causation(long eventId, Aggregate eventAggregate) {
    }

    public record Operation(Operator operator, Operand operand) {
    }

    public record Operand(AttributeRecord value) {
    }

    public enum Operator {CREATE, UPDATE, DELETE, PATCH}
}
