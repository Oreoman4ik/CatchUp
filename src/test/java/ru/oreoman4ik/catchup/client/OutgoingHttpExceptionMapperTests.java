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
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;

import java.io.EOFException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

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
    void mapsRemote4xx() {
        RestClientResponseException cause =
                responseException(
                        404,
                        "not-json"
                );

        UnifiedErrorException error =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "load"
                );

        assertMapped(
                error,
                cause,
                404,
                "REMOTE_CLIENT_ERROR",
                "Удалённый сервис отклонил запрос"
        );
    }

    @Test
    void mapsRemote5xx() {
        RestClientResponseException cause =
                responseException(
                        503,
                        "not-json"
                );

        UnifiedErrorException error =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "load"
                );

        assertMapped(
                error,
                cause,
                503,
                "REMOTE_SERVER_ERROR",
                "Удалённый сервис завершил "
                        + "запрос с ошибкой"
        );
    }

    @Test
    void mapsTimeout() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "secret timeout URL",
                        new SocketTimeoutException(
                                "Read timed out"
                        )
                );

        assertMapped(
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "load"
                ),
                cause,
                504,
                "REMOTE_TIMEOUT",
                "Истекло время ожидания ответа "
                        + "удалённого сервиса"
        );
    }

    @Test
    void mapsConnectionFailure() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "secret",
                        new ConnectException(
                                "Connection refused"
                        )
                );

        assertMapped(
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "load"
                ),
                cause,
                503,
                "REMOTE_UNAVAILABLE",
                "Удалённый сервис недоступен"
        );
    }

    @Test
    void mapsConnectionResetAsNetworkError() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "secret",
                        new SocketException(
                                "Connection reset"
                        )
                );

        assertMapped(
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "load"
                ),
                cause,
                502,
                "REMOTE_NETWORK_ERROR",
                "Ошибка сети при обращении "
                        + "к удалённому сервису"
        );
    }

    @Test
    void mapsUnexpectedEofAsResponseReadError() {
        ResourceAccessException cause =
                new ResourceAccessException(
                        "secret",
                        new EOFException(
                                "response fragment"
                        )
                );

        assertMapped(
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "load"
                ),
                cause,
                502,
                "REMOTE_RESPONSE_READ_ERROR",
                "Не удалось прочитать ответ "
                        + "удалённого сервиса"
        );
    }

    @Test
    void requestBodySerializationIsLocal500() {
        RestClientException cause =
                new RestClientException(
                        "request conversion",
                        new HttpMessageNotWritableException(
                                "token=secret"
                        )
                );

        assertMapped(
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "save"
                ),
                cause,
                500,
                "OUTGOING_REQUEST_BODY_ERROR",
                "Не удалось сформировать "
                        + "исходящий запрос"
        );
    }

    @Test
    void responseBodyDeserializationIs502() {
        RestClientException cause =
                new RestClientException(
                        "response conversion",
                        new HttpMessageNotReadableException(
                                "secret body",
                                httpInputMessage()
                        )
                );

        assertMapped(
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "load"
                ),
                cause,
                502,
                "REMOTE_BODY_CONVERSION_ERROR",
                "Не удалось преобразовать ответ "
                        + "удалённого сервиса"
        );
    }

    @Test
    void unknownContentTypeIsResponseConversionError() {
        UnknownContentTypeException cause =
                new UnknownContentTypeException(
                        String.class,
                        MediaType.APPLICATION_OCTET_STREAM,
                        HttpStatus.OK,
                        "OK",
                        HttpHeaders.EMPTY,
                        "secret body"
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );

        assertMapped(
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "load"
                ),
                cause,
                502,
                "REMOTE_BODY_CONVERSION_ERROR",
                "Не удалось преобразовать ответ "
                        + "удалённого сервиса"
        );
    }

    @Test
    void genericConversionDoesNotBlameRemoteResponse() {
        RestClientException cause =
                new RestClientException(
                        "conversion",
                        new HttpMessageConversionException(
                                "unknown phase"
                        )
                );

        assertMapped(
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "load"
                ),
                cause,
                500,
                "OUTGOING_HTTP_CONVERSION_ERROR",
                "Не удалось преобразовать данные "
                        + "исходящего HTTP-вызова"
        );
    }

    @Test
    void genericRestClientErrorUsesNeutralCode() {
        RestClientException cause =
                new RestClientException(
                        "token=secret"
                );

        assertMapped(
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "load"
                ),
                cause,
                502,
                "OUTGOING_HTTP_ERROR",
                "Не удалось выполнить "
                        + "исходящий HTTP-запрос"
        );
    }

    @Test
    void technicalCauseIsNotPublished() {
        RestClientException cause =
                new RestClientException(
                        "http://internal-host"
                                + "?token=secret"
                );

        UnifiedErrorException error =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "load"
                );

        ErrorResponse response =
                error.toResponse(
                        "caller-service"
                );

        String publicText =
                response.toString();

        assertThat(publicText)
                .doesNotContain(
                        "internal-host"
                )
                .doesNotContain(
                        "token=secret"
                );

        assertThat(
                error.getOriginalCause()
                        .getMessage()
        ).contains(
                "token=secret"
        );
    }

    @Test
    void longCustomPublicMessageIsLimited() {
        RestClientException cause =
                new RestClientException(
                        "technical"
                );

        UnifiedErrorException error =
                mapper.map(
                        cause,
                        "caller-service",
                        "CatalogGateway",
                        "load",
                        "x".repeat(1000)
                );

        assertThat(error.getMessage())
                .hasSize(500);

        assertThat(
                error.getChainElements()
                        .getFirst()
                        .getMessage()
        ).hasSize(500);

        ErrorResponse response =
                error.toResponse(
                        "caller-service"
                );

        assertThat(
                response.getDetails()
                        .getTruncation()
                        .isMessage()
        ).isTrue();
    }

    private static void assertMapped(
            UnifiedErrorException error,
            Exception cause,
            int status,
            String code,
            String message
    ) {
        assertThat(error.getErrorId())
                .isNotNull();

        assertThat(error.getStatus())
                .isEqualTo(status);

        assertThat(error.getErrorCode())
                .isEqualTo(code);

        assertThat(error.getMessage())
                .isEqualTo(message);

        assertThat(error.getOriginalCause())
                .isSameAs(cause);

        assertThat(error.getCause())
                .isSameAs(cause);

        assertThat(error.getChainElements())
                .hasSize(1);

        ChainElement context =
                error.getChainElements()
                        .getFirst();

        assertThat(context.getService())
                .isEqualTo(
                        "caller-service"
                );

        assertThat(context.getComponent())
                .isEqualTo(
                        "CatalogGateway"
                );

        assertThat(context.getStatus())
                .isEqualTo(status);

        assertThat(context.getErrorCode())
                .isEqualTo(code);
    }

    private static RestClientResponseException
    responseException(
            int status,
            String body
    ) {
        return new RestClientResponseException(
                "remote technical",
                status,
                "Remote Error",
                HttpHeaders.EMPTY,
                body.getBytes(
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
                return InputStream.nullInputStream();
            }

            @Override
            public HttpHeaders getHeaders() {
                return HttpHeaders.EMPTY;
            }
        };
    }
}