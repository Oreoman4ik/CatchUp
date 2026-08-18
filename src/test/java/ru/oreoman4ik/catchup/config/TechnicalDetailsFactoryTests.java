package ru.oreoman4ik.catchup.config;

import org.junit.jupiter.api.Test;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.TechnicalDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalDetailsFactoryTests {

    @Test
    void technicalDetailsAreDisabledByDefault() {
        UnifiedErrorProperties properties =
                new UnifiedErrorProperties();

        TechnicalDetailsFactory factory =
                new TechnicalDetailsFactory(
                        properties
                );

        ErrorDetails result =
                factory.enrich(
                        null,
                        new IllegalStateException(
                                "password=secret"
                        )
                );

        assertThat(result)
                .isNull();
    }

    @Test
    void safePublicDetailsArePreserved() {
        UnifiedErrorProperties properties =
                new UnifiedErrorProperties();

        ErrorDetails existing =
                ErrorDetails.builder()
                        .resource("ITEM")
                        .build();

        ErrorDetails result =
                new TechnicalDetailsFactory(
                        properties
                ).enrich(
                        existing,
                        new IllegalStateException(
                                "secret"
                        )
                );

        assertThat(result)
                .isEqualTo(existing);
    }

    @Test
    void remoteTechnicalDetailsAreRemoved() {
        UnifiedErrorProperties properties =
                new UnifiedErrorProperties();

        ErrorDetails remote =
                ErrorDetails.builder()
                        .resource("ITEM")
                        .technical(
                                new TechnicalDetails(
                                        "remote.Exception",
                                        "remote password=secret",
                                        List.of(
                                                "remote.stack.Trace"
                                        )
                                )
                        )
                        .build();

        ErrorDetails sanitized =
                new TechnicalDetailsFactory(
                        properties
                ).publicOnly(
                        remote
                );

        assertThat(sanitized)
                .isNotNull();

        assertThat(sanitized.getResource())
                .isEqualTo("ITEM");

        assertThat(sanitized.getTechnical())
                .isNull();
    }

    @Test
    void localTechnicalDetailsCanBeEnabled() {
        UnifiedErrorProperties properties =
                new UnifiedErrorProperties();

        properties.setIncludeTechnicalDetails(
                true
        );

        TechnicalDetailsFactory factory =
                new TechnicalDetailsFactory(
                        properties
                );

        ErrorDetails details =
                factory.enrich(
                        null,
                        new IllegalStateException(
                                "password=secret"
                        )
                );

        assertThat(details)
                .isNotNull();

        assertThat(details.getTechnical())
                .isNotNull();

        assertThat(
                details.getTechnical()
                        .getExceptionClass()
        ).isEqualTo(
                IllegalStateException.class
                        .getName()
        );

        /*
         * Technical exception message
         * не публикуется клиенту.
         */
        assertThat(
                details.getTechnical()
                        .getExceptionMessage()
        ).isNull();

        assertThat(
                details.getTechnical()
                        .getStackTrace()
        ).isEmpty();
    }

    @Test
    void stackTraceIsLimitedByConfiguredLineCount() {
        UnifiedErrorProperties properties =
                new UnifiedErrorProperties();

        properties.setIncludeTechnicalDetails(
                true
        );

        properties.setIncludeStackTrace(
                true
        );

        properties.setMaxStackTraceLines(
                2
        );

        IllegalStateException cause =
                new IllegalStateException(
                        "secret"
                );

        cause.setStackTrace(
                new StackTraceElement[]{
                        frame("A", "first"),
                        frame("B", "second"),
                        frame("C", "third"),
                        frame("D", "fourth")
                }
        );

        ErrorDetails details =
                new TechnicalDetailsFactory(
                        properties
                ).enrich(
                        null,
                        cause
                );

        assertThat(
                details.getTechnical()
                        .getStackTrace()
        ).hasSize(2);

        assertThat(
                details.getTruncation()
                        .isTechnicalDetails()
        ).isTrue();
    }

    @Test
    void stackTraceLineLengthIsLimited() {
        UnifiedErrorProperties properties =
                new UnifiedErrorProperties();

        properties.setIncludeTechnicalDetails(
                true
        );

        properties.setIncludeStackTrace(
                true
        );

        properties.setMaxTechnicalTextLength(
                128
        );

        IllegalStateException cause =
                new IllegalStateException(
                        "secret"
                );

        cause.setStackTrace(
                new StackTraceElement[]{
                        new StackTraceElement(
                                "X".repeat(400),
                                "method",
                                "Source.java",
                                100
                        )
                }
        );

        ErrorDetails details =
                new TechnicalDetailsFactory(
                        properties
                ).enrich(
                        null,
                        cause
                );

        assertThat(
                details.getTechnical()
                        .getStackTrace()
                        .getFirst()
        ).hasSize(128);

        assertThat(
                details.getTruncation()
                        .isTechnicalDetails()
        ).isTrue();
    }

    private static StackTraceElement frame(
            String className,
            String method
    ) {
        return new StackTraceElement(
                className,
                method,
                "Source.java",
                10
        );
    }
}