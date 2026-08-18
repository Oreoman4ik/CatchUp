package ru.oreoman4ik.catchup.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnifiedErrorExceptionTests {

    private static final Instant ERROR_TIME =
            Instant.parse(
                    "2026-07-31T10:20:30.123Z"
            );

    @Test
    void createsStructuredErrorFromOrdinaryException() {
        IllegalStateException cause =
                new IllegalStateException(
                        "Technical database message"
                );

        ErrorDetails details =
                ErrorDetails.builder()
                        .resource(
                                "COMPONENT"
                        )
                        .retryAfterSeconds(
                                15L
                        )
                        .build();

        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        cause,
                        ERROR_TIME,
                        500,
                        "INTERNAL_ERROR",
                        "Внутренняя ошибка сервиса",
                        details,
                        5
                );

        assertThat(
                exception.getErrorId()
        ).isNotNull();

        assertThat(
                exception.getTimestamp()
        ).isEqualTo(
                ERROR_TIME
        );

        assertThat(
                exception.getStatus()
        ).isEqualTo(500);

        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                "INTERNAL_ERROR"
        );

        assertThat(
                exception.getMessage()
        ).isEqualTo(
                "Внутренняя ошибка сервиса"
        );

        assertThat(
                exception.getDetails()
        ).isEqualTo(details);

        assertThat(
                exception.getOriginalCause()
        ).isSameAs(cause);

        assertThat(
                exception.getCause()
        ).isSameAs(cause);

        assertThat(
                exception.getChainElements()
        ).isEmpty();

        assertThat(
                exception.isChainLimitReached()
        ).isFalse();

        assertThat(
                exception.getChain()
                        .getMaxSize()
        ).isEqualTo(5);
    }

    @Test
    void convenienceFactoryCreatesTimestampAndPublicCode() {
        RuntimeException cause =
                new RuntimeException(
                        "Technical cause"
                );

        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        cause,
                        404,
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        5
                );

        assertThat(
                exception.getTimestamp()
        ).isNotNull();

        assertThat(
                exception.getStatus()
        ).isEqualTo(404);

        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                "COMPONENT_NOT_FOUND"
        );

        assertThat(
                exception.getMessage()
        ).isEqualTo(
                "Компонент не найден"
        );

        assertThat(
                exception.getDetails()
        ).isNull();

        assertThat(
                exception.getOriginalCause()
        ).isSameAs(cause);

        assertThat(
                exception.getChainElements()
        ).isEmpty();
    }

    @Test
    void createsErrorWithInitialContext() {
        RuntimeException cause =
                new RuntimeException(
                        "Technical cause"
                );

        ChainElement initialContext =
                element(
                        "service-b",
                        "ComponentRepository",
                        "findById",
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        404
                );

        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        cause,
                        ERROR_TIME,
                        404,
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        null,
                        initialContext,
                        5
                );

        assertThat(
                exception.getTimestamp()
        ).isEqualTo(
                ERROR_TIME
        );

        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                "COMPONENT_NOT_FOUND"
        );

        assertThat(
                exception.getChainElements()
        ).containsExactly(
                initialContext
        );
    }

    @Test
    void addsContextToSameExceptionWithoutChangingStructuredData() {
        RuntimeException cause =
                new RuntimeException(
                        "Technical cause"
                );

        ErrorDetails details =
                ErrorDetails.builder()
                        .resource(
                                "COMPONENT"
                        )
                        .build();

        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        cause,
                        ERROR_TIME,
                        404,
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        details,
                        5
                );

        UUID originalErrorId =
                exception.getErrorId();

        ChainElement context =
                element(
                        "service-b",
                        "ComponentService",
                        "findComponent",
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        404
                );

        UnifiedErrorException returned =
                exception.addContext(
                        context
                );

        assertThat(returned)
                .isSameAs(exception);

        assertThat(
                exception.getErrorId()
        ).isEqualTo(
                originalErrorId
        );

        assertThat(
                exception.getTimestamp()
        ).isEqualTo(
                ERROR_TIME
        );

        assertThat(
                exception.getStatus()
        ).isEqualTo(404);

        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                "COMPONENT_NOT_FOUND"
        );

        assertThat(
                exception.getMessage()
        ).isEqualTo(
                "Компонент не найден"
        );

        assertThat(
                exception.getDetails()
        ).isSameAs(details);

        assertThat(
                exception.getOriginalCause()
        ).isSameAs(cause);

        assertThat(
                exception.getChainElements()
        ).containsExactly(
                context
        );
    }

    @Test
    void repeatedWrappingReturnsExistingStructuredError() {
        RuntimeException cause =
                new RuntimeException(
                        "Technical cause"
                );

        ErrorDetails details =
                ErrorDetails.builder()
                        .resource(
                                "COMPONENT"
                        )
                        .build();

        UnifiedErrorException original =
                UnifiedErrorException.from(
                        cause,
                        ERROR_TIME,
                        500,
                        "INTERNAL_ERROR",
                        "Внутренняя ошибка",
                        details,
                        5
                );

        UnifiedErrorException repeated =
                UnifiedErrorException.from(
                        original,
                        400,
                        "OTHER_ERROR",
                        "Другое сообщение",
                        10
                );

        assertThat(repeated)
                .isSameAs(original);

        assertThat(
                repeated.getErrorId()
        ).isEqualTo(
                original.getErrorId()
        );

        assertThat(
                repeated.getTimestamp()
        ).isEqualTo(
                ERROR_TIME
        );

        assertThat(
                repeated.getStatus()
        ).isEqualTo(500);

        assertThat(
                repeated.getErrorCode()
        ).isEqualTo(
                "INTERNAL_ERROR"
        );

        assertThat(
                repeated.getMessage()
        ).isEqualTo(
                "Внутренняя ошибка"
        );

        assertThat(
                repeated.getDetails()
        ).isEqualTo(
                details
        );

        assertThat(
                repeated.getOriginalCause()
        ).isSameAs(cause);

        /*
         * Повторная from() не должна менять
         * первоначальный chain limit.
         */
        assertThat(
                repeated.getChain()
                        .getMaxSize()
        ).isEqualTo(5);
    }

    @Test
    void restoresAllStructuredFieldsFromRemoteResponse() {
        UUID errorId =
                UUID.fromString(
                        "7c12c42e-86ee-43b0-8324-"
                                + "9a56bf633ed4"
                );

        ErrorDetails details =
                ErrorDetails.builder()
                        .resource(
                                "COMPONENT"
                        )
                        .retryAfterSeconds(
                                15L
                        )
                        .build();

        ChainElement remoteContext =
                element(
                        "service-b",
                        "ComponentService",
                        "findComponent",
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        404
                );

        ErrorResponse remoteResponse =
                ErrorResponse.builder()
                        .errorId(errorId)
                        .timestamp(
                                ERROR_TIME
                        )
                        .status(404)
                        .message(
                                "Компонент не найден"
                        )
                        .errorCode(
                                "COMPONENT_NOT_FOUND"
                        )
                        .currentService(
                                "service-b"
                        )
                        .chain(
                                List.of(
                                        remoteContext
                                )
                        )
                        .details(details)
                        .build();

        RuntimeException httpCause =
                new RuntimeException(
                        "Remote HTTP 404"
                );

        UnifiedErrorException restored =
                UnifiedErrorException.fromResponse(
                        remoteResponse,
                        httpCause,
                        5
                );

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
        ).isEqualTo(404);

        assertThat(
                restored.getErrorCode()
        ).isEqualTo(
                "COMPONENT_NOT_FOUND"
        );

        assertThat(
                restored.getMessage()
        ).isEqualTo(
                "Компонент не найден"
        );

        assertThat(
                restored.getDetails()
        ).isEqualTo(
                details
        );

        assertThat(
                restored.getOriginalCause()
        ).isSameAs(
                httpCause
        );

        assertThat(
                restored.getCause()
        ).isSameAs(
                httpCause
        );

        assertThat(
                restored.getChainElements()
        ).containsExactly(
                remoteContext
        );

        assertThat(
                restored.getChain()
                        .getMaxSize()
        ).isEqualTo(5);
    }

    @Test
    void preservesRemoteChainAndAddsLocalContext() {
        UUID remoteErrorId =
                UUID.fromString(
                        "7c12c42e-86ee-43b0-8324-"
                                + "9a56bf633ed4"
                );

        ErrorDetails details =
                ErrorDetails.builder()
                        .resource(
                                "COMPONENT"
                        )
                        .build();

        ChainElement remoteContext =
                element(
                        "service-b",
                        "ComponentService",
                        "findComponent",
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        404
                );

        ErrorResponse remoteResponse =
                ErrorResponse.builder()
                        .errorId(
                                remoteErrorId
                        )
                        .timestamp(
                                ERROR_TIME
                        )
                        .status(404)
                        .message(
                                "Компонент не найден"
                        )
                        .errorCode(
                                "COMPONENT_NOT_FOUND"
                        )
                        .currentService(
                                "service-b"
                        )
                        .chain(
                                List.of(
                                        remoteContext
                                )
                        )
                        .details(details)
                        .build();

        RuntimeException httpCause =
                new RuntimeException(
                        "Remote HTTP 404"
                );

        UnifiedErrorException restored =
                UnifiedErrorException.fromResponse(
                        remoteResponse,
                        httpCause,
                        5
                );

        ChainElement localContext =
                element(
                        "service-a",
                        "ComponentGateway",
                        "loadComponent",
                        "UPSTREAM_ERROR",
                        "Не удалось получить компонент",
                        404
                );

        UnifiedErrorException returned =
                restored.addContext(
                        localContext
                );

        assertThat(returned)
                .isSameAs(restored);

        assertThat(
                restored.getErrorId()
        ).isEqualTo(
                remoteErrorId
        );

        assertThat(
                restored.getTimestamp()
        ).isEqualTo(
                ERROR_TIME
        );

        assertThat(
                restored.getErrorCode()
        ).isEqualTo(
                "COMPONENT_NOT_FOUND"
        );

        assertThat(
                restored.getDetails()
        ).isEqualTo(
                details
        );

        assertThat(
                restored.getOriginalCause()
        ).isSameAs(
                httpCause
        );

        assertThat(
                restored.getChainElements()
        ).containsExactly(
                remoteContext,
                localContext
        );
    }

    @Test
    void remoteChainIsTruncatedToConfiguredMaxSize() {
        ChainElement first =
                element(
                        "service-d",
                        "Repository",
                        "load",
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        404
                );

        ChainElement second =
                element(
                        "service-c",
                        "Service",
                        "process",
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        404
                );

        ChainElement third =
                element(
                        "service-b",
                        "Controller",
                        "get",
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        404
                );

        ErrorResponse remoteResponse =
                ErrorResponse.builder()
                        .errorId(
                                UUID.randomUUID()
                        )
                        .timestamp(
                                ERROR_TIME
                        )
                        .status(404)
                        .message(
                                "Компонент не найден"
                        )
                        .errorCode(
                                "COMPONENT_NOT_FOUND"
                        )
                        .currentService(
                                "service-b"
                        )
                        .chain(
                                List.of(
                                        first,
                                        second,
                                        third
                                )
                        )
                        .build();

        /*
         * Локальная настройка = 2.
         *
         * Remote chain = 3.
         *
         * После восстановления chain не может
         * содержать больше двух элементов.
         */
        UnifiedErrorException restored =
                UnifiedErrorException.fromResponse(
                        remoteResponse,
                        new RuntimeException(
                                "Remote HTTP error"
                        ),
                        2
                );

        assertThat(
                restored.getChainElements()
        ).containsExactly(
                first,
                second
        );

        assertThat(
                restored.getChainElements()
        ).hasSize(2);

        assertThat(
                restored.getChain()
                        .getMaxSize()
        ).isEqualTo(2);

        assertThat(
                restored.isChainLimitReached()
        ).isTrue();

        ChainElement localContext =
                element(
                        "service-a",
                        "Gateway",
                        "callRemote",
                        "UPSTREAM_ERROR",
                        "Не удалось получить компонент",
                        404
                );

        restored.addContext(
                localContext
        );

        /*
         * Лимит уже достигнут —
         * третий элемент не добавился.
         */
        assertThat(
                restored.getChainElements()
        ).containsExactly(
                first,
                second
        );
    }

    @Test
    void remoteRestoreCanReserveSlotForCallerContext() {
        ChainElement first =
                element(
                        "service-c",
                        "Repository",
                        "load",
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        404
                );

        ChainElement second =
                element(
                        "service-b",
                        "Service",
                        "process",
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        404
                );

        ErrorResponse remoteResponse =
                ErrorResponse.builder()
                        .errorId(
                                UUID.randomUUID()
                        )
                        .timestamp(
                                ERROR_TIME
                        )
                        .status(404)
                        .message(
                                "Компонент не найден"
                        )
                        .errorCode(
                                "COMPONENT_NOT_FOUND"
                        )
                        .currentService(
                                "service-b"
                        )
                        .chain(
                                List.of(
                                        first,
                                        second
                                )
                        )
                        .build();

        /*
         * max-chain-size = 2
         *
         * reservedChainSlots = 1
         *
         * Поэтому из remote chain можно сохранить
         * только один элемент.
         */
        UnifiedErrorException restored =
                UnifiedErrorException.fromResponse(
                        remoteResponse,
                        new RuntimeException(
                                "Remote HTTP error"
                        ),
                        2,
                        1
                );

        assertThat(
                restored.getChainElements()
        ).containsExactly(
                first
        );

        assertThat(
                restored.getChain()
                        .getMaxSize()
        ).isEqualTo(2);

        assertThat(
                restored.isChainLimitReached()
        ).isFalse();

        ChainElement caller =
                element(
                        "service-a",
                        "Gateway",
                        "callRemote",
                        "REMOTE_CLIENT_ERROR",
                        "Удалённый сервис "
                                + "отклонил запрос",
                        404
                );

        restored.addContext(
                caller
        );

        assertThat(
                restored.getChainElements()
        ).containsExactly(
                first,
                caller
        );

        assertThat(
                restored.getChainElements()
        ).hasSize(2);

        assertThat(
                restored.isChainLimitReached()
        ).isTrue();
    }

    @Test
    void reserveSlotWorksWhenMaxChainSizeIsOne() {
        ChainElement remote =
                element(
                        "service-b",
                        "Controller",
                        "get",
                        "RESOURCE_NOT_FOUND",
                        "Ресурс не найден",
                        404
                );

        ErrorResponse response =
                ErrorResponse.builder()
                        .errorId(
                                UUID.randomUUID()
                        )
                        .timestamp(
                                ERROR_TIME
                        )
                        .status(404)
                        .message(
                                "Ресурс не найден"
                        )
                        .errorCode(
                                "RESOURCE_NOT_FOUND"
                        )
                        .currentService(
                                "service-b"
                        )
                        .chain(
                                List.of(remote)
                        )
                        .build();

        UnifiedErrorException restored =
                UnifiedErrorException.fromResponse(
                        response,
                        new RuntimeException(
                                "Remote HTTP 404"
                        ),
                        1,
                        1
                );

        /*
         * Единственный slot был зарезервирован,
         * поэтому remote context не восстанавливается.
         */
        assertThat(
                restored.getChainElements()
        ).isEmpty();

        ChainElement caller =
                element(
                        "service-a",
                        "Gateway",
                        "call",
                        "REMOTE_CLIENT_ERROR",
                        "Удалённый сервис "
                                + "отклонил запрос",
                        404
                );

        restored.addContext(
                caller
        );

        assertThat(
                restored.getChainElements()
        ).containsExactly(
                caller
        );

        assertThat(
                restored.isChainLimitReached()
        ).isTrue();
    }

    @Test
    void rejectsInvalidReservedChainSlots() {
        ErrorResponse remoteResponse =
                ErrorResponse.builder()
                        .errorId(
                                UUID.randomUUID()
                        )
                        .timestamp(
                                ERROR_TIME
                        )
                        .status(404)
                        .message(
                                "Ресурс не найден"
                        )
                        .errorCode(
                                "RESOURCE_NOT_FOUND"
                        )
                        .currentService(
                                "service-b"
                        )
                        .chain(
                                List.of(
                                        element(
                                                "service-b",
                                                "Controller",
                                                "get",
                                                "RESOURCE_NOT_FOUND",
                                                "Ресурс не найден",
                                                404
                                        )
                                )
                        )
                        .build();

        RuntimeException cause =
                new RuntimeException(
                        "Remote HTTP 404"
                );

        assertThatThrownBy(
                () ->
                        UnifiedErrorException
                                .fromResponse(
                                        remoteResponse,
                                        cause,
                                        5,
                                        -1
                                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "reservedChainSlots"
                );

        assertThatThrownBy(
                () ->
                        UnifiedErrorException
                                .fromResponse(
                                        remoteResponse,
                                        cause,
                                        5,
                                        6
                                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "reservedChainSlots"
                );
    }

    @Test
    void repeatedContextDoesNotCreateDuplicateEntry() {
        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        new RuntimeException(
                                "Technical cause"
                        ),
                        ERROR_TIME,
                        404,
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        null,
                        5
                );

        ChainElement first =
                element(
                        "service-a",
                        "ComponentService",
                        "findComponent",
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        404
                );

        /*
         * Код/status/message отличаются,
         * но service + component + operation
         * совпадают.
         */
        ChainElement duplicate =
                ChainElement.builder()
                        .service(
                                "service-a"
                        )
                        .component(
                                "ComponentService"
                        )
                        .operation(
                                "findComponent"
                        )
                        .errorCode(
                                "UPSTREAM_ERROR"
                        )
                        .message(
                                "Повторная обработка"
                        )
                        .timestamp(
                                ERROR_TIME
                                        .plusSeconds(1)
                        )
                        .status(500)
                        .build();

        exception.addContext(first);
        exception.addContext(duplicate);

        assertThat(
                exception.getChainElements()
        ).containsExactly(
                first
        );
    }

    @Test
    void stopsAddingContextsAfterConfiguredLimit() {
        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        new RuntimeException(
                                "Technical cause"
                        ),
                        ERROR_TIME,
                        500,
                        "INTERNAL_ERROR",
                        "Внутренняя ошибка",
                        null,
                        2
                );

        ChainElement first =
                element(
                        "service-b",
                        "Repository",
                        "load",
                        "INTERNAL_ERROR",
                        "Внутренняя ошибка",
                        500
                );

        ChainElement second =
                element(
                        "service-b",
                        "Service",
                        "process",
                        "INTERNAL_ERROR",
                        "Внутренняя ошибка",
                        500
                );

        ChainElement third =
                element(
                        "service-a",
                        "Controller",
                        "handle",
                        "INTERNAL_ERROR",
                        "Внутренняя ошибка",
                        500
                );

        exception.addContext(first);
        exception.addContext(second);
        exception.addContext(third);

        assertThat(
                exception.isChainLimitReached()
        ).isTrue();

        assertThat(
                exception.getChainElements()
        ).containsExactly(
                first,
                second
        );

        assertThat(
                exception.getChainElements()
        ).hasSize(2);
    }

    @Test
    void convertsExceptionBackToResponseWithoutDataLoss() {
        ErrorDetails details =
                ErrorDetails.builder()
                        .resource(
                                "COMPONENT"
                        )
                        .retryAfterSeconds(
                                30L
                        )
                        .build();

        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        new RuntimeException(
                                "Technical cause"
                        ),
                        ERROR_TIME,
                        404,
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        details,
                        5
                );

        UUID errorId =
                exception.getErrorId();

        ChainElement context =
                element(
                        "service-a",
                        "ComponentController",
                        "getComponent",
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        404
                );

        exception.addContext(
                context
        );

        ErrorResponse response =
                exception.toResponse(
                        "service-a"
                );

        assertThat(
                response.getErrorId()
        ).isEqualTo(
                errorId
        );

        assertThat(
                response.getTimestamp()
        ).isEqualTo(
                ERROR_TIME
        );

        assertThat(
                response.getStatus()
        ).isEqualTo(404);

        assertThat(
                response.getErrorCode()
        ).isEqualTo(
                "COMPONENT_NOT_FOUND"
        );

        assertThat(
                response.getMessage()
        ).isEqualTo(
                "Компонент не найден"
        );

        assertThat(
                response.getCurrentService()
        ).isEqualTo(
                "service-a"
        );

        assertThat(
                response.getDetails()
        ).isEqualTo(
                details
        );

        assertThat(
                response.getChain()
        ).containsExactly(
                context
        );
    }

    @Test
    void remoteResponseRoundTripPreservesStructuredData() {
        UUID errorId =
                UUID.fromString(
                        "7c12c42e-86ee-43b0-8324-"
                                + "9a56bf633ed4"
                );

        ErrorDetails details =
                ErrorDetails.builder()
                        .resource(
                                "COMPONENT"
                        )
                        .violations(
                                List.of(
                                        ErrorDetails
                                                .FieldViolation
                                                .of(
                                                        "id",
                                                        "INVALID",
                                                        "Некорректный "
                                                                + "идентификатор"
                                                )
                                )
                        )
                        .build();

        ChainElement remoteContext =
                element(
                        "service-b",
                        "ComponentController",
                        "getComponent",
                        "VALIDATION_ERROR",
                        "Некорректный запрос",
                        400
                );

        ErrorResponse originalResponse =
                ErrorResponse.builder()
                        .errorId(errorId)
                        .timestamp(
                                ERROR_TIME
                        )
                        .status(400)
                        .message(
                                "Некорректный запрос"
                        )
                        .errorCode(
                                "VALIDATION_ERROR"
                        )
                        .currentService(
                                "service-b"
                        )
                        .chain(
                                List.of(
                                        remoteContext
                                )
                        )
                        .details(details)
                        .build();

        UnifiedErrorException exception =
                UnifiedErrorException.fromResponse(
                        originalResponse,
                        new RuntimeException(
                                "Remote HTTP 400"
                        ),
                        5
                );

        ErrorResponse restoredResponse =
                exception.toResponse(
                        "service-a"
                );

        assertThat(
                restoredResponse.getErrorId()
        ).isEqualTo(
                originalResponse.getErrorId()
        );

        assertThat(
                restoredResponse.getTimestamp()
        ).isEqualTo(
                originalResponse.getTimestamp()
        );

        assertThat(
                restoredResponse.getStatus()
        ).isEqualTo(
                originalResponse.getStatus()
        );

        assertThat(
                restoredResponse.getMessage()
        ).isEqualTo(
                originalResponse.getMessage()
        );

        assertThat(
                restoredResponse.getErrorCode()
        ).isEqualTo(
                originalResponse.getErrorCode()
        );

        assertThat(
                restoredResponse.getDetails()
        ).isEqualTo(
                originalResponse.getDetails()
        );

        assertThat(
                restoredResponse.getChain()
        ).isEqualTo(
                originalResponse.getChain()
        );

        assertThat(
                restoredResponse.getCurrentService()
        ).isEqualTo(
                "service-a"
        );
    }

    @Test
    void toResponseRejectsEmptyChain() {
        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        new RuntimeException(
                                "Technical cause"
                        ),
                        ERROR_TIME,
                        500,
                        "INTERNAL_ERROR",
                        "Внутренняя ошибка",
                        null,
                        5
                );

        assertThatThrownBy(
                () ->
                        exception.toResponse(
                                "service-a"
                        )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        "chain must contain at least one element"
                );
    }

    @Test
    void rejectsInvalidStatusAndInvalidErrorCode() {
        RuntimeException cause =
                new RuntimeException(
                        "Technical cause"
                );

        assertThatThrownBy(
                () ->
                        UnifiedErrorException.from(
                                cause,
                                ERROR_TIME,
                                200,
                                "INTERNAL_ERROR",
                                "Ошибка",
                                null,
                                5
                        )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "400 to 599"
                );

        assertThatThrownBy(
                () ->
                        UnifiedErrorException.from(
                                cause,
                                ERROR_TIME,
                                500,
                                "NULLPOINTEREXCEPTION",
                                "Ошибка",
                                null,
                                5
                        )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "Java exception type"
                );
    }

    private static ChainElement element(
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
}