package ru.oreoman4ik.catchup.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Неизменяемый публичный ответ об ошибке.
 *
 * <p>{@code errorType} в JSON содержит стабильный публичный код,
 * например {@code COMPONENT_NOT_FOUND}, а не имя Java-класса
 * исключения.</p>
 *
 * <p>{@code message} должен формироваться из контролируемого
 * каталога публичных сообщений. Модель не очищает содержимое
 * {@code exception.getMessage()} и такое значение передавать в
 * ответ запрещено.</p>
 *
 * <p>Все поля, кроме {@code details}, обязательны. Цепочка должна
 * содержать хотя бы один элемент.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorResponse {

    private static final int MAX_CHAIN_SIZE = 100;

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
            @JsonFormat(
                    shape = JsonFormat.Shape.STRING,
                    pattern =
                            ErrorModelValidation.TIMESTAMP_PATTERN,
                    timezone =
                            ErrorModelValidation.UTC_TIME_ZONE
            )
            Instant timestamp,

            @JsonProperty(value = "status", required = true)
            @JsonAlias("httpStatus")
            int httpStatus,

            @JsonProperty(value = "message", required = true)
            @JsonAlias("publicMessage")
            String publicMessage,

            @JsonProperty(
                    value = "errorType",
                    required = true
            )
            @JsonAlias({"type_exception", "errorCode"})
            String errorCode,

            @JsonProperty(
                    value = "currentService",
                    required = true
            )
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

        this.publicMessage =
                ErrorModelValidation.publicMessage(
                        "message",
                        publicMessage
                );

        this.errorCode = ErrorModelValidation.publicCode(
                "errorType",
                errorCode
        );

        this.currentService =
                ErrorModelValidation.requiredText(
                        "currentService",
                        currentService,
                        120
                );

        this.chain =
                ErrorModelValidation.immutableNonEmptyList(
                        "chain",
                        chain,
                        MAX_CHAIN_SIZE
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

    /**
     * @return время возникновения исходной ошибки в UTC; JSON
     * всегда использует формат
     * {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}
     */
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
     * @return итоговый числовой HTTP-статус ошибки от 400 до 599
     */
    @JsonProperty("status")
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * @return контролируемое сообщение для внешнего клиента
     */
    @JsonProperty("message")
    public String getPublicMessage() {
        return publicMessage;
    }

    /**
     * @return публичный код вида {@code COMPONENT_NOT_FOUND}, но
     * не имя Java-класса исключения
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
     * @return неизменяемая непустая цепочка, упорядоченная от
     * места возникновения ошибки к текущему сервису
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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ErrorResponse that)) {
            return false;
        }

        return httpStatus == that.httpStatus
                && errorId.equals(that.errorId)
                && timestamp.equals(that.timestamp)
                && publicMessage.equals(that.publicMessage)
                && errorCode.equals(that.errorCode)
                && currentService.equals(that.currentService)
                && chain.equals(that.chain)
                && Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                errorId,
                timestamp,
                httpStatus,
                publicMessage,
                errorCode,
                currentService,
                chain,
                details
        );
    }

    @Override
    public String toString() {
        return "ErrorResponse{"
                + "errorId=" + errorId
                + ", timestamp=" + timestamp
                + ", httpStatus=" + httpStatus
                + ", publicMessage='" + publicMessage + '\''
                + ", errorCode='" + errorCode + '\''
                + ", currentService='" + currentService + '\''
                + ", chain=" + chain
                + ", details=" + details
                + '}';
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