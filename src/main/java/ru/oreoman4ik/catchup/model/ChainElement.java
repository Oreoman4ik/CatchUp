package ru.oreoman4ik.catchup.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Неизменяемый публичный элемент цепочки распространения ошибки.
 *
 * <p>{@code exceptionType} в JSON содержит стабильный публичный
 * код причины, а не имя Java-класса исключения.</p>
 *
 * <p>{@code message} должен поступать из контролируемого каталога
 * публичных сообщений. Нельзя передавать в него результат
 * {@link Throwable#getMessage()}.</p>
 *
 * <p>{@code status} содержит числовой HTTP-статус ошибки от 400
 * до 599. Значение {@code null} допустимо, если на этом шаге
 * цепочки статус ещё не был определён.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ChainElement {

    private final String service;
    private final String component;
    private final String operation;
    private final String causeCode;
    private final String publicMessage;
    private final Instant timestamp;
    private final Integer httpStatus;

    @JsonCreator
    private ChainElement(
            @JsonProperty(value = "service", required = true)
            String service,

            @JsonProperty(value = "component", required = true)
            String component,

            @JsonProperty(value = "operation", required = true)
            String operation,

            @JsonProperty(
                    value = "exceptionType",
                    required = true
            )
            @JsonAlias({"exception_type", "causeCode"})
            String causeCode,

            @JsonProperty(value = "message", required = true)
            @JsonAlias("publicMessage")
            String publicMessage,

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
            @JsonAlias("httpStatus")
            Integer httpStatus
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

        this.causeCode = ErrorModelValidation.publicCode(
                "exceptionType",
                causeCode
        );

        this.publicMessage = ErrorModelValidation.publicMessage(
                "message",
                publicMessage
        );

        this.timestamp = ErrorModelValidation.required(
                "timestamp",
                timestamp
        );

        this.httpStatus =
                ErrorModelValidation.nullableHttpStatus(
                        "status",
                        httpStatus
                );
    }

    private ChainElement(Builder builder) {
        this(
                builder.service,
                builder.component,
                builder.operation,
                builder.causeCode,
                builder.publicMessage,
                builder.timestamp,
                builder.httpStatus
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

    /**
     * @return стабильный публичный код вида
     * {@code COMPONENT_NOT_FOUND}
     */
    @JsonProperty("exceptionType")
    public String getCauseCode() {
        return causeCode;
    }

    /**
     * @return контролируемое сообщение для внешнего клиента
     */
    @JsonProperty("message")
    public String getPublicMessage() {
        return publicMessage;
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

    /**
     * @return числовой HTTP-статус ошибки или {@code null}, если
     * статус на этом шаге не определён
     */
    @JsonProperty("status")
    public Integer getHttpStatus() {
        return httpStatus;
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
                && causeCode.equals(that.causeCode)
                && publicMessage.equals(that.publicMessage)
                && timestamp.equals(that.timestamp)
                && Objects.equals(
                httpStatus,
                that.httpStatus
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                service,
                component,
                operation,
                causeCode,
                publicMessage,
                timestamp,
                httpStatus
        );
    }

    @Override
    public String toString() {
        return "ChainElement{"
                + "service='" + service + '\''
                + ", component='" + component + '\''
                + ", operation='" + operation + '\''
                + ", causeCode='" + causeCode + '\''
                + ", publicMessage='" + publicMessage + '\''
                + ", timestamp=" + timestamp
                + ", httpStatus=" + httpStatus
                + '}';
    }

    public static final class Builder {

        private String service;
        private String component;
        private String operation;
        private String causeCode;
        private String publicMessage;
        private Instant timestamp;
        private Integer httpStatus;

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

        public Builder causeCode(String causeCode) {
            this.causeCode = causeCode;
            return this;
        }

        public Builder publicMessage(String publicMessage) {
            this.publicMessage = publicMessage;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder httpStatus(Integer httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        public ChainElement build() {
            return new ChainElement(this);
        }
    }
}