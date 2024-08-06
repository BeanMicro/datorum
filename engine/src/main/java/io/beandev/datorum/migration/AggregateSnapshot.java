package io.beandev.datorum.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.beandev.datorum.schema.Aggregate;
import io.beandev.datorum.schema.Attribute;
import io.beandev.datorum.schema.Entity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

public record AggregateSnapshot(Aggregate aggregate, Entity[] entities, Attribute[] attributes) {
    public String hash() {
        // Convert the array of objects to JSON string
        ObjectMapper objectMapper = new ObjectMapper();
        String json;
        try {
            String aggregateJson = objectMapper.writeValueAsString(new HashMap<>() {
                {
                    put("id", aggregate.id());
                    put("name", aggregate.name());
                    put("version", aggregate.version());
                    put("context_id", aggregate.context().id());
                }
            });

            String entitiesJson = objectMapper.writeValueAsString(
                    Arrays.stream(entities).map(entity -> new HashMap<>() {
                        {
                            put("id", entity.id());
                            put("name", entity.name());
                            put("aggregate_id", entity.aggregate().id());
                            put("is_root", entity.isRoot());
                        }
                    }).toArray(HashMap[]::new));

            String attributesJson = objectMapper.writeValueAsString(
                    Arrays.stream(attributes).map(attribute -> new HashMap<>() {
                        {
                            put("id", attribute.id());
                            put("name", attribute.name());
                            put("entity_id", attribute.entity().id());
                            put("type", attribute.type());
                            put("is_nullable", attribute.isNullable());
                            put("is_unique", attribute.isUnique());
                        }
                    }).toArray(HashMap[]::new));
            json = objectMapper.writeValueAsString(new String[] {
                    aggregateJson,
                    entitiesJson,
                    attributesJson
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e); // TODO: handle this exception
        }

        // Create MessageDigest instance for SHA-1
        MessageDigest shaDigest = null;
        try {
            shaDigest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // TODO: handle this exception
        }

        // Apply SHA-1 Message Digest
        byte[] result = shaDigest.digest(json.getBytes());

        StringBuilder sb = new StringBuilder();
        for (byte b : result) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
