package ru.oreoman4ik.catchup.model;

import java.util.List;
import java.util.regex.Pattern;

final class ErrorModelValidation {

    static final String TIMESTAMP_PATTERN =
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    static final String UTC_TIME_ZONE = "UTC";

    private static final Pattern PUBLIC_CODE =
            Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    private static final Pattern CONTROL_CHARACTERS =
            Pattern.compile("[\\r\\n\\t]");

    private static final String JAVA_EXCEPTION_SUFFIX =
            "EXCEPTION";

    private ErrorModelValidation() {
    }

    static <T> T required(String fieldName, T value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }

        return value;
    }

    static String requiredText(
            String fieldName,
            String value,
            int maxLength
    ) {
        required(fieldName, value);

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not exceed "
                            + maxLength
                            + " characters"
            );
        }

        if (CONTROL_CHARACTERS.matcher(normalized).find()) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not contain control characters"
            );
        }

        return normalized;
    }

    static String publicCode(String fieldName, String value) {
        String normalized = requiredText(
                fieldName,
                value,
                64
        );

        if (!PUBLIC_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must be a public code matching "
                            + PUBLIC_CODE.pattern()
            );
        }

        if (normalized.endsWith(JAVA_EXCEPTION_SUFFIX)) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not contain a Java exception type"
            );
        }

        return normalized;
    }

    static String optionalPublicCode(
            String fieldName,
            String value
    ) {
        return value == null
                ? null
                : publicCode(fieldName, value);
    }

    /**
     * Проверяет только структуру публичного текста.
     *
     * <p>Содержательная безопасность обеспечивается тем, что сюда
     * передаётся сообщение из контролируемого каталога, а не
     * {@code exception.getMessage()}.</p>
     */
    static String publicMessage(
            String fieldName,
            String value
    ) {
        return requiredText(fieldName, value, 500);
    }

    static int httpStatus(String fieldName, int value) {
        if (value < 400 || value > 599) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must be an HTTP error status "
                            + "from 400 to 599"
            );
        }

        return value;
    }

    static Integer nullableHttpStatus(
            String fieldName,
            Integer value
    ) {
        return value == null
                ? null
                : httpStatus(fieldName, value);
    }

    static Long nonNegativeLong(
            String fieldName,
            Long value
    ) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must be greater than or equal to zero"
            );
        }

        return value;
    }

    static <T> List<T> immutableNonEmptyList(
            String fieldName,
            List<T> source,
            int maxSize
    ) {
        required(fieldName, source);

        if (source.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be empty"
            );
        }

        return immutableList(
                fieldName,
                source,
                maxSize
        );
    }

    static <T> List<T> immutableOptionalList(
            String fieldName,
            List<T> source,
            int maxSize
    ) {
        return source == null
                ? List.of()
                : immutableList(
                fieldName,
                source,
                maxSize
        );
    }

    private static <T> List<T> immutableList(
            String fieldName,
            List<T> source,
            int maxSize
    ) {
        if (source.size() > maxSize) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not contain more than "
                            + maxSize
                            + " elements"
            );
        }

        for (int index = 0; index < source.size(); index++) {
            if (source.get(index) == null) {
                throw new IllegalArgumentException(
                        fieldName
                                + " must not contain null at index "
                                + index
                );
            }
        }

        return List.copyOf(source);
    }
}