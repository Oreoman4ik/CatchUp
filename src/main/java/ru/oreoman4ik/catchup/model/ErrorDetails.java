package ru.oreoman4ik.catchup.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import ru.oreoman4ik.catchup.support.ErrorDataLimiter;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class ErrorDetails {

    private static final int MAX_VIOLATIONS = 100;

    private final String resource;

    private final List<FieldViolation> violations;

    private final Long retryAfterSeconds;

    private final TechnicalDetails technical;

    private final TruncationInfo truncation;

    @JsonCreator
    private ErrorDetails(
            @JsonProperty("resource")
            String resource,

            @JsonProperty("violations")
            List<FieldViolation> violations,

            @JsonProperty("retryAfterSeconds")
            Long retryAfterSeconds,

            @JsonProperty("technical")
            TechnicalDetails technical,

            @JsonProperty("truncation")
            TruncationInfo truncation
    ) {
        this.resource =
                ErrorModelValidation
                        .optionalPublicCode(
                                "resource",
                                resource
                        );

        this.violations =
                ErrorModelValidation
                        .immutableOptionalList(
                                "violations",
                                violations,
                                MAX_VIOLATIONS
                        );

        this.retryAfterSeconds =
                ErrorModelValidation
                        .nonNegativeLong(
                                "retryAfterSeconds",
                                retryAfterSeconds
                        );

        this.technical = technical;

        boolean nestedDataTruncated =
                this.violations
                        .stream()
                        .anyMatch(
                                FieldViolation
                                        ::isDataTruncated
                        );

        TruncationInfo effectiveTruncation =
                truncation;

        if (nestedDataTruncated) {
            effectiveTruncation =
                    effectiveTruncation == null
                            ? TruncationInfo.data()
                            : effectiveTruncation.merge(
                            TruncationInfo.data()
                    );
        }

        this.truncation =
                effectiveTruncation != null
                        && effectiveTruncation.isAny()
                        ? effectiveTruncation
                        : null;

        if (this.resource == null
                && this.violations.isEmpty()
                && this.retryAfterSeconds == null
                && this.technical == null
                && this.truncation == null) {

            throw new IllegalArgumentException(
                    "details must contain "
                            + "at least one value"
            );
        }
    }

    private ErrorDetails(
            Builder builder
    ) {
        this(
                builder.resource,
                builder.violations,
                builder.retryAfterSeconds,
                builder.technical,
                builder.truncation
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Добавляет информацию о сокращении,
     * сохраняя остальные details.
     */
    public static ErrorDetails mergeTruncation(
            ErrorDetails existing,
            TruncationInfo additional
    ) {
        if (additional == null
                || !additional.isAny()) {

            return existing;
        }

        if (existing == null) {
            return ErrorDetails.builder()
                    .truncation(additional)
                    .build();
        }

        TruncationInfo merged =
                existing.truncation == null
                        ? additional
                        : existing.truncation
                        .merge(additional);

        return ErrorDetails.builder()
                .resource(existing.resource)
                .violations(existing.violations)
                .retryAfterSeconds(
                        existing.retryAfterSeconds
                )
                .technical(existing.technical)
                .truncation(merged)
                .build();
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

    @JsonProperty("technical")
    public TechnicalDetails getTechnical() {
        return technical;
    }

    @JsonProperty("truncation")
    public TruncationInfo getTruncation() {
        return truncation;
    }

    @Override
    public boolean equals(
            Object object
    ) {
        if (this == object) {
            return true;
        }

        if (!(object
                instanceof ErrorDetails that)) {

            return false;
        }

        return Objects.equals(
                resource,
                that.resource
        )
                && violations.equals(
                that.violations
        )
                && Objects.equals(
                retryAfterSeconds,
                that.retryAfterSeconds
        )
                && Objects.equals(
                technical,
                that.technical
        )
                && Objects.equals(
                truncation,
                that.truncation
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                resource,
                violations,
                retryAfterSeconds,
                technical,
                truncation
        );
    }

    @Override
    public String toString() {
        return "ErrorDetails{"
                + "resource='"
                + resource
                + '\''
                + ", violations="
                + violations
                + ", retryAfterSeconds="
                + retryAfterSeconds
                + ", technical="
                + technical
                + ", truncation="
                + truncation
                + '}';
    }

    public static final class Builder {

        private String resource;

        private List<FieldViolation> violations =
                List.of();

        private Long retryAfterSeconds;

        private TechnicalDetails technical;

        private TruncationInfo truncation;

        private Builder() {
        }

        public Builder resource(
                String resource
        ) {
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
            this.retryAfterSeconds =
                    retryAfterSeconds;
            return this;
        }

        public Builder technical(
                TechnicalDetails technical
        ) {
            this.technical = technical;
            return this;
        }

        public Builder truncation(
                TruncationInfo truncation
        ) {
            this.truncation = truncation;
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

        private final boolean dataTruncated;

        @JsonCreator
        private FieldViolation(
                @JsonProperty(
                        value = "field",
                        required = true
                )
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
            this(
                    field,
                    reasonCode,
                    message,
                    false
            );
        }

        private FieldViolation(
                String field,
                String reasonCode,
                String message,
                boolean dataTruncated
        ) {
            this.field =
                    ErrorModelValidation
                            .requiredText(
                                    "field",
                                    field,
                                    160
                            );

            this.reasonCode =
                    ErrorModelValidation
                            .publicCode(
                                    "reasonCode",
                                    reasonCode
                            );

            ErrorDataLimiter.LimitedText
                    limitedMessage =
                    ErrorModelValidation
                            .publicMessageWithMetadata(
                                    "message",
                                    message
                            );

            this.message =
                    limitedMessage.value();

            this.dataTruncated =
                    dataTruncated
                            || limitedMessage.truncated();
        }

        public static FieldViolation of(
                String field,
                String reasonCode,
                String message
        ) {
            return new FieldViolation(
                    field,
                    reasonCode,
                    message,
                    false
            );
        }

        /**
         * Используется адаптерами, которые были вынуждены
         * сократить часть данных до создания FieldViolation.
         */
        public static FieldViolation of(
                String field,
                String reasonCode,
                String message,
                boolean dataTruncated
        ) {
            return new FieldViolation(
                    field,
                    reasonCode,
                    message,
                    dataTruncated
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

        @JsonIgnore
        public boolean isDataTruncated() {
            return dataTruncated;
        }

        @Override
        public boolean equals(
                Object object
        ) {
            if (this == object) {
                return true;
            }

            if (!(object
                    instanceof FieldViolation that)) {

                return false;
            }

            return field.equals(that.field)
                    && reasonCode.equals(
                    that.reasonCode
            )
                    && message.equals(
                    that.message
            );
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
                    + "field='"
                    + field
                    + '\''
                    + ", reasonCode='"
                    + reasonCode
                    + '\''
                    + ", message='"
                    + message
                    + '\''
                    + '}';
        }
    }
}