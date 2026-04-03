package io.kestra.plugin.notion.database;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.notion.NotionConnection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractDatabaseTask extends NotionConnection {

    public static final String DATABASES_ENDPOINT = "/v1/databases";

    @Schema(
        title = "Database ID",
        description = "Notion database identifier; both UUID and 32-character hex formats are accepted."
    )
    @NotNull
    private Property<String> databaseId;

    /**
     * Renders and validates the API token.
     */
    protected String renderToken(RunContext runContext) throws IllegalVariableEvaluationException {
        return runContext.render(this.getApiToken()).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("apiToken is required")
        );
    }

    /**
     * Renders, normalizes, and validates the database ID.
     */
    protected String renderDatabaseId(RunContext runContext) throws IllegalVariableEvaluationException {
        var raw = runContext.render(this.databaseId).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("databaseId is required")
        );
        return normalizeId(raw);
    }

    /**
     * Converts a 32-character hex string to hyphenated UUID format.
     * If the input is already a valid UUID, it is returned as-is.
     *
     * @throws IllegalArgumentException if the input is neither a valid UUID nor a 32-char hex string
     */
    public static String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID must not be null or blank");
        }

        // Already a valid UUID
        try {
            UUID.fromString(id);
            return id;
        } catch (IllegalArgumentException ignored) {
            // not a UUID yet, try hex conversion
        }

        if (id.matches("^[0-9a-fA-F]{32}$")) {
            var formatted = id.substring(0, 8) + "-" +
                id.substring(8, 12) + "-" +
                id.substring(12, 16) + "-" +
                id.substring(16, 20) + "-" +
                id.substring(20);
            // Validate the formatted result
            UUID.fromString(formatted);
            return formatted;
        }

        throw new IllegalArgumentException("ID must be a valid UUID or a 32-character hex string, got: " + id);
    }

    @Override
    protected String getEndpoint() {
        return DATABASES_ENDPOINT;
    }

    /**
     * Recursively walks a rendered value and coerces string literals "true"/"false" to their
     * Boolean equivalents. This is needed because Pebble expressions inside quoted YAML strings
     * (e.g. {@code checkbox: "{{ myVar }}"}) are always rendered as Strings by Kestra, even when
     * the underlying value is a boolean. The Notion API strictly expects JSON booleans, not strings,
     * for checkbox and similar boolean-typed properties.
     */
    @SuppressWarnings("unchecked")
    protected static Object coerceBooleans(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>(map.size());
            for (var entry : map.entrySet()) {
                result.put((String) entry.getKey(), coerceBooleans(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(AbstractDatabaseTask::coerceBooleans).toList();
        }
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }
        return value;
    }
}
