package ru.oreoman4ik.catchup.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UnifiedErrorException
        extends RuntimeException {

    private final UUID errorId;

    private final Instant timestamp;

    private final int status;

    private final String errorCode;

    private final ErrorDetails details;

    private final Throwable originalCause;

    private volatile ExceptionChain chain;

    private UnifiedErrorException(
            UUID errorId,
            Instant timestamp,
            int status,
            String errorCode,
            String message,
            ErrorDetails details,
            Throwable originalCause,
            ExceptionChain chain
    ) {
        super(
                ErrorModelValidation.publicMessage(
                        "message",
                        message
                ),
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

        this.originalCause = originalCause;

        this.chain =
                ErrorModelValidation.required(
                        "chain",
                        chain
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

        return new UnifiedErrorException(
                UUID.randomUUID(),
                timestamp,
                status,
                errorCode,
                message,
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

    /**
     * Восстанавливает ошибку из удалённого ErrorResponse.
     *
     * <p>Цепочка никогда не превышает maxChainSize.
     * Если удалённая цепочка длиннее локального лимита,
     * сохраняются первые элементы — то есть контекст,
     * ближайший к месту возникновения ошибки.</p>
     */
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
     * Восстанавливает удалённую ошибку и резервирует
     * указанное количество мест в chain для последующих
     * локальных контекстов.
     *
     * <p>Например, HTTP mapper использует
     * reservedChainSlots = 1, чтобы текущий сервис
     * гарантированно можно было добавить последним,
     * не превышая maxChainSize.</p>
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
         * Одновременно валидирует maxChainSize.
         */
        ExceptionChain.empty(
                maxChainSize
        );

        if (reservedChainSlots < 0
                || reservedChainSlots > maxChainSize) {

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

        List<ChainElement> retainedChain;

        if (remoteChain.size()
                <= maxRemoteElements) {

            retainedChain = remoteChain;

        } else {

            retainedChain =
                    List.copyOf(
                            remoteChain.subList(
                                    0,
                                    maxRemoteElements
                            )
                    );
        }

        ExceptionChain restoredChain =
                ExceptionChain.of(
                        retainedChain,
                        maxChainSize
                );

        return new UnifiedErrorException(
                response.getErrorId(),
                response.getTimestamp(),
                response.getStatus(),
                response.getErrorCode(),
                response.getMessage(),
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
                    "chain must contain at least one "
                            + "element before creating "
                            + "ErrorResponse"
            );
        }

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
                .details(details)
                .build();
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

    public List<ChainElement> getChainElements() {
        return chain.getElements();
    }

    public boolean isChainLimitReached() {
        return chain.isLimitReached();
    }
}