package ru.oreoman4ik.catchup.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Публичный элемент цепочки распространения ошибки.
 *
 * <p>{@code exceptionType} в JSON является стабильным публичным
 * кодом причины, а не именем Java-класса исключения.</p>
 *
 * <p>{@code message} предназначено только для внешнего клиента
 * и не должно формироваться из {@link Throwable#getMessage()}.</p>
 *
 * <p>{@code status} содержит числовой HTTP-статус. Поле
 * необязательно: {@code null} означает, что на данном шаге
 * цепочки HTTP-статус ещё не был определён.</p>
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

            @JsonProperty(value = "exceptionType", required = true)
            @JsonAlias({"exception_type", "causeCode"})
            String causeCode,

            @JsonProperty(value = "message", required = true)
            @JsonAlias("publicMessage")
            String publicMessage,

            @JsonProperty(value = "timestamp", required = true)
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

        this.httpStatus = ErrorModelValidation.nullableHttpStatus(
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
     * Возвращает публичный код причины.
     *
     * @return код вида {@code BOOK_NOT_FOUND}
     */
    @JsonProperty("exceptionType")
    public String getCauseCode() {
        return causeCode;
    }

    /**
     * Возвращает безопасное сообщение для внешнего клиента.
     */
    @JsonProperty("message")
    public String getPublicMessage() {
        return publicMessage;
    }

    @JsonProperty("timestamp")
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * @return числовой HTTP-статус или {@code null}, если статус
     * ещё не определён
     */
    @JsonProperty("status")
    public Integer getHttpStatus() {
        return httpStatus;
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