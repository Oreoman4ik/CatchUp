package ru.oreoman4ik.catchup.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class TechnicalDetails {

    private static final int
            MAX_STACK_TRACE_LINES = 100;

    private static final int
            MAX_STACK_TRACE_LINE_LENGTH = 1000;

    private final String exceptionClass;

    private final String exceptionMessage;

    private final List<String> stackTrace;

    @JsonCreator
    public TechnicalDetails(
            @JsonProperty(
                    value = "exceptionClass",
                    required = true
            )
            String exceptionClass,

            @JsonProperty("exceptionMessage")
            String exceptionMessage,

            @JsonProperty("stackTrace")
            List<String> stackTrace
    ) {
        this.exceptionClass =
                ErrorModelValidation
                        .requiredText(
                                "exceptionClass",
                                exceptionClass,
                                300
                        );

        this.exceptionMessage =
                exceptionMessage == null
                        ? null
                        : ErrorModelValidation
                        .requiredText(
                                "exceptionMessage",
                                exceptionMessage,
                                1000
                        );

        this.stackTrace =
                validateStackTrace(
                        stackTrace
                );
    }

    @JsonProperty("exceptionClass")
    public String getExceptionClass() {
        return exceptionClass;
    }

    @JsonProperty("exceptionMessage")
    public String getExceptionMessage() {
        return exceptionMessage;
    }

    @JsonProperty("stackTrace")
    public List<String> getStackTrace() {
        return stackTrace;
    }

    private static List<String>
    validateStackTrace(
            List<String> source
    ) {
        if (source == null
                || source.isEmpty()) {

            return List.of();
        }

        if (source.size()
                > MAX_STACK_TRACE_LINES) {

            throw new IllegalArgumentException(
                    "stackTrace must not contain "
                            + "more than "
                            + MAX_STACK_TRACE_LINES
                            + " elements"
            );
        }

        List<String> result =
                new ArrayList<>(
                        source.size()
                );

        for (int index = 0;
             index < source.size();
             index++) {

            result.add(
                    ErrorModelValidation
                            .requiredText(
                                    "stackTrace["
                                            + index
                                            + "]",
                                    source.get(index),
                                    MAX_STACK_TRACE_LINE_LENGTH
                            )
            );
        }

        return List.copyOf(result);
    }

    @Override
    public boolean equals(
            Object object
    ) {
        if (this == object) {
            return true;
        }

        if (!(object
                instanceof TechnicalDetails that)) {

            return false;
        }

        return exceptionClass.equals(
                that.exceptionClass
        )
                && Objects.equals(
                exceptionMessage,
                that.exceptionMessage
        )
                && stackTrace.equals(
                that.stackTrace
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                exceptionClass,
                exceptionMessage,
                stackTrace
        );
    }

    @Override
    public String toString() {
        return "TechnicalDetails{"
                + "exceptionClass='"
                + exceptionClass
                + '\''
                + ", exceptionMessage='"
                + exceptionMessage
                + '\''
                + ", stackTrace="
                + stackTrace
                + '}';
    }
}