package ru.oreoman4ik.catchup.context;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.web.client.RestClientException;
import ru.oreoman4ik.catchup.annotation.ErrorContext;
import ru.oreoman4ik.catchup.client.OutgoingHttpExceptionMapper;
import ru.oreoman4ik.catchup.config.CurrentServiceName;
import ru.oreoman4ik.catchup.config.TechnicalDetailsFactory;
import ru.oreoman4ik.catchup.config.UnifiedErrorProperties;
import ru.oreoman4ik.catchup.model.BusinessException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;

import java.lang.reflect.Method;
import java.time.Instant;

@Aspect
public final class ErrorContextAspect {

    private static final int DEFAULT_STATUS = 500;

    private static final String DEFAULT_ERROR_CODE =
            "INTERNAL_ERROR";

    private final String currentService;

    private final int maxChainSize;

    private final String unknownErrorMessage;

    private final OutgoingHttpExceptionMapper
            httpExceptionMapper;

    private final TechnicalDetailsFactory
            technicalDetailsFactory;

    public ErrorContextAspect(
            CurrentServiceName currentService,
            UnifiedErrorProperties properties,
            OutgoingHttpExceptionMapper
                    httpExceptionMapper,
            TechnicalDetailsFactory
                    technicalDetailsFactory
    ) {
        if (currentService == null) {
            throw new IllegalArgumentException(
                    "currentService is required"
            );
        }

        if (properties == null) {
            throw new IllegalArgumentException(
                    "properties is required"
            );
        }

        if (httpExceptionMapper == null) {
            throw new IllegalArgumentException(
                    "httpExceptionMapper is required"
            );
        }

        if (technicalDetailsFactory == null) {
            throw new IllegalArgumentException(
                    "technicalDetailsFactory is required"
            );
        }

        this.currentService =
                currentService.value();

        this.maxChainSize =
                properties.getMaxChainSize();

        this.unknownErrorMessage =
                properties
                        .getUnknownErrorMessage();

        this.httpExceptionMapper =
                httpExceptionMapper;

        this.technicalDetailsFactory =
                technicalDetailsFactory;
    }

    /*
     * Совместимость с существующими unit-тестами.
     */
    public ErrorContextAspect(
            String currentService,
            int maxChainSize,
            OutgoingHttpExceptionMapper
                    httpExceptionMapper
    ) {
        UnifiedErrorProperties properties =
                defaultProperties(
                        maxChainSize
                );

        this.currentService =
                CurrentServiceName
                        .of(currentService)
                        .value();

        this.maxChainSize =
                properties.getMaxChainSize();

        this.unknownErrorMessage =
                properties
                        .getUnknownErrorMessage();

        this.httpExceptionMapper =
                httpExceptionMapper;

        this.technicalDetailsFactory =
                new TechnicalDetailsFactory(
                        properties
                );
    }

    @Around(
            value = "@annotation(errorContext)",
            argNames =
                    "joinPoint,errorContext"
    )
    public Object addOperationContext(
            ProceedingJoinPoint joinPoint,
            ErrorContext errorContext
    ) throws Throwable {

        try {
            return joinPoint.proceed();

        } catch (Exception cause) {

            /*
             * JVM Error намеренно не ловятся.
             *
             * OutOfMemoryError,
             * StackOverflowError,
             * LinkageError и т.п.
             * проходят дальше без оборачивания.
             */
            throw enrich(
                    cause,
                    joinPoint,
                    errorContext
            );
        }
    }

    private UnifiedErrorException enrich(
            Exception cause,
            ProceedingJoinPoint joinPoint,
            ErrorContext annotation
    ) {
        Context context =
                resolveContext(
                        joinPoint,
                        annotation
                );

        if (cause
                instanceof
                UnifiedErrorException existing) {

            String contextMessage =
                    publicMessageOrDefault(
                            annotation.message(),
                            existing.getMessage()
                    );

            return existing.addContext(
                    chainElement(
                            context,
                            existing.getStatus(),
                            existing.getErrorCode(),
                            contextMessage
                    )
            );
        }

        if (cause
                instanceof
                RestClientException httpException) {

            return httpExceptionMapper.map(
                    httpException,
                    context.service(),
                    context.component(),
                    context.operation(),
                    optionalPublicMessage(
                            annotation.message()
                    )
            );
        }

        if (cause
                instanceof
                BusinessException business) {

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
                    technicalDetailsFactory.enrich(
                            business.getDetails(),
                            business
                    ),
                    chainElement(
                            context,
                            business.getStatus(),
                            business.getErrorCode(),
                            publicMessage
                    ),
                    maxChainSize
            );
        }

        String publicMessage =
                publicMessageOrDefault(
                        annotation.message(),
                        unknownErrorMessage
                );

        return UnifiedErrorException.from(
                cause,
                Instant.now(),
                DEFAULT_STATUS,
                DEFAULT_ERROR_CODE,
                publicMessage,
                technicalDetailsFactory.enrich(
                        null,
                        cause
                ),
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
                        joinPoint
                                .getSignature();

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
                .service(
                        context.service()
                )
                .component(
                        context.component()
                )
                .operation(
                        context.operation()
                )
                .errorCode(errorCode)
                .message(message)
                .timestamp(Instant.now())
                .status(status)
                .build();
    }

    private static String
    optionalPublicMessage(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value.trim();
    }

    private static String
    publicMessageOrDefault(
            String value,
            String defaultMessage
    ) {
        String message =
                optionalPublicMessage(value);

        return message == null
                ? defaultMessage
                : message;
    }

    private static UnifiedErrorProperties
    defaultProperties(
            int maxChainSize
    ) {
        UnifiedErrorProperties properties =
                new UnifiedErrorProperties();

        properties.setMaxChainSize(
                maxChainSize
        );

        return properties;
    }

    private record Context(
            String service,
            String component,
            String operation
    ) {
    }
}