package ru.oreoman4ik.catchup.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;
import ru.oreoman4ik.catchup.config.CurrentServiceName;
import ru.oreoman4ik.catchup.config.TechnicalDetailsFactory;
import ru.oreoman4ik.catchup.config.UnifiedErrorProperties;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

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
    void validRemoteResponsePreservesIdentityAndChain() {
        UUID errorId =
                UUID.fromString(
                        "7c12c42e-86ee-43b0-8324-9a56bf633ed4"
                );

        ChainElement origin =
                element(
                        "inventory-service",
                        "InventoryRepository",
                        "findItem",
                        404,
                        "ITEM_NOT_FOUND"
                );

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
                .isEqualTo(
                        "ITEM_NOT_FOUND"
                );

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
                .isEqualTo(
                        "caller-service"
                );

        assertThat(caller.getComponent())
                .isEqualTo(
                        "CatalogGateway"
                );

        assertThat(caller.getOperation())
                .isEqualTo(
                        "loadItem"
                );
    }

    @Test
    void damagedJsonFallsBackSafely() {
        RestClientResponseException cause =
                responseException(
                        503,
                        "{broken-json"
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );

        UnifiedErrorException error =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "load"
                );

        assertThat(error.getErrorId())
                .isNotNull();

        assertThat(error.getStatus())
                .isEqualTo(503);

        assertThat(error.getErrorCode())
                .isEqualTo(
                        "REMOTE_SERVER_ERROR"
                );

        assertThat(error.getChainElements())
                .hasSize(1);
    }

    @Test
    void unknownFormatGetsNewErrorId() {
        UUID foreignId =
                UUID.randomUUID();

        String body = """
                {
                  "errorId": "%s",
                  "status": 404,
                  "legacyCode": "NOT_FOUND"
                }
                """.formatted(
                foreignId
        );

        UnifiedErrorException error =
                mapper.map(
                        responseException(
                                404,
                                body.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        ),
                        "CatalogGateway",
                        "load"
                );

        assertThat(error.getErrorId())
                .isNotEqualTo(
                        foreignId
                );

        assertThat(error.getErrorCode())
                .isEqualTo(
                        "REMOTE_CLIENT_ERROR"
                );
    }

    @Test
    void jsonStatusMustMatchHttpStatus() {
        UUID remoteId =
                UUID.randomUUID();

        ErrorResponse remote =
                ErrorResponse.builder()
                        .errorId(remoteId)
                        .timestamp(ERROR_TIME)
                        .status(404)
                        .message("Не найдено")
                        .errorCode(
                                "RESOURCE_NOT_FOUND"
                        )
                        .currentService(
                                "remote-service"
                        )
                        .chain(
                                List.of(
                                        element(
                                                "remote-service",
                                                "Controller",
                                                "find",
                                                404,
                                                "RESOURCE_NOT_FOUND"
                                        )
                                )
                        )
                        .build();

        UnifiedErrorException error =
                mapper.map(
                        responseException(
                                500,
                                jsonMapper
                                        .writeValueAsBytes(
                                                remote
                                        )
                        ),
                        "Gateway",
                        "load"
                );

        assertThat(error.getErrorId())
                .isNotEqualTo(
                        remoteId
                );

        assertThat(error.getStatus())
                .isEqualTo(500);

        assertThat(error.getErrorCode())
                .isEqualTo(
                        "REMOTE_SERVER_ERROR"
                );
    }

    @Test
    void validResponseLargerThan64KiBIsStillRecognized() {
        OutgoingHttpExceptionMapper largeMapper =
                new OutgoingHttpExceptionMapper(
                        "caller-service",
                        100
                );

        UUID errorId =
                UUID.randomUUID();

        List<ChainElement> chain =
                IntStream.range(0, 70)
                        .mapToObj(
                                RemoteErrorResponseSupportTests
                                        ::largeElement
                        )
                        .toList();

        ErrorResponse remote =
                ErrorResponse.builder()
                        .errorId(errorId)
                        .timestamp(ERROR_TIME)
                        .status(500)
                        .message(
                                "Удалённая ошибка"
                        )
                        .errorCode(
                                "REMOTE_TEST_ERROR"
                        )
                        .currentService(
                                "remote-service"
                        )
                        .chain(chain)
                        .build();

        byte[] body =
                jsonMapper.writeValueAsBytes(
                        remote
                );

        assertThat(body.length)
                .isGreaterThan(
                        64 * 1024
                );

        UnifiedErrorException restored =
                largeMapper.map(
                        responseException(
                                500,
                                body
                        ),
                        "Gateway",
                        "load"
                );

        assertThat(restored.getErrorId())
                .isEqualTo(errorId);

        assertThat(restored.getChainElements())
                .hasSize(71);
    }

    @Test
    void configuredRemoteBodyLimitIsRespected() {
        UnifiedErrorProperties properties =
                new UnifiedErrorProperties();

        properties.setMaxChainSize(5);
        properties.setMaxRemoteBodyBytes(
                1024
        );

        TechnicalDetailsFactory factory =
                new TechnicalDetailsFactory(
                        properties
                );

        RemoteErrorResponseDecoder decoder =
                new RemoteErrorResponseDecoder(
                        jsonMapper,
                        properties
                );

        OutgoingHttpExceptionMapper limitedMapper =
                new OutgoingHttpExceptionMapper(
                        CurrentServiceName.of(
                                "caller-service"
                        ),
                        properties,
                        decoder,
                        factory
                );

        byte[] oversizedBody =
                "x".repeat(2048)
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        UnifiedErrorException error =
                limitedMapper.map(
                        responseException(
                                500,
                                oversizedBody
                        ),
                        "Gateway",
                        "load"
                );

        ErrorResponse response =
                error.toResponse(
                        "caller-service"
                );

        assertThat(error.getStatus())
                .isEqualTo(500);

        assertThat(error.getErrorCode())
                .isEqualTo(
                        "REMOTE_SERVER_ERROR"
                );

        assertThat(response.getDetails())
                .isNotNull();

        assertThat(
                response.getDetails()
                        .getTruncation()
                        .isRemoteBody()
        ).isTrue();

        /*
         * Огромный remote body не попадает
         * в публичный response.
         */
        assertThat(
                jsonMapper
                        .writeValueAsBytes(
                                response
                        ).length
        ).isLessThan(
                16 * 1024
        );
    }

    @Test
    void maxChainSizeRemainsAbsolute() {
        List<ChainElement> remoteChain =
                List.of(
                        element(
                                "service-1",
                                "A",
                                "one",
                                404,
                                "RESOURCE_NOT_FOUND"
                        ),
                        element(
                                "service-2",
                                "B",
                                "two",
                                404,
                                "RESOURCE_NOT_FOUND"
                        ),
                        element(
                                "service-3",
                                "C",
                                "three",
                                404,
                                "RESOURCE_NOT_FOUND"
                        ),
                        element(
                                "service-4",
                                "D",
                                "four",
                                404,
                                "RESOURCE_NOT_FOUND"
                        ),
                        element(
                                "service-5",
                                "E",
                                "five",
                                404,
                                "RESOURCE_NOT_FOUND"
                        )
                );

        UUID errorId =
                UUID.randomUUID();

        ErrorResponse remote =
                ErrorResponse.builder()
                        .errorId(errorId)
                        .timestamp(ERROR_TIME)
                        .status(404)
                        .message(
                                "Ресурс не найден"
                        )
                        .errorCode(
                                "RESOURCE_NOT_FOUND"
                        )
                        .currentService(
                                "service-5"
                        )
                        .chain(remoteChain)
                        .build();

        UnifiedErrorException restored =
                mapper.map(
                        responseException(
                                404,
                                jsonMapper
                                        .writeValueAsBytes(
                                                remote
                                        )
                        ),
                        "CatalogGateway",
                        "load"
                );

        assertThat(restored.getErrorId())
                .isEqualTo(errorId);

        assertThat(
                restored.getChain()
                        .getMaxSize()
        ).isEqualTo(5);

        assertThat(restored.getChainElements())
                .hasSize(5);

        assertThat(
                restored.getChainElements()
                        .subList(0, 4)
        ).containsExactlyElementsOf(
                remoteChain.subList(
                        0,
                        4
                )
        );

        assertThat(
                restored.getChainElements()
                        .getLast()
                        .getService()
        ).isEqualTo(
                "caller-service"
        );

        assertThat(restored.isChainTruncated())
                .isTrue();
    }

    private static ChainElement element(
            String service,
            String component,
            String operation,
            int status,
            String errorCode
    ) {
        return ChainElement.builder()
                .service(service)
                .component(component)
                .operation(operation)
                .errorCode(errorCode)
                .message(
                        status == 404
                                ? "Ресурс не найден"
                                : "Удалённая ошибка"
                )
                .timestamp(ERROR_TIME)
                .status(status)
                .build();
    }

    private static ChainElement largeElement(
            int index
    ) {
        String prefix =
                "operation-" + index + "-";

        return ChainElement.builder()
                .service(
                        "s".repeat(120)
                )
                .component(
                        "c".repeat(160)
                )
                .operation(
                        prefix
                                + "o".repeat(
                                160
                                        - prefix.length()
                        )
                )
                .errorCode(
                        "REMOTE_TEST_ERROR"
                )
                .message(
                        "m".repeat(500)
                )
                .timestamp(ERROR_TIME)
                .status(500)
                .build();
    }

    private static RestClientResponseException
    responseException(
            int status,
            byte[] body
    ) {
        return new RestClientResponseException(
                "remote technical",
                status,
                "Remote Error",
                HttpHeaders.EMPTY,
                body,
                StandardCharsets.UTF_8
        );
    }
}