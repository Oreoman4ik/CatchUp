package ru.oreoman4ik.catchup.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

@Component
public final class OutgoingHttpExceptionMapper {

    private static final int ABSOLUTE_MAX_CHAIN_SIZE = 100;

    private final String currentService;
    private final int maxChainSize;

    public OutgoingHttpExceptionMapper(
            @Value("${spring.application.name:application}")
            String currentService,

            @Value("${catchup.errors.max-chain-size:10}")
            int maxChainSize
    ) {
        this.currentService =
                validateServiceName(currentService);

        this.maxChainSize =
                validateMaxChainSize(maxChainSize);
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
                instanceof UnifiedErrorException existing) {

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

        Mapping mapping = classify(cause);

        String responseMessage =
                publicMessageOrDefault(
                        publicMessage,
                        mapping.message()
                );

        ChainElement context =
                context(
                        service,
                        component,
                        operation,
                        mapping.status(),
                        mapping.errorCode(),
                        responseMessage
                );

        return UnifiedErrorException.from(
                cause,
                Instant.now(),
                mapping.status(),
                mapping.errorCode(),
                responseMessage,
                null,
                context,
                maxChainSize
        );
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

        UnifiedErrorException restored =
                UnifiedErrorException.fromResponse(
                        response,
                        cause,
                        maxChainSize
                );

        Mapping localMapping =
                mappingForStatus(
                        response.getStatus()
                );

        return restored.addContext(
                context(
                        service,
                        component,
                        operation,
                        response.getStatus(),
                        localMapping.errorCode(),
                        publicMessageOrDefault(
                                publicMessage,
                                localMapping.message()
                        )
                )
        );
    }

    private static Mapping classify(
            Exception cause
    ) {
        /*
         * Удалённый сервер действительно прислал HTTP-ответ.
         */
        if (cause
                instanceof
                RestClientResponseException response) {

            return mappingForStatus(
                    response
                            .getStatusCode()
                            .value()
            );
        }

        /*
         * Timeout имеет приоритет над общей сетевой
         * классификацией.
         */
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

        /*
         * Соединение с удалённым сервисом
         * установить не удалось.
         */
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

        /*
         * Ошибка сериализации ИСХОДЯЩЕГО request body.
         *
         * Это локальная проблема вызывающего приложения.
         * Удалённый сервис мог вообще не получить запрос,
         * поэтому здесь не используется REMOTE_* код.
         */
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

        /*
         * Ответ получен, но для него нет подходящего
         * HttpMessageConverter.
         */
        if (cause instanceof UnknownContentTypeException) {
            return new Mapping(
                    502,
                    "REMOTE_BODY_CONVERSION_ERROR",
                    "Не удалось преобразовать ответ "
                            + "удалённого сервиса"
            );
        }

        /*
         * HttpMessageConverter не смог прочитать /
         * десериализовать RESPONSE body.
         */
        if (hasCause(
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

        /*
         * Базовый HttpMessageConversionException сам по себе
         * не сообщает направление операции.
         *
         * Нельзя утверждать, что ошибка произошла именно
         * при чтении response body.
         */
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

        /*
         * Ответ начал читаться, но поток оборвался.
         */
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

        /*
         * Прочая I/O ошибка после исключения
         * connection/timeout случаев выше.
         *
         * Например Connection reset.
         */
        if (cause instanceof ResourceAccessException
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

        /*
         * У RestClientException недостаточно информации,
         * чтобы честно утверждать, что проблема была именно
         * в response body.
         *
         * Поэтому используем нейтральную категорию.
         */
        if (cause instanceof RestClientException) {
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
        if (status >= 400 && status < 500) {
            return new Mapping(
                    status,
                    "REMOTE_CLIENT_ERROR",
                    "Удалённый сервис отклонил запрос"
            );
        }

        if (status >= 500 && status <= 599) {
            return new Mapping(
                    status,
                    "REMOTE_SERVER_ERROR",
                    "Удалённый сервис завершил запрос "
                            + "с ошибкой"
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

    private static String publicMessageOrDefault(
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

    private record Mapping(
            int status,
            String errorCode,
            String message
    ) {
    }
}