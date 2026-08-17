package ru.oreoman4ik.catchup.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client
        .RestClientResponseException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteErrorResponseSupportTests {

    private static final Instant ERROR_TIME =
            Instant.parse(
                    "2026-08-13T08:10:00.123Z"
            );

    private JsonMapper jsonMapper;

    private OutgoingHttpExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        jsonMapper =
                JsonMapper.builder().build();

        mapper =
                new OutgoingHttpExceptionMapper(
                        "caller-service",
                        5
                );
    }

    @Test
    void supportedRemoteErrorPreservesIdChainAndStatus() {
        UUID errorId =
                UUID.fromString(
                        "7c12c42e-86ee-43b0-"
                                + "8324-9a56bf633ed4"
                );

        ChainElement origin =
                ChainElement.builder()
                        .service(
                                "inventory-service"
                        )
                        .component(
                                "InventoryRepository"
                        )
                        .operation("findItem")
                        .errorCode(
                                "ITEM_NOT_FOUND"
                        )
                        .message(
                                "Товар не найден"
                        )
                        .timestamp(ERROR_TIME)
                        .status(404)
                        .build();

        ErrorResponse remote =
                ErrorResponse.builder()
                        .errorId(errorId)
                        .timestamp(ERROR_TIME)
                        .status(404)
                        .message(
                                "Товар не найден"
                        )
                        .errorCode(
                                "ITEM_NOT_FOUND"
                        )
                        .currentService(
                                "inventory-service"
                        )
                        .chain(
                                List.of(origin)
                        )
                        .details(
                                ErrorDetails.builder()
                                        .resource("ITEM")
                                        .build()
                        )
                        .build();

        RestClientResponseException cause =
                responseException(
                        404,
                        jsonMapper
                                .writeValueAsBytes(
                                        remote
                                )
                );

        UnifiedErrorException restored =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadItem"
                );

        assertThat(restored.getErrorId())
                .isEqualTo(errorId);

        assertThat(restored.getTimestamp())
                .isEqualTo(ERROR_TIME);

        assertThat(restored.getStatus())
                .isEqualTo(404);

        assertThat(restored.getErrorCode())
                .isEqualTo("ITEM_NOT_FOUND");

        assertThat(restored.getDetails())
                .isEqualTo(remote.getDetails());

        assertThat(restored.getOriginalCause())
                .isSameAs(cause);

        assertThat(restored.getChainElements())
                .hasSize(2);

        assertThat(
                restored.getChainElements()
                        .getFirst()
        ).isEqualTo(origin);

        ChainElement caller =
                restored.getChainElements()
                        .getLast();

        assertThat(caller.getService())
                .isEqualTo("caller-service");

        assertThat(caller.getComponent())
                .isEqualTo("CatalogGateway");

        assertThat(caller.getOperation())
                .isEqualTo("loadItem");
    }

    @Test
    void damagedJsonFallsBackSafely() {
        RestClientResponseException cause =
                responseException(
                        503,
                        "{damaged-json"
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadItem"
                );

        assertThat(exception.getErrorId())
                .isNotNull();

        assertThat(exception.getStatus())
                .isEqualTo(503);

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        "REMOTE_SERVER_ERROR"
                );

        assertThat(exception.getChainElements())
                .hasSize(1);
    }

    @Test
    void unknownFormatCreatesNewError() {
        UUID foreignId =
                UUID.fromString(
                        "aaaaaaaa-bbbb-cccc-dddd-"
                                + "eeeeeeeeeeee"
                );

        String json = """
                {
                  "errorId": "%s",
                  "status": 404,
                  "message": "legacy format",
                  "legacyCode": "NOT_FOUND"
                }
                """.formatted(foreignId);

        RestClientResponseException cause =
                responseException(
                        404,
                        json.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadItem"
                );

        assertThat(exception.getErrorId())
                .isNotEqualTo(foreignId);

        assertThat(exception.getStatus())
                .isEqualTo(404);

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        "REMOTE_CLIENT_ERROR"
                );
    }

    @Test
    void jsonStatusMustMatchRealHttpStatus() {
        UUID remoteId = UUID.randomUUID();

        ErrorResponse body =
                ErrorResponse.builder()
                        .errorId(remoteId)
                        .timestamp(ERROR_TIME)
                        .status(404)
                        .message("Товар не найден")
                        .errorCode(
                                "ITEM_NOT_FOUND"
                        )
                        .currentService(
                                "inventory-service"
                        )
                        .chain(
                                List.of(
                                        ChainElement.builder()
                                                .service(
                                                        "inventory-service"
                                                )
                                                .component(
                                                        "Controller"
                                                )
                                                .operation(
                                                        "getItem"
                                                )
                                                .errorCode(
                                                        "ITEM_NOT_FOUND"
                                                )
                                                .message(
                                                        "Товар не найден"
                                                )
                                                .timestamp(
                                                        ERROR_TIME
                                                )
                                                .status(404)
                                                .build()
                                )
                        )
                        .build();

        /*
         * HTTP = 500, JSON = 404.
         */
        RestClientResponseException cause =
                responseException(
                        500,
                        jsonMapper
                                .writeValueAsBytes(
                                        body
                                )
                );

        UnifiedErrorException exception =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadItem"
                );

        assertThat(exception.getErrorId())
                .isNotEqualTo(remoteId);

        assertThat(exception.getStatus())
                .isEqualTo(500);

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        "REMOTE_SERVER_ERROR"
                );
    }

    private static RestClientResponseException
    responseException(
            int status,
            byte[] body
    ) {
        return new RestClientResponseException(
                "remote technical error",
                status,
                "Remote Error",
                HttpHeaders.EMPTY,
                body,
                StandardCharsets.UTF_8
        );
    }
}