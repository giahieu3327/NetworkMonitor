package com.network_monitor.portal_service.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class DatabaseTypeConverter {

    private DatabaseTypeConverter() {
    }

    /*
     * ============================================================
     * OBJECT MAPPER
     * ============================================================
     */

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /*
     * ============================================================
     * BOOLEAN
     *
     * PostgreSQL:
     *     BOOLEAN
     *
     * SQLite:
     *     0 = false
     *     1 = true
     * ============================================================
     */

    public static Integer booleanToSqlite(Boolean value) {
        if (value == null) {
            return null;
        }

        return value ? 1 : 0;
    }

    public static Boolean sqliteToBoolean(Integer value) {
        if (value == null) {
            return null;
        }

        if (value == 0) {
            return false;
        }

        if (value == 1) {
            return true;
        }

        throw new IllegalArgumentException(
                "SQLite boolean must be 0 or 1, but got: " + value
        );
    }

    public static Boolean sqliteToBoolean(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        if (value instanceof Number number) {
            int numberValue = number.intValue();

            if (numberValue == 0) {
                return false;
            }

            if (numberValue == 1) {
                return true;
            }
        }

        if (value instanceof String stringValue) {
            String normalized = stringValue.trim().toLowerCase();

            if ("0".equals(normalized) || "false".equals(normalized)) {
                return false;
            }

            if ("1".equals(normalized) || "true".equals(normalized)) {
                return true;
            }
        }

        throw new IllegalArgumentException(
                "Cannot convert value to Boolean: " + value
        );
    }

    /*
     * ============================================================
     * INTEGER
     *
     * PostgreSQL:
     *     INTEGER
     *
     * SQLite:
     *     INTEGER
     * ============================================================
     */

    public static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Integer integerValue) {
            return integerValue;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof String stringValue) {
            return Integer.valueOf(stringValue.trim());
        }

        throw new IllegalArgumentException(
                "Cannot convert value to Integer: " + value
        );
    }

    /*
     * ============================================================
     * BIGINT / BIGSERIAL
     *
     * PostgreSQL:
     *     BIGINT
     *     BIGSERIAL
     *
     * SQLite:
     *     INTEGER
     *
     * IMPORTANT:
     * SQLite INTEGER is 64-bit signed integer.
     * Therefore Java Long is used, NOT Integer.
     * ============================================================
     */

    public static Long toLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Long longValue) {
            return longValue;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String stringValue) {
            return Long.valueOf(stringValue.trim());
        }

        throw new IllegalArgumentException(
                "Cannot convert value to Long: " + value
        );
    }

    /*
     * ============================================================
     * DOUBLE PRECISION / REAL
     * ============================================================
     */

    public static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Double doubleValue) {
            return doubleValue;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value instanceof String stringValue) {
            return Double.valueOf(stringValue.trim());
        }

        throw new IllegalArgumentException(
                "Cannot convert value to Double: " + value
        );
    }

    /*
     * ============================================================
     * STRING
     *
     * PostgreSQL:
     *     VARCHAR
     *     TEXT
     *
     * SQLite:
     *     TEXT
     * ============================================================
     */

    public static String toStringValue(Object value) {
        if (value == null) {
            return null;
        }

        return value.toString();
    }

    /*
     * ============================================================
     * TIMESTAMP
     *
     * PostgreSQL:
     *     TIMESTAMP
     *
     * SQLite:
     *     TEXT
     *
     * Main format:
     *     2026-10-07T16:30:15.123
     *
     * This is LocalDateTime because the current PostgreSQL schema
     * uses TIMESTAMP without timezone.
     * ============================================================
     */

    private static final DateTimeFormatter SQLITE_DATETIME_FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static String localDateTimeToSqlite(LocalDateTime value) {
        if (value == null) {
            return null;
        }

        return value.format(SQLITE_DATETIME_FORMATTER);
    }

    public static LocalDateTime sqliteToLocalDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(
                    value,
                    SQLITE_DATETIME_FORMATTER
            );
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid SQLite datetime: " + value,
                    e
            );
        }
    }

    /*
     * ============================================================
     * INSTANT
     *
     * Nếu sau này PostgreSQL đổi sang TIMESTAMPTZ thì nên dùng
     * Instant thay cho LocalDateTime.
     *
     * SQLite:
     *     TEXT
     *
     * Example:
     *     2026-10-07T09:30:15.123Z
     * ============================================================
     */

    public static String instantToSqlite(Instant value) {
        if (value == null) {
            return null;
        }

        return value.toString();
    }

    public static Instant sqliteToInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid SQLite Instant: " + value,
                    e
            );
        }
    }

    /*
     * ============================================================
     * JSONB -> TEXT
     *
     * PostgreSQL:
     *     JSONB
     *
     * SQLite:
     *     TEXT
     * ============================================================
     */

    public static String objectToJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Cannot convert object to JSON",
                    e
            );
        }
    }

    public static JsonNode sqliteJsonToJsonNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Invalid JSON: " + json,
                    e
            );
        }
    }

    public static <T> T jsonToObject(
            String json,
            Class<T> targetType
    ) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readValue(
                    json,
                    targetType
            );
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Cannot convert JSON to "
                            + targetType.getSimpleName(),
                    e
            );
        }
    }

    public static <T> T jsonToObject(
            String json,
            TypeReference<T> typeReference
    ) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readValue(
                    json,
                    typeReference
            );
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Cannot convert JSON",
                    e
            );
        }
    }

    public static Map<String, Object> jsonToMap(String json) {
        return jsonToObject(
                json,
                new TypeReference<Map<String, Object>>() {
                }
        );
    }

    /*
     * ============================================================
     * ENUM
     *
     * PostgreSQL:
     *     thường lưu VARCHAR/TEXT
     *
     * SQLite:
     *     TEXT
     * ============================================================
     */

    public static String enumToString(Enum<?> value) {
        if (value == null) {
            return null;
        }

        return value.name();
    }

    public static <E extends Enum<E>> E stringToEnum(
            String value,
            Class<E> enumClass
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Enum.valueOf(
                    enumClass,
                    value
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid enum value '"
                            + value
                            + "' for "
                            + enumClass.getSimpleName(),
                    e
            );
        }
    }

    /*
     * ============================================================
     * UUID / VARCHAR(36)
     *
     * PostgreSQL:
     *     VARCHAR(36)
     *
     * SQLite:
     *     TEXT
     * ============================================================
     */

    public static String uuidToSqlite(String uuid) {
        return uuid;
    }

    public static String sqliteToUuid(String value) {
        return value;
    }

    /*
     * ============================================================
     * GENERIC:
     *
     * Convert Java object -> SQLite compatible value
     *
     * Dùng khi chuẩn bị dữ liệu trước khi insert vào SQLite.
     * ============================================================
     */

    public static Object toSqliteValue(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof Boolean booleanValue) {
            return booleanToSqlite(booleanValue);
        }

        if (value instanceof LocalDateTime localDateTime) {
            return localDateTimeToSqlite(localDateTime);
        }

        if (value instanceof Instant instant) {
            return instantToSqlite(instant);
        }

        if (value instanceof Enum<?> enumValue) {
            return enumToString(enumValue);
        }

        if (value instanceof JsonNode
                || value instanceof Map<?, ?>
                || value instanceof Iterable<?>) {

            return objectToJson(value);
        }

        /*
         * Long -> SQLite INTEGER
         * Integer -> SQLite INTEGER
         * Double -> SQLite REAL
         * String -> SQLite TEXT
         *
         * Không cần biến đổi thêm.
         */
        return value;
    }

    /*
     * ============================================================
     * NULL SAFE
     * ============================================================
     */

    public static boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }
}