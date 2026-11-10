package org.example.helptreeservice.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a small list of attachment URLs as a JSON array in a TEXT column.
 *
 * <p>Using an explicit converter avoids depending on a JSONB mapping and keeps
 * the partitioned {@code messages} table free of type-specific columns. Values
 * are always written by the server, so malformed JSON is treated as "no
 * attachments" rather than failing a read.</p>
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Не удалось сериализовать список вложений", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(MAPPER.readValue(dbData, TYPE));
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}
