package ru.oreoman4ik.catchup.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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

    @Test
    void mapsTimeoutTo504() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "Read timed out "
                                + "at internal-host",
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

    @Test
    void mapsConnectionFailureToUnavailable503() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "Connection failed",
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
    void mapsOtherIoFailureToNetworkError502() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "Network reset",
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

    @Test
    void mapsResponseReadFailureTo502() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "Unexpected end of response",
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

    @Test
    void mapsBodyConversionFailureTo502() {
        RestClientException cause =
                new RestClientException(
                        "Cannot decode secret payload",
                        new HttpMessageConversionException(
                                "Cannot convert payload"
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
    }

    @Test
    void mapsGenericRestClientFailureToResponseReadError() {
        RestClientException cause =
                new RestClientException(
                        "Decoder failed on "
                                + "secret response"
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

        String publicResponse =
                exception
                        .toResponse("caller-service")
                        .toString();

        assertThat(publicResponse)
                .doesNotContain("internal-host")
                .doesNotContain("token=secret");
    }

    @Test
    void restoresStructuredRemoteErrorWithoutLosingData() {
        UUID errorId = UUID.fromString(
                "7c12c42e-86ee-43b0-8324-9a56bf633ed4"
        );

        Instant timestamp = Instant.parse(
                "2026-08-10T08:00:00Z"
        );

        ErrorDetails details =
                ErrorDetails.builder()
                        .resource("COMPONENT")
                        .build();

        ChainElement remoteContext =
                ChainElement.builder()
                        .service("remote-service")
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
                                List.of(remoteContext)
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
                restored
                        .getChainElements()
                        .getFirst()
        ).isEqualTo(remoteContext);

        ChainElement callerContext =
                restored
                        .getChainElements()
                        .getLast();

        assertThat(callerContext.getService())
                .isEqualTo("caller-service");

        assertThat(callerContext.getComponent())
                .isEqualTo("CatalogGateway");

        assertThat(callerContext.getOperation())
                .isEqualTo("loadCatalog");

        assertThat(callerContext.getErrorCode())
                .isEqualTo(
                        "REMOTE_CLIENT_ERROR"
                );
    }

    private static void assertMapped(
            UnifiedErrorException exception,
            Throwable cause,
            int expectedStatus,
            String expectedCode,
            String expectedMessage
    ) {
        assertThat(exception.getErrorId())
                .isNotNull();

        assertThat(exception.getStatus())
                .isEqualTo(expectedStatus);

        assertThat(exception.getErrorCode())
                .isEqualTo(expectedCode);

        assertThat(exception.getMessage())
                .isEqualTo(expectedMessage);

        assertThat(exception.getOriginalCause())
                .isSameAs(cause);

        assertThat(exception.getCause())
                .isSameAs(cause);

        assertThat(exception.getChainElements())
                .hasSize(1);

        ChainElement context =
                exception
                        .getChainElements()
                        .getFirst();

        assertThat(context.getService())
                .isEqualTo("caller-service");

        assertThat(context.getComponent())
                .isEqualTo("CatalogGateway");

        assertThat(context.getOperation())
                .isEqualTo("loadCatalog");

        assertThat(context.getStatus())
                .isEqualTo(expectedStatus);

        assertThat(context.getErrorCode())
                .isEqualTo(expectedCode);

        assertThat(context.getMessage())
                .isEqualTo(expectedMessage);
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
}