package ru.oreoman4ik.catchup.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorDetails;
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
                        .operation(
                                "findItem"
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

        assertThat(
                restored.getErrorId()
        ).isEqualTo(errorId);

        assertThat(
                restored.getTimestamp()
        ).isEqualTo(ERROR_TIME);

        assertThat(
                restored.getStatus()
        ).isEqualTo(404);

        assertThat(
                restored.getErrorCode()
        ).isEqualTo(
                "ITEM_NOT_FOUND"
        );

        assertThat(
                restored.getMessage()
        ).isEqualTo(
                "Товар не найден"
        );

        assertThat(
                restored.getDetails()
        ).isEqualTo(
                remote.getDetails()
        );

        assertThat(
                restored.getOriginalCause()
        ).isSameAs(cause);

        assertThat(
                restored.getCause()
        ).isSameAs(cause);

        assertThat(
                restored.getChainElements()
        ).hasSize(2);

        assertThat(
                restored
                        .getChainElements()
                        .getFirst()
        ).isEqualTo(origin);

        ChainElement caller =
                restored
                        .getChainElements()
                        .getLast();

        assertThat(
                caller.getService()
        ).isEqualTo(
                "caller-service"
        );

        assertThat(
                caller.getComponent()
        ).isEqualTo(
                "CatalogGateway"
        );

        assertThat(
                caller.getOperation()
        ).isEqualTo(
                "loadItem"
        );

        assertThat(
                caller.getStatus()
        ).isEqualTo(404);

        assertThat(
                caller.getErrorCode()
        ).isEqualTo(
                "REMOTE_CLIENT_ERROR"
        );
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

        assertThat(
                exception.getErrorId()
        ).isNotNull();

        assertThat(
                exception.getStatus()
        ).isEqualTo(503);

        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                "REMOTE_SERVER_ERROR"
        );

        assertThat(
                exception.getMessage()
        ).isEqualTo(
                "Удалённый сервис завершил "
                        + "запрос с ошибкой"
        );

        assertThat(
                exception.getOriginalCause()
        ).isSameAs(cause);

        assertThat(
                exception.getChainElements()
        ).hasSize(1);

        ChainElement caller =
                exception
                        .getChainElements()
                        .getFirst();

        assertThat(
                caller.getService()
        ).isEqualTo(
                "caller-service"
        );

        assertThat(
                caller.getComponent()
        ).isEqualTo(
                "CatalogGateway"
        );

        assertThat(
                caller.getOperation()
        ).isEqualTo(
                "loadItem"
        );
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
                """.formatted(
                foreignId
        );

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

        /*
         * foreignId не должен быть принят:
         * JSON не соответствует ErrorResponse.
         */
        assertThat(
                exception.getErrorId()
        ).isNotEqualTo(
                foreignId
        );

        assertThat(
                exception.getStatus()
        ).isEqualTo(404);

        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                "REMOTE_CLIENT_ERROR"
        );

        assertThat(
                exception.getOriginalCause()
        ).isSameAs(cause);

        assertThat(
                exception.getChainElements()
        ).hasSize(1);
    }

    @Test
    void jsonStatusMustMatchRealHttpStatus() {
        UUID remoteId =
                UUID.randomUUID();

        ErrorResponse body =
                ErrorResponse.builder()
                        .errorId(remoteId)
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
                                List.of(
                                        chainElement(
                                                "inventory-service",
                                                "Controller",
                                                "getItem",
                                                "ITEM_NOT_FOUND",
                                                "Товар не найден",
                                                404
                                        )
                                )
                        )
                        .build();

        /*
         * Фактический HTTP status = 500,
         * status внутри JSON = 404.
         *
         * Такой ответ не считается доверенным
         * ErrorResponse библиотеки.
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

        assertThat(
                exception.getErrorId()
        ).isNotEqualTo(
                remoteId
        );

        assertThat(
                exception.getStatus()
        ).isEqualTo(500);

        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                "REMOTE_SERVER_ERROR"
        );

        assertThat(
                exception.getChainElements()
        ).hasSize(1);
    }

    @Test
    void validLibraryResponseLargerThan64KiBIsRestored() {
        /*
         * Здесь нужен более высокий chain limit,
         * чтобы большой remote response не был
         * обрезан из-за max-chain-size.
         */
        OutgoingHttpExceptionMapper largeChainMapper =
                new OutgoingHttpExceptionMapper(
                        "caller-service",
                        100
                );

        UUID errorId =
                UUID.fromString(
                        "11111111-2222-3333-4444-"
                                + "555555555555"
                );

        /*
         * Каждый элемент заполнен почти до максимально
         * допустимого моделью размера.
         *
         * 70 таких элементов гарантированно дают JSON
         * больше прежнего лимита 64 KiB.
         */
        List<ChainElement> remoteChain =
                IntStream.range(
                                0,
                                70
                        )
                        .mapToObj(
                                RemoteErrorResponseSupportTests
                                        ::largeChainElement
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
                                "inventory-service"
                        )
                        .chain(
                                remoteChain
                        )
                        .build();

        byte[] body =
                jsonMapper
                        .writeValueAsBytes(
                                remote
                        );

        /*
         * Regression для прежнего:
         *
         * MAX_SUPPORTED_BODY_BYTES = 64 * 1024.
         */
        assertThat(
                body.length
        ).isGreaterThan(
                64 * 1024
        );

        RestClientResponseException cause =
                responseException(
                        500,
                        body
                );

        UnifiedErrorException restored =
                largeChainMapper.map(
                        cause,
                        "CatalogGateway",
                        "loadItem"
                );

        /*
         * Если decoder ошибочно отвергнет body,
         * здесь будет новый UUID.
         */
        assertThat(
                restored.getErrorId()
        ).isEqualTo(
                errorId
        );

        assertThat(
                restored.getTimestamp()
        ).isEqualTo(
                ERROR_TIME
        );

        assertThat(
                restored.getStatus()
        ).isEqualTo(500);

        assertThat(
                restored.getErrorCode()
        ).isEqualTo(
                "REMOTE_TEST_ERROR"
        );

        /*
         * max-chain-size = 100.
         * Remote = 70 элементов.
         * Caller = ещё 1.
         */
        assertThat(
                restored.getChainElements()
        ).hasSize(71);

        assertThat(
                restored
                        .getChainElements()
                        .subList(
                                0,
                                70
                        )
        ).containsExactlyElementsOf(
                remoteChain
        );

        ChainElement caller =
                restored
                        .getChainElements()
                        .getLast();

        assertThat(
                caller.getService()
        ).isEqualTo(
                "caller-service"
        );

        assertThat(
                caller.getComponent()
        ).isEqualTo(
                "CatalogGateway"
        );

        assertThat(
                caller.getOperation()
        ).isEqualTo(
                "loadItem"
        );
    }

    @Test
    void maxChainSizeIsAbsoluteAndReservesPlaceForCaller() {
        UUID errorId =
                UUID.fromString(
                        "22222222-3333-4444-5555-"
                                + "666666666666"
                );

        List<ChainElement> remoteChain =
                List.of(
                        chainElement(
                                "service-1",
                                "RemoteComponent",
                                "operation-1",
                                "RESOURCE_NOT_FOUND",
                                "Ресурс не найден",
                                404
                        ),
                        chainElement(
                                "service-2",
                                "RemoteComponent",
                                "operation-2",
                                "RESOURCE_NOT_FOUND",
                                "Ресурс не найден",
                                404
                        ),
                        chainElement(
                                "service-3",
                                "RemoteComponent",
                                "operation-3",
                                "RESOURCE_NOT_FOUND",
                                "Ресурс не найден",
                                404
                        ),
                        chainElement(
                                "service-4",
                                "RemoteComponent",
                                "operation-4",
                                "RESOURCE_NOT_FOUND",
                                "Ресурс не найден",
                                404
                        ),
                        chainElement(
                                "service-5",
                                "RemoteComponent",
                                "operation-5",
                                "RESOURCE_NOT_FOUND",
                                "Ресурс не найден",
                                404
                        )
                );

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
                        .chain(
                                remoteChain
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

        /*
         * setUp() создаёт mapper с:
         *
         * max-chain-size = 5
         */
        UnifiedErrorException restored =
                mapper.map(
                        cause,
                        "CatalogGateway",
                        "loadItem"
                );

        assertThat(
                restored.getErrorId()
        ).isEqualTo(
                errorId
        );

        /*
         * Самое важное:
         * локальный maxChainSize не увеличился.
         */
        assertThat(
                restored.getChain()
                        .getMaxSize()
        ).isEqualTo(5);

        assertThat(
                restored.getChainElements()
        ).hasSize(5);

        /*
         * Одно место было зарезервировано
         * для caller-service.
         *
         * Поэтому из remote chain сохранились
         * первые четыре уровня.
         */
        assertThat(
                restored
                        .getChainElements()
                        .subList(
                                0,
                                4
                        )
        ).containsExactlyElementsOf(
                remoteChain.subList(
                        0,
                        4
                )
        );

        /*
         * Пятый remote элемент не помещается,
         * потому что последнее место принадлежит
         * текущему сервису.
         */
        assertThat(
                restored.getChainElements()
        ).doesNotContain(
                remoteChain.get(4)
        );

        ChainElement caller =
                restored
                        .getChainElements()
                        .getLast();

        assertThat(
                caller.getService()
        ).isEqualTo(
                "caller-service"
        );

        assertThat(
                caller.getComponent()
        ).isEqualTo(
                "CatalogGateway"
        );

        assertThat(
                caller.getOperation()
        ).isEqualTo(
                "loadItem"
        );

        assertThat(
                restored.isChainLimitReached()
        ).isTrue();
    }

    private static ChainElement largeChainElement(
            int index
    ) {
        String operationPrefix =
                "operation-"
                        + index
                        + "-";

        return ChainElement.builder()
                .service(
                        "s".repeat(120)
                )
                .component(
                        "c".repeat(160)
                )
                .operation(
                        operationPrefix
                                + "o".repeat(
                                160
                                        - operationPrefix
                                        .length()
                        )
                )
                .errorCode(
                        "REMOTE_TEST_ERROR"
                )
                .message(
                        "m".repeat(500)
                )
                .timestamp(
                        ERROR_TIME
                )
                .status(500)
                .build();
    }

    private static ChainElement chainElement(
            String service,
            String component,
            String operation,
            String errorCode,
            String message,
            int status
    ) {
        return ChainElement.builder()
                .service(service)
                .component(component)
                .operation(operation)
                .errorCode(errorCode)
                .message(message)
                .timestamp(ERROR_TIME)
                .status(status)
                .build();
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