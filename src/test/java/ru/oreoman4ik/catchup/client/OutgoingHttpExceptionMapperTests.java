package ru.oreoman4ik.catchup.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;

import java.io.EOFException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutgoingHttpExceptionMapperTests {

    private OutgoingHttpExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper =
                new OutgoingHttpExceptionMapper(
                        "caller-service",
                        5
                );
    }

    /*
     * ------------------------------------------------------------
     * HTTP status
     * ------------------------------------------------------------
     */

    @Test
    void preservesRemote4xxStatus() {
        RestClientResponseException cause =
                responseException(404);

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                404,
                "REMOTE_CLIENT_ERROR",
                "Удалённый сервис отклонил запрос"
        );
    }

    @Test
    void preservesRemote5xxStatus() {
        RestClientResponseException cause =
                responseException(503);

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                503,
                "REMOTE_SERVER_ERROR",
                "Удалённый сервис завершил запрос "
                        + "с ошибкой"
        );
    }

    /*
     * ------------------------------------------------------------
     * Timeout
     * ------------------------------------------------------------
     */

    @Test
    void mapsTimeoutTo504() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "read timeout "
                                + "token=secret",
                        new SocketTimeoutException(
                                "Read timed out"
                        )
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                504,
                "REMOTE_TIMEOUT",
                "Истекло время ожидания ответа "
                        + "удалённого сервиса"
        );
    }

    /*
     * ------------------------------------------------------------
     * Unavailable
     * ------------------------------------------------------------
     */

    @Test
    void mapsConnectionRefusedTo503() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "connection failed",
                        new ConnectException(
                                "Connection refused"
                        )
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                503,
                "REMOTE_UNAVAILABLE",
                "Удалённый сервис недоступен"
        );
    }

    @Test
    void mapsUnknownHostTo503() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "DNS failed",
                        new UnknownHostException(
                                "internal-secret-host"
                        )
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                503,
                "REMOTE_UNAVAILABLE",
                "Удалённый сервис недоступен"
        );
    }

    @Test
    void mapsNoRouteToHostTo503() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "route failed",
                        new NoRouteToHostException(
                                "secret subnet"
                        )
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                503,
                "REMOTE_UNAVAILABLE",
                "Удалённый сервис недоступен"
        );
    }

    /*
     * ------------------------------------------------------------
     * Network
     * ------------------------------------------------------------
     */

    @Test
    void mapsConnectionResetToNetwork502() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "network error",
                        new SocketException(
                                "Connection reset"
                        )
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                502,
                "REMOTE_NETWORK_ERROR",
                "Ошибка сети при обращении "
                        + "к удалённому сервису"
        );
    }

    /*
     * ------------------------------------------------------------
     * Response read
     * ------------------------------------------------------------
     */

    @Test
    void mapsUnexpectedEofToResponseRead502() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "response interrupted",
                        new EOFException(
                                "secret response fragment"
                        )
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                502,
                "REMOTE_RESPONSE_READ_ERROR",
                "Не удалось прочитать ответ "
                        + "удалённого сервиса"
        );
    }

    /*
     * ------------------------------------------------------------
     * Request body conversion
     * ------------------------------------------------------------
     */

    @Test
    void mapsRequestBodySerializationFailureToLocal500() {
        HttpMessageNotWritableException conversion =
                new HttpMessageNotWritableException(
                        "Cannot serialize request "
                                + "token=secret"
                );

        RestClientException cause =
                new RestClientException(
                        "Request body conversion failed",
                        conversion
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                500,
                "OUTGOING_REQUEST_BODY_ERROR",
                "Не удалось сформировать "
                        + "исходящий запрос"
        );

        assertThat(
                exception
                        .toResponse("caller-service")
                        .toString()
        )
                .doesNotContain(
                        "token=secret"
                )
                .doesNotContain(
                        "Cannot serialize request"
                );
    }

    /*
     * ------------------------------------------------------------
     * Response body conversion
     * ------------------------------------------------------------
     */

    @Test
    void mapsResponseBodyDeserializationFailureTo502() {
        HttpMessageNotReadableException conversion =
                new HttpMessageNotReadableException(
                        "Cannot deserialize "
                                + "secret response",
                        httpInputMessage()
                );

        RestClientException cause =
                new RestClientException(
                        "Response extraction failed",
                        conversion
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                502,
                "REMOTE_BODY_CONVERSION_ERROR",
                "Не удалось преобразовать ответ "
                        + "удалённого сервиса"
        );
    }

    @Test
    void mapsUnknownContentTypeToResponseConversion502() {
        UnknownContentTypeException cause =
                new UnknownContentTypeException(
                        String.class,
                        MediaType.APPLICATION_OCTET_STREAM,
                        HttpStatus.OK,
                        "OK",
                        HttpHeaders.EMPTY,
                        "secret unsupported response"
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                502,
                "REMOTE_BODY_CONVERSION_ERROR",
                "Не удалось преобразовать ответ "
                        + "удалённого сервиса"
        );

        assertThat(
                exception
                        .toResponse("caller-service")
                        .toString()
        ).doesNotContain(
                "secret unsupported response"
        );
    }

    /*
     * ------------------------------------------------------------
     * Ambiguous conversion
     * ------------------------------------------------------------
     */

    @Test
    void genericConversionFailureDoesNotBlameRemoteResponse() {
        RestClientException cause =
                new RestClientException(
                        "HTTP conversion failed",
                        new HttpMessageConversionException(
                                "Unknown conversion phase"
                        )
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                500,
                "OUTGOING_HTTP_CONVERSION_ERROR",
                "Не удалось преобразовать данные "
                        + "исходящего HTTP-вызова"
        );
    }

    /*
     * ------------------------------------------------------------
     * Generic RestClient failure
     * ------------------------------------------------------------
     */

    @Test
    void genericRestClientFailureUsesNeutral502() {
        RestClientException cause =
                new RestClientException(
                        "Unknown technical "
                                + "HTTP client failure"
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertMapped(
                exception,
                cause,
                502,
                "OUTGOING_HTTP_ERROR",
                "Не удалось выполнить "
                        + "исходящий HTTP-запрос"
        );
    }

    /*
     * ------------------------------------------------------------
     * Public message vs technical cause
     * ------------------------------------------------------------
     */

    @Test
    void customPublicMessageDoesNotReplaceTechnicalCause() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "GET http://internal-host"
                                + "?token=secret",
                        new ConnectException(
                                "Connection refused"
                        )
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "caller-service",
                        "CatalogGateway",
                        "loadCatalog",
                        "Каталог временно недоступен"
                );

        assertThat(exception.getMessage())
                .isEqualTo(
                        "Каталог временно недоступен"
                );

        assertThat(exception.getOriginalCause())
                .isSameAs(cause);

        assertThat(
                exception
                        .getOriginalCause()
                        .getMessage()
        )
                .contains("internal-host")
                .contains("token=secret");

        assertThat(
                exception
                        .toResponse(
                                "caller-service"
                        )
                        .toString()
        )
                .doesNotContain("internal-host")
                .doesNotContain("token=secret");
    }

    /*
     * ------------------------------------------------------------
     * Existing structured error
     * ------------------------------------------------------------
     */

    @Test
    void existingUnifiedErrorKeepsIdentity() {
        RuntimeException originalCause =
                new RuntimeException(
                        "technical root"
                );

        UnifiedErrorException existing =
                UnifiedErrorException.from(
                        originalCause,
                        Instant.parse(
                                "2026-08-10T08:00:00Z"
                        ),
                        502,
                        "OUTGOING_HTTP_ERROR",
                        "Не удалось выполнить "
                                + "исходящий HTTP-запрос",
                        null,
                        5
                );

        UUID errorId =
                existing.getErrorId();

        UnifiedErrorException mapped =
                mapper.map(
                        existing,
                        "caller-service",
                        "CatalogGateway",
                        "loadCatalog",
                        null
                );

        assertThat(mapped)
                .isSameAs(existing);

        assertThat(mapped.getErrorId())
                .isEqualTo(errorId);

        assertThat(mapped.getOriginalCause())
                .isSameAs(originalCause);

        assertThat(mapped.getChainElements())
                .hasSize(1);
    }

    /*
     * ------------------------------------------------------------
     * Restore supported remote ErrorResponse
     * ------------------------------------------------------------
     */

    @Test
    void restoresRemoteErrorWithoutLosingStructuredData() {
        UUID errorId =
                UUID.fromString(
                        "7c12c42e-86ee-43b0-8324-9a56bf633ed4"
                );

        Instant timestamp =
                Instant.parse(
                        "2026-08-10T08:00:00Z"
                );

        ErrorDetails details =
                ErrorDetails.builder()
                        .resource("COMPONENT")
                        .build();

        ChainElement remoteContext =
                ChainElement.builder()
                        .service(
                                "remote-service"
                        )
                        .component(
                                "ComponentRepository"
                        )
                        .operation("findById")
                        .errorCode(
                                "COMPONENT_NOT_FOUND"
                        )
                        .message(
                                "Компонент не найден"
                        )
                        .timestamp(timestamp)
                        .status(404)
                        .build();

        ErrorResponse response =
                ErrorResponse.builder()
                        .errorId(errorId)
                        .timestamp(timestamp)
                        .status(404)
                        .message(
                                "Компонент не найден"
                        )
                        .errorCode(
                                "COMPONENT_NOT_FOUND"
                        )
                        .currentService(
                                "remote-service"
                        )
                        .chain(
                                List.of(
                                        remoteContext
                                )
                        )
                        .details(details)
                        .build();

        RestClientResponseException cause =
                responseException(404);

        UnifiedErrorException restored =
                mapper.restore(
                        response,
                        cause,
                        "CatalogGateway",
                        "loadCatalog"
                );

        assertThat(restored.getErrorId())
                .isEqualTo(errorId);

        assertThat(restored.getTimestamp())
                .isEqualTo(timestamp);

        assertThat(restored.getStatus())
                .isEqualTo(404);

        assertThat(restored.getErrorCode())
                .isEqualTo(
                        "COMPONENT_NOT_FOUND"
                );

        assertThat(restored.getMessage())
                .isEqualTo(
                        "Компонент не найден"
                );

        assertThat(restored.getDetails())
                .isEqualTo(details);

        assertThat(restored.getOriginalCause())
                .isSameAs(cause);

        assertThat(restored.getChainElements())
                .hasSize(2);

        assertThat(
                restored.getChainElements()
                        .getFirst()
        ).isEqualTo(remoteContext);

        ChainElement callerContext =
                restored
                        .getChainElements()
                        .getLast();

        assertThat(callerContext.getService())
                .isEqualTo(
                        "caller-service"
                );

        assertThat(callerContext.getComponent())
                .isEqualTo(
                        "CatalogGateway"
                );

        assertThat(callerContext.getOperation())
                .isEqualTo(
                        "loadCatalog"
                );

        assertThat(callerContext.getErrorCode())
                .isEqualTo(
                        "REMOTE_CLIENT_ERROR"
                );
    }

    /*
     * ------------------------------------------------------------
     * Helpers
     * ------------------------------------------------------------
     */

    private static void assertMapped(
            UnifiedErrorException exception,
            Exception originalCause,
            int expectedStatus,
            String expectedCode,
            String expectedMessage
    ) {
        assertThat(exception.getErrorId())
                .isNotNull();

        assertThat(exception.getTimestamp())
                .isNotNull();

        assertThat(exception.getStatus())
                .isEqualTo(
                        expectedStatus
                );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        expectedCode
                );

        assertThat(exception.getMessage())
                .isEqualTo(
                        expectedMessage
                );

        assertThat(exception.getOriginalCause())
                .isSameAs(
                        originalCause
                );

        assertThat(exception.getCause())
                .isSameAs(
                        originalCause
                );

        assertThat(exception.getChainElements())
                .hasSize(1);

        ChainElement context =
                exception.getChainElements()
                        .getFirst();

        assertThat(context.getService())
                .isEqualTo(
                        "caller-service"
                );

        assertThat(context.getComponent())
                .isEqualTo(
                        "CatalogGateway"
                );

        assertThat(context.getOperation())
                .isEqualTo(
                        "loadCatalog"
                );

        assertThat(context.getStatus())
                .isEqualTo(
                        expectedStatus
                );

        assertThat(context.getErrorCode())
                .isEqualTo(
                        expectedCode
                );

        assertThat(context.getMessage())
                .isEqualTo(
                        expectedMessage
                );
    }

    private static RestClientResponseException
    responseException(
            int status
    ) {
        return new RestClientResponseException(
                "remote technical message "
                        + "token=secret",
                status,
                "Remote Error",
                HttpHeaders.EMPTY,
                "secret response body"
                        .getBytes(
                                StandardCharsets.UTF_8
                        ),
                StandardCharsets.UTF_8
        );
    }

    private static HttpInputMessage
    httpInputMessage() {

        return new HttpInputMessage() {

            @Override
            public InputStream getBody() {
                return InputStream
                        .nullInputStream();
            }

            @Override
            public HttpHeaders getHeaders() {
                return HttpHeaders.EMPTY;
            }
        };
    }
}