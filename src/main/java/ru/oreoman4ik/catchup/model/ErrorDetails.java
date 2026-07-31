package ru.oreoman4ik.catchup.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Типизированные и безопасные дополнительные данные публичной
 * ошибки.
 *
 * <p>Объект допускается только при наличии хотя бы одного
 * значения. В него нельзя помещать stack trace, SQL, токены,
 * внутренние DTO или произвольные объекты.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class ErrorDetails {

    private final String resource;
    private final List<FieldViolation> violations;
    private final Long retryAfterSeconds;

    @JsonCreator
    private ErrorDetails(
            @JsonProperty("resource")
            String resource,

            @JsonProperty("violations")
            @JsonAlias("fieldErrors")
            List<FieldViolation> violations,

            @JsonProperty("retryAfterSeconds")
            @JsonAlias("retry_after_seconds")
            Long retryAfterSeconds
    ) {
        this.resource = ErrorModelValidation.optionalPublicCode(
                "resource",
                resource
        );

        this.violations = ErrorModelValidation.immutableOptionalList(
                "violations",
                violations
        );

        this.retryAfterSeconds = ErrorModelValidation.nonNegativeLong(
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

    /**
     * Публичный машинный код ресурса, например {@code COMPONENT}.
     */
    @JsonProperty("resource")
    public String getResource() {
        return resource;
    }

    /**
     * Неизменяемый список публичных ошибок валидации.
     */
    @JsonProperty("violations")
    public List<FieldViolation> getViolations() {
        return violations;
    }

    /**
     * Рекомендованное время до повторной попытки.
     */
    @JsonProperty("retryAfterSeconds")
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
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

        public Builder violations(List<FieldViolation> violations) {
            this.violations = violations;
            return this;
        }

        public Builder retryAfterSeconds(Long retryAfterSeconds) {
            this.retryAfterSeconds = retryAfterSeconds;
            return this;
        }

        public ErrorDetails build() {
            return new ErrorDetails(this);
        }
    }

    /**
     * Публичное описание ошибки конкретного поля.
     *
     * <p>Отклонённое значение намеренно не возвращается, поскольку
     * оно может содержать персональные или технические данные.</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class FieldViolation {

        private final String field;
        private final String reasonCode;
        private final String publicMessage;

        @JsonCreator
        private FieldViolation(
                @JsonProperty(value = "field", required = true)
                String field,

                @JsonProperty(value = "reasonCode", required = true)
                @JsonAlias("code")
                String reasonCode,

                @JsonProperty(value = "message", required = true)
                @JsonAlias("publicMessage")
                String publicMessage
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

            this.publicMessage = ErrorModelValidation.publicMessage(
                    "message",
                    publicMessage
            );
        }

        public static FieldViolation of(
                String field,
                String reasonCode,
                String publicMessage
        ) {
            return new FieldViolation(
                    field,
                    reasonCode,
                    publicMessage
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
        public String getPublicMessage() {
            return publicMessage;
        }
    }
}
