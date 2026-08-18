package ru.oreoman4ik.catchup.client;

import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;
import ru.oreoman4ik.catchup.config.CurrentServiceName;
import ru.oreoman4ik.catchup.config.TechnicalDetailsFactory;
import ru.oreoman4ik.catchup.config.UnifiedErrorProperties;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.TruncationInfo;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;
import tools.jackson.databind.json.JsonMapper;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

public final class OutgoingHttpExceptionMapper {

    private final String currentService;

    private final int maxChainSize;

    private final RemoteErrorResponseDecoder
            remoteErrorResponseDecoder;

    private final TechnicalDetailsFactory
            technicalDetailsFactory;

    public OutgoingHttpExceptionMapper(
            CurrentServiceName currentService,
            UnifiedErrorProperties properties,
            RemoteErrorResponseDecoder
                    remoteErrorResponseDecoder,
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

        if (remoteErrorResponseDecoder == null) {
            throw new IllegalArgumentException(
                    "remoteErrorResponseDecoder "
                            + "is required"
            );
        }

        if (technicalDetailsFactory == null) {
            throw new IllegalArgumentException(
                    "technicalDetailsFactory "
                            + "is required"
            );
        }

        this.currentService =
                currentService.value();

        this.maxChainSize =
                properties.getMaxChainSize();

        this.remoteErrorResponseDecoder =
                remoteErrorResponseDecoder;

        this.technicalDetailsFactory =
                technicalDetailsFactory;
    }

    /**
     * Совместимость со старыми unit tests.
     */
    public OutgoingHttpExceptionMapper(
            String currentService,
            int maxChainSize
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

        this.remoteErrorResponseDecoder =
                new RemoteErrorResponseDecoder(
                        JsonMapper
                                .builder()
                                .build(),
                        properties
                );

        this.technicalDetailsFactory =
                new TechnicalDetailsFactory(
                        properties
                );
    }

    public UnifiedErrorException map(
            Exception cause,
            String component,
            String operation
    ) {
        return map(
                cause,
                currentService,
                component,
                operation,
                null
        );
    }

    public UnifiedErrorException map(
            Exception cause,
            String service,
            String component,
            String operation,
            String publicMessage
    ) {
        if (cause == null) {
            throw new IllegalArgumentException(
                    "cause is required"
            );
        }

        if (cause
                instanceof
                UnifiedErrorException existing) {

            return existing.addContext(
                    context(
                            service,
                            component,
                            operation,
                            existing.getStatus(),
                            existing.getErrorCode(),
                            publicMessageOrDefault(
                                    publicMessage,
                                    existing.getMessage()
                            )
                    )
            );
        }

        RemoteErrorResponseDecoder.DecodeResult
                decodeResult = null;

        /*
         * Сначала пытаемся восстановить
         * структурированную ошибку другого сервиса.
         */
        if (cause
                instanceof
                RestClientResponseException
                        responseException) {

            decodeResult =
                    remoteErrorResponseDecoder
                            .decodeWithMetadata(
                                    responseException
                            );

            if (decodeResult
                    .response()
                    .isPresent()) {

                return restore(
                        decodeResult
                                .response()
                                .get(),
                        responseException,
                        service,
                        component,
                        operation,
                        publicMessage
                );
            }
        }

        Mapping mapping =
                classify(cause);

        String responseMessage =
                publicMessageOrDefault(
                        publicMessage,
                        mapping.message()
                );

        ErrorDetails details =
                technicalDetailsFactory.enrich(
                        null,
                        cause
                );

        /*
         * Remote body превышает допустимый
         * объём — фиксируем это в response metadata.
         */
        if (decodeResult != null
                && decodeResult
                .remoteBodyTruncated()) {

            details =
                    ErrorDetails.mergeTruncation(
                            details,
                            new TruncationInfo(
                                    false,
                                    false,
                                    false,
                                    true
                            )
                    );
        }

        UnifiedErrorException unified =
                UnifiedErrorException.from(
                        cause,
                        Instant.now(),
                        mapping.status(),
                        mapping.errorCode(),
                        responseMessage,
                        details,
                        maxChainSize
                );

        /*
         * Используем уже нормализованное сообщение
         * из UnifiedErrorException.
         */
        unified.addContext(
                context(
                        service,
                        component,
                        operation,
                        mapping.status(),
                        mapping.errorCode(),
                        unified.getMessage()
                )
        );

        return unified;
    }

    public UnifiedErrorException restore(
            ErrorResponse response,
            Exception cause,
            String component,
            String operation
    ) {
        return restore(
                response,
                cause,
                currentService,
                component,
                operation,
                null
        );
    }

    public UnifiedErrorException restore(
            ErrorResponse response,
            Exception cause,
            String service,
            String component,
            String operation,
            String publicMessage
    ) {
        if (response == null) {
            throw new IllegalArgumentException(
                    "response is required"
            );
        }

        if (cause == null) {
            throw new IllegalArgumentException(
                    "cause is required"
            );
        }

        /*
         * Remote technical details не проксируются.
         *
         * При включённых local technical details
         * используются данные локального
         * HTTP exception.
         */
        ErrorDetails details =
                technicalDetailsFactory.enrich(
                        response.getDetails(),
                        cause
                );

        ErrorResponse sanitizedResponse =
                ErrorResponse.builder()
                        .errorId(
                                response.getErrorId()
                        )
                        .timestamp(
                                response.getTimestamp()
                        )
                        .status(
                                response.getStatus()
                        )
                        .message(
                                response.getMessage()
                        )
                        .errorCode(
                                response.getErrorCode()
                        )
                        .currentService(
                                response
                                        .getCurrentService()
                        )
                        .chain(
                                response.getChain()
                        )
                        .details(details)
                        .build();

        /*
         * Одно место резервируется
         * под текущий сервис.
         */
        UnifiedErrorException restored =
                UnifiedErrorException
                        .fromResponse(
                                sanitizedResponse,
                                cause,
                                maxChainSize,
                                1
                        );

        Mapping callerMapping =
                mappingForStatus(
                        response.getStatus()
                );

        return restored.addContext(
                context(
                        service,
                        component,
                        operation,
                        response.getStatus(),
                        callerMapping.errorCode(),
                        publicMessageOrDefault(
                                publicMessage,
                                callerMapping.message()
                        )
                )
        );
    }

    private static Mapping classify(
            Exception cause
    ) {
        if (cause
                instanceof
                RestClientResponseException response) {

            return mappingForStatus(
                    response
                            .getStatusCode()
                            .value()
            );
        }

        if (hasCause(
                cause,
                SocketTimeoutException.class
        )
                || hasCause(
                cause,
                HttpTimeoutException.class
        )
                || hasCause(
                cause,
                TimeoutException.class
        )) {

            return new Mapping(
                    504,
                    "REMOTE_TIMEOUT",
                    "Истекло время ожидания ответа "
                            + "удалённого сервиса"
            );
        }

        if (hasCause(
                cause,
                ConnectException.class
        )
                || hasCause(
                cause,
                UnknownHostException.class
        )
                || hasCause(
                cause,
                NoRouteToHostException.class
        )) {

            return new Mapping(
                    503,
                    "REMOTE_UNAVAILABLE",
                    "Удалённый сервис недоступен"
            );
        }

        if (hasCause(
                cause,
                HttpMessageNotWritableException.class
        )) {

            return new Mapping(
                    500,
                    "OUTGOING_REQUEST_BODY_ERROR",
                    "Не удалось сформировать "
                            + "исходящий запрос"
            );
        }

        if (cause
                instanceof
                UnknownContentTypeException
                || hasCause(
                cause,
                HttpMessageNotReadableException.class
        )) {

            return new Mapping(
                    502,
                    "REMOTE_BODY_CONVERSION_ERROR",
                    "Не удалось преобразовать ответ "
                            + "удалённого сервиса"
            );
        }

        if (hasCause(
                cause,
                HttpMessageConversionException.class
        )) {

            return new Mapping(
                    500,
                    "OUTGOING_HTTP_CONVERSION_ERROR",
                    "Не удалось преобразовать данные "
                            + "исходящего HTTP-вызова"
            );
        }

        if (hasCause(
                cause,
                EOFException.class
        )) {

            return new Mapping(
                    502,
                    "REMOTE_RESPONSE_READ_ERROR",
                    "Не удалось прочитать ответ "
                            + "удалённого сервиса"
            );
        }

        if (cause
                instanceof
                ResourceAccessException
                || hasCause(
                cause,
                IOException.class
        )) {

            return new Mapping(
                    502,
                    "REMOTE_NETWORK_ERROR",
                    "Ошибка сети при обращении "
                            + "к удалённому сервису"
            );
        }

        if (cause
                instanceof RestClientException) {

            return new Mapping(
                    502,
                    "OUTGOING_HTTP_ERROR",
                    "Не удалось выполнить "
                            + "исходящий HTTP-запрос"
            );
        }

        return new Mapping(
                502,
                "OUTGOING_HTTP_ERROR",
                "Не удалось выполнить "
                        + "исходящий HTTP-запрос"
        );
    }

    private static Mapping mappingForStatus(
            int status
    ) {
        if (status >= 400
                && status < 500) {

            return new Mapping(
                    status,
                    "REMOTE_CLIENT_ERROR",
                    "Удалённый сервис отклонил запрос"
            );
        }

        if (status >= 500
                && status <= 599) {

            return new Mapping(
                    status,
                    "REMOTE_SERVER_ERROR",
                    "Удалённый сервис завершил "
                            + "запрос с ошибкой"
            );
        }

        return new Mapping(
                502,
                "REMOTE_RESPONSE_ERROR",
                "Удалённый сервис вернул "
                        + "некорректный HTTP-ответ"
        );
    }

    private static ChainElement context(
            String service,
            String component,
            String operation,
            int status,
            String errorCode,
            String message
    ) {
        return ChainElement.builder()
                .service(service)
                .component(component)
                .operation(operation)
                .errorCode(errorCode)
                .message(message)
                .timestamp(Instant.now())
                .status(status)
                .build();
    }

    private static String
    publicMessageOrDefault(
            String publicMessage,
            String defaultMessage
    ) {
        if (publicMessage == null
                || publicMessage.isBlank()) {

            return defaultMessage;
        }

        return publicMessage.trim();
    }

    private static boolean hasCause(
            Throwable throwable,
            Class<? extends Throwable> type
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
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

    private record Mapping(
            int status,
            String errorCode,
            String message
    ) {
    }
}