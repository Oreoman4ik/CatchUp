package ru.oreoman4ik.catchup.support;

import java.util.regex.Pattern;

public final class ErrorDataLimiter {

    public static final int
            MAX_PUBLIC_MESSAGE_LENGTH = 500;

    private static final Pattern
            CONTROL_CHARACTERS =
            Pattern.compile("[\\r\\n\\t]");

    private ErrorDataLimiter() {
    }

    public static LimitedText publicMessage(
            String value
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "message is required"
            );
        }

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "message must not be blank"
            );
        }

        if (CONTROL_CHARACTERS
                .matcher(normalized)
                .find()) {

            throw new IllegalArgumentException(
                    "message must not contain "
                            + "control characters"
            );
        }

        if (normalized.length()
                <= MAX_PUBLIC_MESSAGE_LENGTH) {

            return new LimitedText(
                    normalized,
                    false
            );
        }

        return new LimitedText(
                normalized.substring(
                        0,
                        MAX_PUBLIC_MESSAGE_LENGTH
                ),
                true
        );
    }

    public static LimitedText technicalText(
            String value,
            int maxLength
    ) {
        if (value == null
                || value.isBlank()) {

            return new LimitedText(
                    null,
                    false
            );
        }

        String normalized = value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();

        if (normalized.length()
                <= maxLength) {

            return new LimitedText(
                    normalized,
                    false
            );
        }

        return new LimitedText(
                normalized.substring(
                        0,
                        maxLength
                ),
                true
        );
    }

    public record LimitedText(
            String value,
            boolean truncated
    ) {
    }
}