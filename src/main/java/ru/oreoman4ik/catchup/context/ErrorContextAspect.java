package ru.oreoman4ik.catchup.context;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import ru.oreoman4ik.catchup.annotation.ErrorContext;
import ru.oreoman4ik.catchup.client.OutgoingHttpExceptionMapper;
import ru.oreoman4ik.catchup.model.BusinessException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;

import java.lang.reflect.Method;
import java.time.Instant;

/**
 * Автоматически добавляет контекст метода, помеченного
 * ErrorContext, если выполнение метода завершилось исключением.
 */
@Aspect
@Component
public final class ErrorContextAspect {

    private static final int ABSOLUTE_MAX_CHAIN_SIZE = 100;

    private static final int DEFAULT_STATUS = 500;

    private static final String DEFAULT_ERROR_CODE =
            "INTERNAL_ERROR";

    private static final String DEFAULT_MESSAGE =
            "Внутренняя ошибка сервиса";

    private final String currentService;
    private final int maxChainSize;

    private final OutgoingHttpExceptionMapper
            httpExceptionMapper;

    public ErrorContextAspect(
            @Value("${spring.application.name:application}")
            String currentService,

            @Value("${catchup.errors.max-chain-size:10}")
            int maxChainSize,

            OutgoingHttpExceptionMapper httpExceptionMapper
    ) {
        this.currentService =
                validateServiceName(currentService);

        this.maxChainSize =
                validateMaxChainSize(maxChainSize);

        this.httpExceptionMapper =
                httpExceptionMapper;
    }

    @Around(
            value = "@annotation(errorContext)",
            argNames = "joinPoint,errorContext"
    )
    public Object addOperationContext(
            ProceedingJoinPoint joinPoint,
            ErrorContext errorContext
    ) throws Throwable {

        try {
            /*
             * Успешный метод выполняется совершенно обычно.
             */
            return joinPoint.proceed();

        } catch (Throwable cause) {
            throw enrich(
                    cause,
                    joinPoint,
                    errorContext
            );
        }
    }

    private UnifiedErrorException enrich(
            Throwable cause,
            ProceedingJoinPoint joinPoint,
            ErrorContext annotation
    ) {
        Context context = resolveContext(
                joinPoint,
                annotation
        );

        /*
         * Ошибка уже структурирована.
         *
         * Не создаём новый объект ошибки:
         * сохраняются errorId, timestamp, исходная причина,
         * details и вся существующая цепочка.
         */
        if (cause
                instanceof UnifiedErrorException existing) {

            String contextMessage =
                    publicMessageOrDefault(
                            annotation.message(),
                            existing.getMessage()
                    );

            existing.addContext(
                    chainElement(
                            context,
                            existing.getStatus(),
                            existing.getErrorCode(),
                            contextMessage
                    )
            );

            return existing;
        }

        /*
         * Ошибку исходящего HTTP-вызова передаём
         * специализированному mapper из задачи 6.
         */
        if (cause instanceof RestClientException) {
            return httpExceptionMapper.map(
                    cause,
                    context.service(),
                    context.component(),
                    context.operation(),
                    optionalPublicMessage(
                            annotation.message()
                    )
            );
        }

        /*
         * Бизнес-ошибка сохраняет status, errorCode
         * и details.
         */
        if (cause instanceof BusinessException business) {

            String publicMessage =
                    publicMessageOrDefault(
                            annotation.message(),
                            business.getMessage()
                    );

            return UnifiedErrorException.from(
                    business,
                    Instant.now(),
                    business.getStatus(),
                    business.getErrorCode(),
                    publicMessage,
                    business.getDetails(),
                    chainElement(
                            context,
                            business.getStatus(),
                            business.getErrorCode(),
                            publicMessage
                    ),
                    maxChainSize
            );
        }

        /*
         * Обычное неизвестное исключение превращается
         * в безопасную структурированную 500 ошибку.
         *
         * cause.getMessage() никогда не становится
         * публичным сообщением.
         */
        String publicMessage =
                publicMessageOrDefault(
                        annotation.message(),
                        DEFAULT_MESSAGE
                );

        return UnifiedErrorException.from(
                cause,
                Instant.now(),
                DEFAULT_STATUS,
                DEFAULT_ERROR_CODE,
                publicMessage,
                null,
                chainElement(
                        context,
                        DEFAULT_STATUS,
                        DEFAULT_ERROR_CODE,
                        publicMessage
                ),
                maxChainSize
        );
    }

    private Context resolveContext(
            ProceedingJoinPoint joinPoint,
            ErrorContext annotation
    ) {
        MethodSignature signature =
                (MethodSignature)
                        joinPoint.getSignature();

        Method method =
                AopUtils.getMostSpecificMethod(
                        signature.getMethod(),
                        joinPoint
                                .getTarget()
                                .getClass()
                );

        String service =
                annotation.service().isBlank()
                        ? currentService
                        : annotation
                        .service()
                        .trim();

        String operation =
                annotation.operation().isBlank()
                        ? method.getName()
                        : annotation
                        .operation()
                        .trim();

        Class<?> targetClass =
                AopUtils.getTargetClass(
                        joinPoint.getTarget()
                );

        return new Context(
                service,
                targetClass.getSimpleName(),
                operation
        );
    }

    private static ChainElement chainElement(
            Context context,
            int status,
            String errorCode,
            String message
    ) {
        return ChainElement.builder()
                .service(context.service())
                .component(context.component())
                .operation(context.operation())
                .errorCode(errorCode)
                .message(message)
                .timestamp(Instant.now())
                .status(status)
                .build();
    }

    private static String optionalPublicMessage(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String publicMessageOrDefault(
            String value,
            String defaultMessage
    ) {
        String message =
                optionalPublicMessage(value);

        return message == null
                ? defaultMessage
                : message;
    }

    private static String validateServiceName(
            String value
    ) {
        String normalized =
                value == null || value.isBlank()
                        ? "application"
                        : value.trim();

        if (normalized.length() > 120) {
            throw new IllegalArgumentException(
                    "spring.application.name must not "
                            + "exceed 120 characters"
            );
        }

        if (normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\t') >= 0) {

            throw new IllegalArgumentException(
                    "spring.application.name must not "
                            + "contain control characters"
            );
        }

        return normalized;
    }

    private static int validateMaxChainSize(
            int value
    ) {
        if (value < 1
                || value > ABSOLUTE_MAX_CHAIN_SIZE) {

            throw new IllegalArgumentException(
                    "catchup.errors.max-chain-size "
                            + "must be from 1 to "
                            + ABSOLUTE_MAX_CHAIN_SIZE
            );
        }

        return value;
    }

    private record Context(
            String service,
            String component,
            String operation
    ) {
    }
}