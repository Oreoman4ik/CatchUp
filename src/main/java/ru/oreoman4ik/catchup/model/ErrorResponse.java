package ru.oreoman4ik.catchup.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Неизменяемый публичный ответ об ошибке.
 *
 * <p>{@code errorType} в JSON является стабильным публичным
 * машинным кодом, например {@code COMPONENT_NOT_FOUND}. Полные имена
 * Java-исключений в это поле передавать запрещено.</p>
 *
 * <p>{@code status} всегда содержит числовой итоговый
 * HTTP-статус. Все поля, кроме {@code details}, обязательны.
 * {@code chain} должна содержать хотя бы один элемент.</p>
 *
 * <p>Необязательное поле {@code details} полностью отсутствует
 * в JSON, если безопасных дополнительных данных нет.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorResponse {

    private final UUID errorId;
    private final Instant timestamp;
    private final int httpStatus;
    private final String publicMessage;
    private final String errorCode;
    private final String currentService;
    private final List<ChainElement> chain;
    private final ErrorDetails details;

    @JsonCreator
    private ErrorResponse(
            @JsonProperty(value = "errorId", required = true)
            @JsonAlias("error_id")
            UUID errorId,

            @JsonProperty(value = "timestamp", required = true)
            Instant timestamp,

            @JsonProperty(value = "status", required = true)
            @JsonAlias("httpStatus")
            int httpStatus,

            @JsonProperty(value = "message", required = true)
            @JsonAlias("publicMessage")
            String publicMessage,

            @JsonProperty(value = "errorType", required = true)
            @JsonAlias({"type_exception", "errorCode"})
            String errorCode,

            @JsonProperty(value = "currentService", required = true)
            @JsonAlias({"current_service", "service"})
            String currentService,

            @JsonProperty(value = "chain", required = true)
            List<ChainElement> chain,

            @JsonProperty("details")
            ErrorDetails details
    ) {
        this.errorId = ErrorModelValidation.required(
                "errorId",
                errorId
        );

        this.timestamp = ErrorModelValidation.required(
                "timestamp",
                timestamp
        );

        this.httpStatus = ErrorModelValidation.httpStatus(
                "status",
                httpStatus
        );

        this.publicMessage = ErrorModelValidation.publicMessage(
                "message",
                publicMessage
        );

        this.errorCode = ErrorModelValidation.publicCode(
                "errorType",
                errorCode
        );

        this.currentService = ErrorModelValidation.requiredText(
                "currentService",
                currentService,
                120
        );

        this.chain = ErrorModelValidation.immutableNonEmptyList(
                "chain",
                chain
        );

        this.details = details;
    }

    private ErrorResponse(Builder builder) {
        this(
                builder.errorId,
                builder.timestamp,
                builder.httpStatus,
                builder.publicMessage,
                builder.errorCode,
                builder.currentService,
                builder.chain,
                builder.details
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty("errorId")
    public UUID getErrorId() {
        return errorId;
    }

    @JsonProperty("timestamp")
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * @return числовой итоговый HTTP-статус
     */
    @JsonProperty("status")
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * @return безопасное сообщение для внешнего клиента
     */
    @JsonProperty("message")
    public String getPublicMessage() {
        return publicMessage;
    }

    /**
     * Возвращает публичный код ошибки.
     *
     * @return код вида {@code COMPONENT_NOT_FOUND}, но не имя
     * Java-класса исключения
     */
    @JsonProperty("errorType")
    public String getErrorCode() {
        return errorCode;
    }

    @JsonProperty("currentService")
    public String getCurrentService() {
        return currentService;
    }

    /**
     * Возвращает неизменяемую непустую цепочку ошибки.
     */
    @JsonProperty("chain")
    public List<ChainElement> getChain() {
        return chain;
    }

    /**
     * @return типизированные публичные данные или {@code null},
     * если дополнительных сведений нет
     */
    @JsonProperty("details")
    public ErrorDetails getDetails() {
        return details;
    }

    public static final class Builder {

        private UUID errorId;
        private Instant timestamp;
        private int httpStatus;
        private String publicMessage;
        private String errorCode;
        private String currentService;
        private List<ChainElement> chain;
        private ErrorDetails details;

        private Builder() {
        }

        public Builder errorId(UUID errorId) {
            this.errorId = errorId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder httpStatus(int httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        public Builder publicMessage(String publicMessage) {
            this.publicMessage = publicMessage;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder currentService(String currentService) {
            this.currentService = currentService;
            return this;
        }

        public Builder chain(List<ChainElement> chain) {
            this.chain = chain;
            return this;
        }

        public Builder details(ErrorDetails details) {
            this.details = details;
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(this);
        }
    }
}
