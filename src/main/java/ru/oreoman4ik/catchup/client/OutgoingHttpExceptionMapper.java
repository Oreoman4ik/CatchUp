package ru.oreoman4ik.catchup.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageConversionException;
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

/**
 * Преобразует ошибки исходящих HTTP-запросов
 * в UnifiedErrorException.
 */
@Component
public final class OutgoingHttpExceptionMapper {

    private static final int ABSOLUTE_MAX_CHAIN_SIZE =
            100;

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

    /**
     * Использует spring.application.name
     * как название вызывающего сервиса.
     */
    public UnifiedErrorException map(
            Throwable cause,
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

    /**
     * Преобразует ошибку HTTP-клиента в общий тип
     * библиотеки и добавляет контекст вызова.
     *
     * <p>publicMessage является только публичным
     * сообщением. Технический Throwable остаётся
     * неизменным.</p>
     */
    public UnifiedErrorException map(
            Throwable cause,
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

        /*
         * Если ошибка уже структурирована,
         * не создаём новый errorId.
         */
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

        Mapping mapping =
                classify(cause);

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

        /*
         * UnifiedErrorException.from создаёт errorId
         * и сохраняет cause как originalCause.
         */
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

    /**
     * Восстанавливает поддерживаемый ErrorResponse
     * удалённого сервиса.
     *
     * <p>Сохраняются:</p>
     *
     * <ul>
     *     <li>errorId;</li>
     *     <li>исходный timestamp;</li>
     *     <li>status;</li>
     *     <li>errorCode;</li>
     *     <li>message;</li>
     *     <li>details;</li>
     *     <li>удалённая chain.</li>
     * </ul>
     */
    public UnifiedErrorException restore(
            ErrorResponse response,
            Throwable cause,
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
            Throwable cause,
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
            Throwable cause
    ) {
        /*
         * Реальный HTTP-ответ.
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
         * Timeout.
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
         * Не удалось установить соединение:
         * connection refused, DNS, route.
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
         * Не удалось преобразовать тело ответа.
         */
        if (cause
                instanceof UnknownContentTypeException
                || hasCause(
                cause,
                HttpMessageConversionException.class
        )) {

            return new Mapping(
                    502,
                    "REMOTE_BODY_CONVERSION_ERROR",
                    "Не удалось преобразовать ответ "
                            + "удалённого сервиса"
            );
        }

        /*
         * Соединение было установлено,
         * но ответ оборвался при чтении.
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
         * Остальные сетевые/I/O ошибки.
         */
        if (cause
                instanceof ResourceAccessException
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
         * Другие ошибки RestClient/RestTemplate,
         * например ошибки декодирования ответа.
         */
        if (cause instanceof RestClientException) {
            return new Mapping(
                    502,
                    "REMOTE_RESPONSE_READ_ERROR",
                    "Не удалось прочитать ответ "
                            + "удалённого сервиса"
            );
        }

        return new Mapping(
                502,
                "REMOTE_REQUEST_ERROR",
                "Не удалось выполнить запрос "
                        + "к удалённому сервису"
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