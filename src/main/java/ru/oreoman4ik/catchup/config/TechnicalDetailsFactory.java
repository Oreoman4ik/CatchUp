package ru.oreoman4ik.catchup.config;

import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.TechnicalDetails;
import ru.oreoman4ik.catchup.model.TruncationInfo;
import ru.oreoman4ik.catchup.support.ErrorDataLimiter;

import java.util.ArrayList;
import java.util.List;

public final class TechnicalDetailsFactory {

    private static final int
            MAX_EXCEPTION_CLASS_LENGTH = 300;

    private final UnifiedErrorProperties properties;

    public TechnicalDetailsFactory(
            UnifiedErrorProperties properties
    ) {
        if (properties == null) {
            throw new IllegalArgumentException(
                    "properties is required"
            );
        }

        this.properties = properties;
    }

    /**
     * Сохраняет безопасные публичные details
     * и при необходимости добавляет локальные
     * technical details.
     */
    public ErrorDetails enrich(
            ErrorDetails existing,
            Throwable throwable
    ) {
        ErrorDetails publicDetails =
                publicOnly(existing);

        if (!properties
                .isIncludeTechnicalDetails()) {

            return publicDetails;
        }

        if (throwable == null) {
            return publicDetails;
        }

        TechnicalResult result =
                createTechnicalDetails(
                        throwable
                );

        ErrorDetails enriched =
                ErrorDetails.builder()
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
                        .technical(
                                result.details()
                        )
                        .truncation(
                                publicDetails == null
                                        ? null
                                        : publicDetails
                                        .getTruncation()
                        )
                        .build();

        if (result.truncated()) {
            enriched =
                    ErrorDetails.mergeTruncation(
                            enriched,
                            new TruncationInfo(
                                    false,
                                    false,
                                    true,
                                    false
                            )
                    );
        }

        return enriched;
    }

    /**
     * Удаляет technical details, полученные
     * от другого сервиса.
     *
     * <p>Удалённый stack trace не должен
     * автоматически проксироваться клиенту.</p>
     */
    public ErrorDetails publicOnly(
            ErrorDetails existing
    ) {
        if (existing == null) {
            return null;
        }

        if (existing.getResource() == null
                && existing
                .getViolations()
                .isEmpty()
                && existing
                .getRetryAfterSeconds()
                == null
                && existing
                .getTruncation()
                == null) {

            return null;
        }

        return ErrorDetails.builder()
                .resource(
                        existing.getResource()
                )
                .violations(
                        existing.getViolations()
                )
                .retryAfterSeconds(
                        existing
                                .getRetryAfterSeconds()
                )
                .truncation(
                        existing.getTruncation()
                )
                .build();
    }

    private TechnicalResult
    createTechnicalDetails(
            Throwable throwable
    ) {
        boolean truncated = false;

        ErrorDataLimiter.LimitedText
                exceptionClass =
                ErrorDataLimiter
                        .technicalText(
                                throwable
                                        .getClass()
                                        .getName(),
                                MAX_EXCEPTION_CLASS_LENGTH
                        );

        if (exceptionClass.truncated()) {
            truncated = true;
        }

        List<String> stackTrace =
                List.of();

        if (properties
                .isIncludeStackTrace()) {

            StackTraceElement[] original =
                    throwable.getStackTrace();

            int retained =
                    Math.min(
                            original.length,
                            properties
                                    .getMaxStackTraceLines()
                    );

            if (original.length > retained) {
                truncated = true;
            }

            List<String> lines =
                    new ArrayList<>(
                            retained
                    );

            for (int index = 0;
                 index < retained;
                 index++) {

                ErrorDataLimiter.LimitedText
                        line =
                        ErrorDataLimiter
                                .technicalText(
                                        original[index]
                                                .toString(),
                                        properties
                                                .getMaxTechnicalTextLength()
                                );

                if (line.truncated()) {
                    truncated = true;
                }

                lines.add(
                        line.value()
                );
            }

            stackTrace =
                    List.copyOf(lines);
        }

        /*
         * Throwable.getMessage() намеренно
         * не отправляется клиенту.
         *
         * Полное сообщение остаётся в логах,
         * потому что logger получает originalCause.
         */
        TechnicalDetails details =
                new TechnicalDetails(
                        exceptionClass.value(),
                        null,
                        stackTrace
                );

        return new TechnicalResult(
                details,
                truncated
        );
    }

    private record TechnicalResult(
            TechnicalDetails details,
            boolean truncated
    ) {
    }
}