package ru.oreoman4ik.catchup.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Типизированные дополнительные данные публичной ошибки.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class ErrorDetails {

    private static final int MAX_VIOLATIONS = 100;

    private final String resource;
    private final List<FieldViolation> violations;
    private final Long retryAfterSeconds;

    @JsonCreator
    private ErrorDetails(
            @JsonProperty("resource")
            String resource,

            @JsonProperty("violations")
            List<FieldViolation> violations,

            @JsonProperty("retryAfterSeconds")
            Long retryAfterSeconds
    ) {
        this.resource =
                ErrorModelValidation.optionalPublicCode(
                        "resource",
                        resource
                );

        this.violations =
                ErrorModelValidation.immutableOptionalList(
                        "violations",
                        violations,
                        MAX_VIOLATIONS
                );

        this.retryAfterSeconds =
                ErrorModelValidation.nonNegativeLong(
                        "retryAfterSeconds",
                        retryAfterSeconds
                );

        if (this.resource == null
                && this.violations.isEmpty()
                && this.retryAfterSeconds == null) {

            throw new IllegalArgumentException(
                    "details must contain at least one public value"
            );
        }
    }

    private ErrorDetails(Builder builder) {
        this(
                builder.resource,
                builder.violations,
                builder.retryAfterSeconds
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty("resource")
    public String getResource() {
        return resource;
    }

    @JsonProperty("violations")
    public List<FieldViolation> getViolations() {
        return violations;
    }

    @JsonProperty("retryAfterSeconds")
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ErrorDetails that)) {
            return false;
        }

        return Objects.equals(resource, that.resource)
                && violations.equals(that.violations)
                && Objects.equals(
                retryAfterSeconds,
                that.retryAfterSeconds
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                resource,
                violations,
                retryAfterSeconds
        );
    }

    @Override
    public String toString() {
        return "ErrorDetails{"
                + "resource='" + resource + '\''
                + ", violations=" + violations
                + ", retryAfterSeconds=" + retryAfterSeconds
                + '}';
    }

    public static final class Builder {

        private String resource;
        private List<FieldViolation> violations = List.of();
        private Long retryAfterSeconds;

        private Builder() {
        }

        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        public Builder violations(
                List<FieldViolation> violations
        ) {
            this.violations = violations;
            return this;
        }

        public Builder retryAfterSeconds(
                Long retryAfterSeconds
        ) {
            this.retryAfterSeconds = retryAfterSeconds;
            return this;
        }

        public ErrorDetails build() {
            return new ErrorDetails(this);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class FieldViolation {

        private final String field;
        private final String reasonCode;
        private final String message;

        @JsonCreator
        private FieldViolation(
                @JsonProperty(value = "field", required = true)
                String field,

                @JsonProperty(
                        value = "reasonCode",
                        required = true
                )
                String reasonCode,

                @JsonProperty(
                        value = "message",
                        required = true
                )
                String message
        ) {
            this.field = ErrorModelValidation.requiredText(
                    "field",
                    field,
                    160
            );

            this.reasonCode = ErrorModelValidation.publicCode(
                    "reasonCode",
                    reasonCode
            );

            this.message = ErrorModelValidation.publicMessage(
                    "message",
                    message
            );
        }

        public static FieldViolation of(
                String field,
                String reasonCode,
                String message
        ) {
            return new FieldViolation(
                    field,
                    reasonCode,
                    message
            );
        }

        @JsonProperty("field")
        public String getField() {
            return field;
        }

        @JsonProperty("reasonCode")
        public String getReasonCode() {
            return reasonCode;
        }

        @JsonProperty("message")
        public String getMessage() {
            return message;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof FieldViolation that)) {
                return false;
            }

            return field.equals(that.field)
                    && reasonCode.equals(that.reasonCode)
                    && message.equals(that.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    field,
                    reasonCode,
                    message
            );
        }

        @Override
        public String toString() {
            return "FieldViolation{"
                    + "field='" + field + '\''
                    + ", reasonCode='" + reasonCode + '\''
                    + ", message='" + message + '\''
                    + '}';
        }
    }
}