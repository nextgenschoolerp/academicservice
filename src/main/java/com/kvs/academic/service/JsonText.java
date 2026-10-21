package com.kvs.academic.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Reads and writes the small JSON documents kept in text columns (quiz options, attempt orders and answers,
 * annotations, remedial student lists), so they work the same on PostgreSQL and the H2 test database.
 */
@Component
public class JsonText {

    @Autowired
    private ObjectMapper objectMapper;

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + value.getClass().getSimpleName(), e);
        }
    }

    public <T> T read(String json, TypeReference<T> type, T empty) {
        if (json == null || json.isBlank()) {
            return empty;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON could not be read", e);
        }
    }
}
