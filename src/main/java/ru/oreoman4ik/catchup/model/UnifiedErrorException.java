package ru.oreoman4ik.catchup.model;

import java.util.List;
import java.util.UUID;

/**
 * Базовое исключение библиотеки для передачи одной логической
 * структурированной ошибки между уровнями приложения.
 *
 * <p>Идентификатор ошибки и исходная причина создаются один раз
 * и не меняются при добавлении нового контекста.</p>
 */
public final class UnifiedErrorException
        extends RuntimeException {

    private final UUID errorId;
    private final int httpStatus;
    private final String publicMessage;
    private final Throwable originalCause;

    private volatile ExceptionChain chain;

    private UnifiedErrorException(
            UUID errorId,
            int httpStatus,
            String publicMessage,
            Throwable originalCause,
            ExceptionChain chain
    ) {
        super(
                ErrorModelValidation.publicMessage(
                        "publicMessage",
                        publicMessage
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

        this.httpStatus =
                ErrorModelValidation.httpStatus(
                        "httpStatus",
                        httpStatus
                );

        this.publicMessage = getMessage();
        this.originalCause = originalCause;

        this.chain = ErrorModelValidation.required(
                "chain",
                chain
        );
    }

    /**
     * Создаёт структурированную ошибку из обычного исключения.
     *
     * <p>Цепочка изначально пуста и может быть дополнена через
     * {@link #addContext(ChainElement)}.</p>
     *
     * <p>Если переданный объект уже является
     * {@code UnifiedErrorException}, возвращается тот же экземпляр.
     * Новый идентификатор ошибки не создаётся.</p>
     */
    public static UnifiedErrorException from(
            Throwable cause,
            int httpStatus,
            String publicMessage,
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
                httpStatus,
                publicMessage,
                cause,
                ExceptionChain.empty(maxChainSize)
        );
    }

    /**
     * Создаёт ошибку из обычного исключения и сразу добавляет
     * первый контекст.
     */
    public static UnifiedErrorException from(
            Throwable cause,
            int httpStatus,
            String publicMessage,
            ChainElement initialContext,
            int maxChainSize
    ) {
        return from(
                cause,
                httpStatus,
                publicMessage,
                maxChainSize
        ).addContext(initialContext);
    }

    /**
     * Восстанавливает ошибку из ответа другого сервиса.
     *
     * <p>Сохраняются:</p>
     *
     * <ul>
     *     <li>исходный errorId;</li>
     *     <li>HTTP-статус;</li>
     *     <li>публичное сообщение;</li>
     *     <li>вся существующая цепочка.</li>
     * </ul>
     *
     * <p>В качестве originalCause передаётся локальное исключение,
     * возникшее при обработке удалённого HTTP-ответа.</p>
     *
     * <p>Если удалённая цепочка уже длиннее локального лимита,
     * существующие элементы не удаляются, но дальнейшее увеличение
     * блокируется.</p>
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

        // Отдельно проверяем корректность локальной настройки.
        ExceptionChain.empty(maxChainSize);

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
                response.getHttpStatus(),
                response.getPublicMessage(),
                cause,
                restoredChain
        );
    }

    /**
     * Добавляет новый контекст к этой же логической ошибке.
     *
     * <p>Метод возвращает текущий экземпляр. errorId и
     * originalCause остаются неизменными.</p>
     */
    public synchronized UnifiedErrorException addContext(
            ChainElement context
    ) {
        chain = chain.add(context);
        return this;
    }

    public UUID getErrorId() {
        return errorId;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getPublicMessage() {
        return publicMessage;
    }

    /**
     * Возвращает самое первое исключение, из которого была создана
     * структурированная ошибка.
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