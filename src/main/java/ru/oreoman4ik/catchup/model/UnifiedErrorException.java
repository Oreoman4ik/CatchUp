package ru.oreoman4ik.catchup.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Базовое исключение библиотеки для передачи одной логической
 * структурированной ошибки между уровнями приложения.
 *
 * <p>Идентификатор, время возникновения, код ошибки, details и
 * исходная причина не меняются при добавлении контекста.</p>
 */
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

        this.errorCode = ErrorModelValidation.publicCode(
                "errorCode",
                errorCode
        );

        this.details = details;
        this.originalCause = originalCause;

        this.chain = ErrorModelValidation.required(
                "chain",
                chain
        );
    }

    /**
     * Создаёт исключение из обычной причины.
     *
     * <p>Время фиксируется в момент вызова метода.</p>
     */
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

    /**
     * Создаёт исключение с полным набором структурированных
     * данных.
     *
     * <p>Перегрузка с явным timestamp нужна, когда точное время
     * возникновения ошибки уже известно.</p>
     */
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
                instanceof UnifiedErrorException existing) {

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
                ExceptionChain.empty(maxChainSize)
        );
    }

    /**
     * Создаёт исключение и сразу добавляет первый контекст.
     */
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
        ).addContext(initialContext);
    }

    /**
     * Создаёт исключение с полными данными и первым контекстом.
     */
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
        ).addContext(initialContext);
    }

    /**
     * Восстанавливает ошибку из ответа другого сервиса.
     *
     * <p>Сохраняются errorId, timestamp, status, message,
     * errorCode, details и существующая цепочка.</p>
     */
    public static UnifiedErrorException fromResponse(
            ErrorResponse response,
            Throwable cause,
            int maxChainSize
    ) {
        ErrorModelValidation.required(
                "response",
                response
        );

        ErrorModelValidation.required(
                "cause",
                cause
        );

        // Проверяет корректность локального лимита.
        ExceptionChain.empty(maxChainSize);

        /*
         * Если удалённая цепочка уже превышает локальный лимит,
         * существующие элементы не удаляются. Дальнейшее
         * увеличение будет заблокировано.
         */
        int effectiveMaxSize = Math.max(
                maxChainSize,
                response.getChain().size()
        );

        ExceptionChain restoredChain =
                ExceptionChain.of(
                        response.getChain(),
                        effectiveMaxSize
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

    /**
     * Добавляет контекст к тому же экземпляру исключения.
     */
    public synchronized UnifiedErrorException addContext(
            ChainElement context
    ) {
        chain = chain.add(context);
        return this;
    }

    /**
     * Преобразует исключение обратно в публичный ответ.
     *
     * <p>Перед преобразованием должен быть добавлен хотя бы один
     * элемент цепочки.</p>
     */
    public ErrorResponse toResponse(String currentService) {
        if (chain.isEmpty()) {
            throw new IllegalStateException(
                    "chain must contain at least one element "
                            + "before creating ErrorResponse"
            );
        }

        return ErrorResponse.builder()
                .errorId(errorId)
                .timestamp(timestamp)
                .status(status)
                .message(getMessage())
                .errorCode(errorCode)
                .currentService(currentService)
                .chain(chain.getElements())
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

    /**
     * Возвращает исходную техническую причину ошибки.
     */
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