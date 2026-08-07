package ru.oreoman4ik.catchup.model;

/**
 * Контролируемая бизнес-ошибка приложения.
 *
 * <p>Сообщение должно быть безопасным публичным текстом.
 * Нельзя передавать в него {@code exception.getMessage()}.</p>
 */
public final class BusinessException
        extends RuntimeException {

    private final int status;
    private final String errorCode;
    private final ErrorDetails details;

    public BusinessException(
            int status,
            String errorCode,
            String message
    ) {
        this(
                status,
                errorCode,
                message,
                null,
                null
        );
    }

    public BusinessException(
            int status,
            String errorCode,
            String message,
            ErrorDetails details
    ) {
        this(
                status,
                errorCode,
                message,
                details,
                null
        );
    }

    public BusinessException(
            int status,
            String errorCode,
            String message,
            ErrorDetails details,
            Throwable cause
    ) {
        super(
                ErrorModelValidation.publicMessage(
                        "message",
                        message
                ),
                cause
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
}