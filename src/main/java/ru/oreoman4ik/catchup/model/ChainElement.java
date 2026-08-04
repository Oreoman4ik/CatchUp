package ru.oreoman4ik.catchup.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Неизменяемый элемент цепочки распространения ошибки.
 *
 * <p>{@code errorCode} содержит стабильный публичный код,
 * а не имя Java-класса исключения.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ChainElement {

    private final String service;
    private final String component;
    private final String operation;
    private final String errorCode;
    private final String message;
    private final Instant timestamp;
    private final Integer status;

    @JsonCreator
    private ChainElement(
            @JsonProperty(value = "service", required = true)
            String service,

            @JsonProperty(value = "component", required = true)
            String component,

            @JsonProperty(value = "operation", required = true)
            String operation,

            @JsonProperty(value = "errorCode", required = true)
            String errorCode,

            @JsonProperty(value = "message", required = true)
            String message,

            @JsonProperty(value = "timestamp", required = true)
            @JsonFormat(
                    shape = JsonFormat.Shape.STRING,
                    pattern =
                            ErrorModelValidation.TIMESTAMP_PATTERN,
                    timezone =
                            ErrorModelValidation.UTC_TIME_ZONE
            )
            Instant timestamp,

            @JsonProperty("status")
            Integer status
    ) {
        this.service = ErrorModelValidation.requiredText(
                "service",
                service,
                120
        );

        this.component = ErrorModelValidation.requiredText(
                "component",
                component,
                160
        );

        this.operation = ErrorModelValidation.requiredText(
                "operation",
                operation,
                160
        );

        this.errorCode = ErrorModelValidation.publicCode(
                "errorCode",
                errorCode
        );

        this.message = ErrorModelValidation.publicMessage(
                "message",
                message
        );

        this.timestamp = ErrorModelValidation.required(
                "timestamp",
                timestamp
        );

        this.status =
                ErrorModelValidation.nullableHttpStatus(
                        "status",
                        status
                );
    }

    private ChainElement(Builder builder) {
        this(
                builder.service,
                builder.component,
                builder.operation,
                builder.errorCode,
                builder.message,
                builder.timestamp,
                builder.status
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty("service")
    public String getService() {
        return service;
    }

    @JsonProperty("component")
    public String getComponent() {
        return component;
    }

    @JsonProperty("operation")
    public String getOperation() {
        return operation;
    }

    @JsonProperty("errorCode")
    public String getErrorCode() {
        return errorCode;
    }

    @JsonProperty("message")
    public String getMessage() {
        return message;
    }

    @JsonProperty("timestamp")
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = ErrorModelValidation.TIMESTAMP_PATTERN,
            timezone = ErrorModelValidation.UTC_TIME_ZONE
    )
    public Instant getTimestamp() {
        return timestamp;
    }

    @JsonProperty("status")
    public Integer getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ChainElement that)) {
            return false;
        }

        return service.equals(that.service)
                && component.equals(that.component)
                && operation.equals(that.operation)
                && errorCode.equals(that.errorCode)
                && message.equals(that.message)
                && timestamp.equals(that.timestamp)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                service,
                component,
                operation,
                errorCode,
                message,
                timestamp,
                status
        );
    }

    @Override
    public String toString() {
        return "ChainElement{"
                + "service='" + service + '\''
                + ", component='" + component + '\''
                + ", operation='" + operation + '\''
                + ", errorCode='" + errorCode + '\''
                + ", message='" + message + '\''
                + ", timestamp=" + timestamp
                + ", status=" + status
                + '}';
    }

    public static final class Builder {

        private String service;
        private String component;
        private String operation;
        private String errorCode;
        private String message;
        private Instant timestamp;
        private Integer status;

        private Builder() {
        }

        public Builder service(String service) {
            this.service = service;
            return this;
        }

        public Builder component(String component) {
            this.component = component;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder status(Integer status) {
            this.status = status;
            return this;
        }

        public ChainElement build() {
            return new ChainElement(this);
        }
    }
}