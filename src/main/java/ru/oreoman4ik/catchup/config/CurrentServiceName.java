package ru.oreoman4ik.catchup.config;

import org.springframework.core.env.Environment;

public final class CurrentServiceName {

    private static final int MAX_LENGTH = 120;

    private final String value;

    public CurrentServiceName(
            UnifiedErrorProperties properties,
            Environment environment
    ) {
        if (properties == null) {
            throw new IllegalArgumentException(
                    "properties is required"
            );
        }

        if (environment == null) {
            throw new IllegalArgumentException(
                    "environment is required"
            );
        }

        String configured =
                properties.getServiceName();

        if (configured == null
                || configured.isBlank()) {

            configured =
                    environment.getProperty(
                            "spring.application.name"
                    );
        }

        if (configured == null
                || configured.isBlank()) {

            configured = "application";
        }

        this.value = validate(configured);
    }

    private CurrentServiceName(
            String value
    ) {
        this.value = validate(value);
    }

    public static CurrentServiceName of(
            String value
    ) {
        return new CurrentServiceName(value);
    }

    public String value() {
        return value;
    }

    private static String validate(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return "application";
        }

        String normalized = value.trim();

        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "resolved service name must not "
                            + "exceed "
                            + MAX_LENGTH
                            + " characters"
            );
        }

        if (normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\t') >= 0) {

            throw new IllegalArgumentException(
                    "resolved service name must not "
                            + "contain control characters"
            );
        }

        return normalized;
    }
}