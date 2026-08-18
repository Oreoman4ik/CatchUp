package ru.oreoman4ik.catchup.model;

import ru.oreoman4ik.catchup.support.ErrorDataLimiter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UnifiedErrorException
        extends RuntimeException {

    private final UUID errorId;

    private final Instant timestamp;

    private final int status;

    private final String errorCode;

    private final ErrorDetails details;

    private final Throwable originalCause;

    private final AtomicBoolean messageTruncated;

    /**
     * Нужен для защиты от повторного логирования
     * одного экземпляра ошибки.
     */
    private final AtomicBoolean logged =
            new AtomicBoolean(false);

    private volatile ExceptionChain chain;

    private UnifiedErrorException(
            UUID errorId,
            Instant timestamp,
            int status,
            String errorCode,
            ErrorDataLimiter.LimitedText message,
            ErrorDetails details,
            Throwable originalCause,
            ExceptionChain chain
    ) {
        super(
                message.value(),
                ErrorModelValidation.required(
                        "originalCause",
                        originalCause
                )
        );

        this.errorId =
                ErrorModelValidation.required(
                        "errorId",
                        errorId
                );

        this.timestamp =
                ErrorModelValidation.required(
                        "timestamp",
                        timestamp
                );

        this.status =
                ErrorModelValidation.httpStatus(
                        "status",
                        status
                );

        this.errorCode =
                ErrorModelValidation.publicCode(
                        "errorCode",
                        errorCode
                );

        this.details = details;

        this.originalCause =
                originalCause;

        this.chain =
                ErrorModelValidation.required(
                        "chain",
                        chain
                );

        this.messageTruncated =
                new AtomicBoolean(
                        message.truncated()
                );
    }

    public static UnifiedErrorException from(
            Throwable cause,
            int status,
            String errorCode,
            String message,
            int maxChainSize
    ) {
        return from(
                cause,
                Instant.now(),
                status,
                errorCode,
                message,
                null,
                maxChainSize
        );
    }

    public static UnifiedErrorException from(
            Throwable cause,
            Instant timestamp,
            int status,
            String errorCode,
            String message,
            ErrorDetails details,
            int maxChainSize
    ) {
        ErrorModelValidation.required(
                "cause",
                cause
        );

        if (cause
                instanceof
                UnifiedErrorException existing) {

            return existing;
        }

        ErrorDataLimiter.LimitedText
                limitedMessage =
                ErrorDataLimiter
                        .publicMessage(
                                message
                        );

        return new UnifiedErrorException(
                UUID.randomUUID(),
                timestamp,
                status,
                errorCode,
                limitedMessage,
                details,
                cause,
                ExceptionChain.empty(
                        maxChainSize
                )
        );
    }

    public static UnifiedErrorException from(
            Throwable cause,
            int status,
            String errorCode,
            String message,
            ChainElement initialContext,
            int maxChainSize
    ) {
        return from(
                cause,
                status,
                errorCode,
                message,
                maxChainSize
        ).addContext(
                initialContext
        );
    }

    public static UnifiedErrorException from(
            Throwable cause,
            Instant timestamp,
            int status,
            String errorCode,
            String message,
            ErrorDetails details,
            ChainElement initialContext,
            int maxChainSize
    ) {
        return from(
                cause,
                timestamp,
                status,
                errorCode,
                message,
                details,
                maxChainSize
        ).addContext(
                initialContext
        );
    }

    public static UnifiedErrorException fromResponse(
            ErrorResponse response,
            Throwable cause,
            int maxChainSize
    ) {
        return fromResponse(
                response,
                cause,
                maxChainSize,
                0
        );
    }

    /**
     * Восстанавливает ошибку другого сервиса,
     * сохраняя строгий локальный maxChainSize.
     */
    public static UnifiedErrorException fromResponse(
            ErrorResponse response,
            Throwable cause,
            int maxChainSize,
            int reservedChainSlots
    ) {
        ErrorModelValidation.required(
                "response",
                response
        );

        ErrorModelValidation.required(
                "cause",
                cause
        );

        /*
         * Одновременно валидируем maxChainSize.
         */
        ExceptionChain.empty(
                maxChainSize
        );

        if (reservedChainSlots < 0
                || reservedChainSlots
                > maxChainSize) {

            throw new IllegalArgumentException(
                    "reservedChainSlots must be "
                            + "from 0 to "
                            + maxChainSize
            );
        }

        int maxRemoteElements =
                maxChainSize
                        - reservedChainSlots;

        List<ChainElement> remoteChain =
                response.getChain();

        boolean chainTruncated =
                remoteChain.size()
                        > maxRemoteElements;

        List<ChainElement> retainedChain =
                remoteChain.size()
                        <= maxRemoteElements
                        ? remoteChain
                        : List.copyOf(
                        remoteChain.subList(
                                0,
                                maxRemoteElements
                        )
                );

        ExceptionChain restoredChain =
                ExceptionChain.restored(
                        retainedChain,
                        maxChainSize,
                        chainTruncated
                );

        ErrorDataLimiter.LimitedText
                limitedMessage =
                ErrorDataLimiter
                        .publicMessage(
                                response.getMessage()
                        );

        return new UnifiedErrorException(
                response.getErrorId(),
                response.getTimestamp(),
                response.getStatus(),
                response.getErrorCode(),
                limitedMessage,
                response.getDetails(),
                cause,
                restoredChain
        );
    }

    public synchronized
    UnifiedErrorException addContext(
            ChainElement context
    ) {
        chain = chain.add(context);

        return this;
    }

    public ErrorResponse toResponse(
            String currentService
    ) {
        if (chain.isEmpty()) {
            throw new IllegalStateException(
                    "chain must contain at least "
                            + "one element before creating "
                            + "ErrorResponse"
            );
        }

        TruncationInfo runtimeTruncation =
                new TruncationInfo(
                        chain.isTruncated(),
                        messageTruncated.get(),
                        false,
                        false
                );

        ErrorDetails responseDetails =
                ErrorDetails.mergeTruncation(
                        details,
                        runtimeTruncation
                );

        return ErrorResponse.builder()
                .errorId(errorId)
                .timestamp(timestamp)
                .status(status)
                .message(getMessage())
                .errorCode(errorCode)
                .currentService(
                        currentService
                )
                .chain(
                        chain.getElements()
                )
                .details(
                        responseDetails
                )
                .build();
    }

    /**
     * @return true только один раз для конкретного
     * экземпляра UnifiedErrorException.
     */
    public boolean tryMarkLogged() {
        return logged.compareAndSet(
                false,
                true
        );
    }

    public UUID getErrorId() {
        return errorId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public ErrorDetails getDetails() {
        return details;
    }

    public Throwable getOriginalCause() {
        return originalCause;
    }

    public ExceptionChain getChain() {
        return chain;
    }

    public List<ChainElement>
    getChainElements() {
        return chain.getElements();
    }

    public boolean isChainLimitReached() {
        return chain.isLimitReached();
    }

    public boolean isChainTruncated() {
        return chain.isTruncated();
    }

    public boolean isMessageTruncated() {
        return messageTruncated.get();
    }
}