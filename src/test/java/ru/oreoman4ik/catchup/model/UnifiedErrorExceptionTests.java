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
                        "secret database details"
                );

        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        cause,
                        ERROR_TIME,
                        500,
                        "INTERNAL_ERROR",
                        "Внутренняя ошибка сервиса",
                        null,
                        5
                );

        assertThat(exception.getErrorId())
                .isNotNull();

        assertThat(exception.getTimestamp())
                .isEqualTo(ERROR_TIME);

        assertThat(exception.getStatus())
                .isEqualTo(500);

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        "INTERNAL_ERROR"
                );

        assertThat(exception.getMessage())
                .isEqualTo(
                        "Внутренняя ошибка сервиса"
                );

        assertThat(exception.getOriginalCause())
                .isSameAs(cause);

        assertThat(exception.getCause())
                .isSameAs(cause);

        assertThat(exception.getChainElements())
                .isEmpty();

        assertThat(exception.isMessageTruncated())
                .isFalse();
    }

    @Test
    void repeatedFromReturnsSameStructuredException() {
        RuntimeException originalCause =
                new RuntimeException(
                        "technical"
                );

        UnifiedErrorException existing =
                UnifiedErrorException.from(
                        originalCause,
                        500,
                        "INTERNAL_ERROR",
                        "Ошибка",
                        5
                );

        UnifiedErrorException repeated =
                UnifiedErrorException.from(
                        existing,
                        400,
                        "OTHER_ERROR",
                        "Другая ошибка",
                        20
                );

        assertThat(repeated)
                .isSameAs(existing);

        assertThat(
                repeated.getOriginalCause()
        ).isSameAs(
                originalCause
        );

        assertThat(
                repeated.getChain()
                        .getMaxSize()
        ).isEqualTo(5);
    }

    @Test
    void longPublicMessageIsTruncated() {
        String longMessage =
                "x".repeat(900);

        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        new RuntimeException(
                                "technical"
                        ),
                        500,
                        "INTERNAL_ERROR",
                        longMessage,
                        5
                );

        assertThat(exception.getMessage())
                .hasSize(500);

        assertThat(exception.isMessageTruncated())
                .isTrue();

        exception.addContext(
                element(
                        "service-a",
                        "Controller",
                        "call",
                        exception.getMessage(),
                        500,
                        "INTERNAL_ERROR"
                )
        );

        ErrorResponse response =
                exception.toResponse(
                        "service-a"
                );

        assertThat(response.getMessage())
                .hasSize(500);

        assertThat(response.getDetails())
                .isNotNull();

        assertThat(
                response.getDetails()
                        .getTruncation()
        ).isNotNull();

        assertThat(
                response.getDetails()
                        .getTruncation()
                        .isMessage()
        ).isTrue();
    }

    @Test
    void restoresRemoteIdentity() {
        UUID errorId =
                UUID.fromString(
                        "7c12c42e-86ee-43b0-8324-9a56bf633ed4"
                );

        ChainElement origin =
                element(
                        "inventory-service",
                        "Repository",
                        "findItem",
                        "Товар не найден",
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

        RuntimeException cause =
                new RuntimeException(
                        "HTTP 404"
                );

        UnifiedErrorException restored =
                UnifiedErrorException.fromResponse(
                        remote,
                        cause,
                        5
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
                .containsExactly(origin);

        assertThat(restored.getOriginalCause())
                .isSameAs(cause);
    }

    @Test
    void remoteChainNeverExceedsLocalMaximum() {
        ChainElement first =
                element(
                        "service-d",
                        "D",
                        "call",
                        "Ошибка",
                        500,
                        "REMOTE_ERROR"
                );

        ChainElement second =
                element(
                        "service-c",
                        "C",
                        "call",
                        "Ошибка",
                        500,
                        "REMOTE_ERROR"
                );

        ChainElement third =
                element(
                        "service-b",
                        "B",
                        "call",
                        "Ошибка",
                        500,
                        "REMOTE_ERROR"
                );

        ErrorResponse remote =
                ErrorResponse.builder()
                        .errorId(
                                UUID.randomUUID()
                        )
                        .timestamp(ERROR_TIME)
                        .status(500)
                        .message("Ошибка")
                        .errorCode(
                                "REMOTE_ERROR"
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

        UnifiedErrorException restored =
                UnifiedErrorException.fromResponse(
                        remote,
                        new RuntimeException(
                                "HTTP 500"
                        ),
                        2
                );

        assertThat(restored.getChainElements())
                .containsExactly(
                        first,
                        second
                );

        assertThat(
                restored.getChain()
                        .getMaxSize()
        ).isEqualTo(2);

        assertThat(restored.isChainTruncated())
                .isTrue();
    }

    @Test
    void remoteRestoreCanReserveCallerSlot() {
        ChainElement origin =
                element(
                        "service-c",
                        "Repository",
                        "load",
                        "Ошибка",
                        500,
                        "REMOTE_ERROR"
                );

        ChainElement intermediate =
                element(
                        "service-b",
                        "Service",
                        "process",
                        "Ошибка",
                        500,
                        "REMOTE_ERROR"
                );

        ErrorResponse remote =
                ErrorResponse.builder()
                        .errorId(
                                UUID.randomUUID()
                        )
                        .timestamp(ERROR_TIME)
                        .status(500)
                        .message("Ошибка")
                        .errorCode(
                                "REMOTE_ERROR"
                        )
                        .currentService(
                                "service-b"
                        )
                        .chain(
                                List.of(
                                        origin,
                                        intermediate
                                )
                        )
                        .build();

        UnifiedErrorException restored =
                UnifiedErrorException.fromResponse(
                        remote,
                        new RuntimeException(
                                "HTTP 500"
                        ),
                        2,
                        1
                );

        ChainElement caller =
                element(
                        "service-a",
                        "Gateway",
                        "callRemote",
                        "Удалённая ошибка",
                        500,
                        "REMOTE_SERVER_ERROR"
                );

        restored.addContext(caller);

        assertThat(restored.getChainElements())
                .containsExactly(
                        origin,
                        caller
                );

        assertThat(restored.getChainElements())
                .hasSize(2);

        assertThat(restored.isChainTruncated())
                .isTrue();

        ErrorResponse response =
                restored.toResponse(
                        "service-a"
                );

        assertThat(
                response.getDetails()
                        .getTruncation()
                        .isChain()
        ).isTrue();
    }

    @Test
    void preservesExistingDetailsWhenAddingTruncation() {
        ErrorDetails details =
                ErrorDetails.builder()
                        .resource("ITEM")
                        .build();

        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        new RuntimeException(
                                "technical"
                        ),
                        ERROR_TIME,
                        500,
                        "INTERNAL_ERROR",
                        "Ошибка",
                        details,
                        1
                );

        exception.addContext(
                element(
                        "service-a",
                        "Service",
                        "first",
                        "Ошибка",
                        500,
                        "INTERNAL_ERROR"
                )
        );

        exception.addContext(
                element(
                        "service-b",
                        "Service",
                        "second",
                        "Ошибка",
                        500,
                        "INTERNAL_ERROR"
                )
        );

        ErrorResponse response =
                exception.toResponse(
                        "service-a"
                );

        assertThat(
                response.getDetails()
                        .getResource()
        ).isEqualTo("ITEM");

        assertThat(
                response.getDetails()
                        .getTruncation()
                        .isChain()
        ).isTrue();
    }

    @Test
    void canOnlyBeMarkedLoggedOnce() {
        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        new RuntimeException(
                                "technical"
                        ),
                        500,
                        "INTERNAL_ERROR",
                        "Ошибка",
                        5
                );

        assertThat(
                exception.tryMarkLogged()
        ).isTrue();

        assertThat(
                exception.tryMarkLogged()
        ).isFalse();

        assertThat(
                exception.tryMarkLogged()
        ).isFalse();
    }

    @Test
    void toResponseRequiresContext() {
        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        new RuntimeException(
                                "technical"
                        ),
                        500,
                        "INTERNAL_ERROR",
                        "Ошибка",
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
    void rejectsInvalidReservedSlots() {
        ErrorResponse remote =
                ErrorResponse.builder()
                        .errorId(
                                UUID.randomUUID()
                        )
                        .timestamp(ERROR_TIME)
                        .status(500)
                        .message("Ошибка")
                        .errorCode(
                                "REMOTE_ERROR"
                        )
                        .currentService(
                                "service-b"
                        )
                        .chain(
                                List.of(
                                        element(
                                                "service-b",
                                                "Service",
                                                "call",
                                                "Ошибка",
                                                500,
                                                "REMOTE_ERROR"
                                        )
                                )
                        )
                        .build();

        assertThatThrownBy(
                () ->
                        UnifiedErrorException
                                .fromResponse(
                                        remote,
                                        new RuntimeException(),
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

    private static ChainElement element(
            String service,
            String component,
            String operation,
            String message,
            int status,
            String errorCode
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