package ru.oreoman4ik.catchup.config;

import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.TechnicalDetails;

import java.util.Arrays;
import java.util.List;

public final class TechnicalDetailsFactory {

    private static final int MAX_STACK_TRACE_LINES =
            100;

    private static final int MAX_TECHNICAL_TEXT_LENGTH =
            1000;

    private final UnifiedErrorProperties properties;

    public TechnicalDetailsFactory(
            UnifiedErrorProperties properties
    ) {
        this.properties = properties;
    }

    public ErrorDetails enrich(
            ErrorDetails existing,
            Throwable throwable
    ) {
        ErrorDetails publicDetails =
                sanitizeRemote(existing);

        if (!properties
                .isIncludeTechnicalDetails()) {

            return publicDetails;
        }

        TechnicalDetails technical =
                createTechnicalDetails(
                        throwable
                );

        return ErrorDetails.builder()
                .resource(
                        publicDetails == null
                                ? null
                                : publicDetails
                                .getResource()
                )
                .violations(
                        publicDetails == null
                                ? List.of()
                                : publicDetails
                                .getViolations()
                )
                .retryAfterSeconds(
                        publicDetails == null
                                ? null
                                : publicDetails
                                .getRetryAfterSeconds()
                )
                .technical(technical)
                .build();
    }

    /**
     * Не позволяет удалённому сервису включить
     * technical details, если текущий сервис
     * сам их запрещает.
     */
    public ErrorDetails sanitizeRemote(
            ErrorDetails existing
    ) {
        if (existing == null) {
            return null;
        }

        if (properties
                .isIncludeTechnicalDetails()) {

            return existing;
        }

        if (existing.getResource() == null
                && existing.getViolations().isEmpty()
                && existing
                .getRetryAfterSeconds() == null) {

            return null;
        }

        return ErrorDetails.builder()
                .resource(existing.getResource())
                .violations(
                        existing.getViolations()
                )
                .retryAfterSeconds(
                        existing
                                .getRetryAfterSeconds()
                )
                .build();
    }

    private TechnicalDetails createTechnicalDetails(
            Throwable throwable
    ) {
        String exceptionMessage =
                normalizeTechnicalText(
                        throwable.getMessage()
                );

        List<String> stackTrace =
                properties.isIncludeStackTrace()
                        ? Arrays.stream(
                                throwable
                                        .getStackTrace()
                        )
                        .limit(
                                MAX_STACK_TRACE_LINES
                        )
                        .map(
                                StackTraceElement
                                        ::toString
                        )
                        .map(
                                TechnicalDetailsFactory
                                        ::normalizeTechnicalText
                        )
                        .toList()
                        : List.of();

        return new TechnicalDetails(
                throwable
                        .getClass()
                        .getName(),
                exceptionMessage,
                stackTrace
        );
    }

    private static String normalizeTechnicalText(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return null;
        }

        String normalized = value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();

        if (normalized.length()
                > MAX_TECHNICAL_TEXT_LENGTH) {

            return normalized.substring(
                    0,
                    MAX_TECHNICAL_TEXT_LENGTH
            );
        }

        return normalized;
    }
}