package ru.oreoman4ik.catchup.model;

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
 * <p>Java- и JSON-контракт используют одинаковые названия:
 * {@code status}, {@code message} и {@code errorCode}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorResponse {

    private static final int MAX_CHAIN_SIZE = 100;

    private final UUID errorId;
    private final Instant timestamp;
    private final int status;
    private final String message;
    private final String errorCode;
    private final String currentService;
    private final List<ChainElement> chain;
    private final ErrorDetails details;

    @JsonCreator
    private ErrorResponse(
            @JsonProperty(value = "errorId", required = true)
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
            int status,

            @JsonProperty(value = "message", required = true)
            String message,

            @JsonProperty(value = "errorCode", required = true)
            String errorCode,

            @JsonProperty(
                    value = "currentService",
                    required = true
            )
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

        this.status = ErrorModelValidation.httpStatus(
                "status",
                status
        );

        this.message = ErrorModelValidation.publicMessage(
                "message",
                message
        );

        this.errorCode = ErrorModelValidation.publicCode(
                "errorCode",
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
                builder.status,
                builder.message,
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
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = ErrorModelValidation.TIMESTAMP_PATTERN,
            timezone = ErrorModelValidation.UTC_TIME_ZONE
    )
    public Instant getTimestamp() {
        return timestamp;
    }

    @JsonProperty("status")
    public int getStatus() {
        return status;
    }

    @JsonProperty("message")
    public String getMessage() {
        return message;
    }

    @JsonProperty("errorCode")
    public String getErrorCode() {
        return errorCode;
    }

    @JsonProperty("currentService")
    public String getCurrentService() {
        return currentService;
    }

    @JsonProperty("chain")
    public List<ChainElement> getChain() {
        return chain;
    }

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

        return status == that.status
                && errorId.equals(that.errorId)
                && timestamp.equals(that.timestamp)
                && message.equals(that.message)
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
                status,
                message,
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
                + ", status=" + status
                + ", message='" + message + '\''
                + ", errorCode='" + errorCode + '\''
                + ", currentService='" + currentService + '\''
                + ", chain=" + chain
                + ", details=" + details
                + '}';
    }

    public static final class Builder {

        private UUID errorId;
        private Instant timestamp;
        private int status;
        private String message;
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

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
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