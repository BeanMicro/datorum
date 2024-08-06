package io.beandev.datorum.data;

import io.beandev.datorum.schema.Attribute;

public record AttributeRecord(BigId id,
        long version,
        Attribute attribute,
        EntityRecord entityRecord,
        Value value) {

    public AttributeRecord(BigId id, long version,
            Attribute attribute,
            EntityRecord entityRecord,
            String stringValue) {
        this(id, version, attribute, entityRecord, new Value(stringValue, null));
    }

    public AttributeRecord(BigId id, long version,
            Attribute attribute,
            EntityRecord entityRecord,
            Long longValue) {
        this(id, version, attribute, entityRecord, new Value(null, longValue));
    }

    public record Value(String stringValue, Long longValue) {
    }

    public record Causation(Record record, Event event) {

    }
}