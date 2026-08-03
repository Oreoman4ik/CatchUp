package ru.oreoman4ik.catchup.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedErrorExceptionTests {

    private static final Instant ERROR_TIME = Instant.parse(
            "2026-07-31T10:20:30.123Z"
    );

    @Test
    void createsStructuredErrorFromOrdinaryException() {
        IllegalStateException cause =
                new IllegalStateException(
                        "Technical database message"
                );

        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        cause,
                        500,
                        "Внутренняя ошибка сервиса",
                        5
                );

        assertThat(exception.getErrorId())
                .isNotNull();

        assertThat(exception.getHttpStatus())
                .isEqualTo(500);

        assertThat(exception.getPublicMessage())
                .isEqualTo(
                        "Внутренняя ошибка сервиса"
                );

        assertThat(exception.getOriginalCause())
                .isSameAs(cause);

        assertThat(exception.getCause())
                .isSameAs(cause);

        assertThat(exception.getChainElements())
                .isEmpty();
    }

    @Test
    void addsContextToSameExceptionWithoutChangingIdentity() {
        RuntimeException cause =
                new RuntimeException("Technical cause");

        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        cause,
                        404,
                        "Компонент не найден",
                        5
                );

        UUID originalErrorId =
                exception.getErrorId();

        ChainElement context = element(
                "service-b",
                "ComponentService",
                "findComponent",
                "COMPONENT_NOT_FOUND"
        );

        UnifiedErrorException returned =
                exception.addContext(context);

        assertThat(returned).isSameAs(exception);

        assertThat(exception.getErrorId())
                .isEqualTo(originalErrorId);

        assertThat(exception.getOriginalCause())
                .isSameAs(cause);

        assertThat(exception.getChainElements())
                .containsExactly(context);
    }

    @Test
    void repeatedWrappingReturnsExistingStructuredError() {
        RuntimeException cause =
                new RuntimeException("Technical cause");

        UnifiedErrorException original =
                UnifiedErrorException.from(
                        cause,
                        500,
                        "Внутренняя ошибка",
                        5
                );

        UnifiedErrorException repeated =
                UnifiedErrorException.from(
                        original,
                        400,
                        "Другое сообщение",
                        10
                );

        assertThat(repeated).isSameAs(original);

        assertThat(repeated.getErrorId())
                .isEqualTo(original.getErrorId());

        assertThat(repeated.getOriginalCause())
                .isSameAs(cause);
    }

    @Test
    void restoresErrorFromRemoteResponseAndPreservesChain() {
        UUID remoteErrorId = UUID.fromString(
                "7c12c42e-86ee-43b0-8324-9a56bf633ed4"
        );

        ChainElement remoteContext = element(
                "service-b",
                "ComponentService",
                "findComponent",
                "COMPONENT_NOT_FOUND"
        );

        ErrorResponse remoteResponse =
                ErrorResponse.builder()
                        .errorId(remoteErrorId)
                        .timestamp(ERROR_TIME)
                        .httpStatus(404)
                        .publicMessage(
                                "Компонент не найден"
                        )
                        .errorCode(
                                "COMPONENT_NOT_FOUND"
                        )
                        .currentService("service-b")
                        .chain(
                                List.of(remoteContext)
                        )
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

        assertThat(restored.getErrorId())
                .isEqualTo(remoteErrorId);

        assertThat(restored.getHttpStatus())
                .isEqualTo(404);

        assertThat(restored.getPublicMessage())
                .isEqualTo(
                        "Компонент не найден"
                );

        assertThat(restored.getOriginalCause())
                .isSameAs(httpCause);

        assertThat(restored.getChainElements())
                .containsExactly(remoteContext);

        ChainElement localContext = element(
                "service-a",
                "ComponentGateway",
                "loadComponent",
                "UPSTREAM_ERROR"
        );

        restored.addContext(localContext);

        assertThat(restored.getErrorId())
                .isEqualTo(remoteErrorId);

        assertThat(restored.getChainElements())
                .containsExactly(
                        remoteContext,
                        localContext
                );
    }

    @Test
    void remoteChainLongerThanLocalLimitIsPreservedButNotExpanded() {
        ChainElement first = element(
                "service-c",
                "Repository",
                "load",
                "COMPONENT_NOT_FOUND"
        );

        ChainElement second = element(
                "service-b",
                "Service",
                "find",
                "COMPONENT_NOT_FOUND"
        );

        ChainElement third = element(
                "service-b",
                "Controller",
                "get",
                "COMPONENT_NOT_FOUND"
        );

        ErrorResponse remoteResponse =
                ErrorResponse.builder()
                        .errorId(UUID.randomUUID())
                        .timestamp(ERROR_TIME)
                        .httpStatus(404)
                        .publicMessage(
                                "Компонент не найден"
                        )
                        .errorCode(
                                "COMPONENT_NOT_FOUND"
                        )
                        .currentService("service-b")
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
                        remoteResponse,
                        new RuntimeException(
                                "Remote HTTP error"
                        ),
                        2
                );

        assertThat(restored.getChainElements())
                .containsExactly(
                        first,
                        second,
                        third
                );

        assertThat(restored.isChainLimitReached())
                .isTrue();

        restored.addContext(
                element(
                        "service-a",
                        "Gateway",
                        "callRemote",
                        "UPSTREAM_ERROR"
                )
        );

        assertThat(restored.getChainElements())
                .containsExactly(
                        first,
                        second,
                        third
                );
    }

    @Test
    void repeatedContextDoesNotCreateDuplicateEntry() {
        UnifiedErrorException exception =
                UnifiedErrorException.from(
                        new RuntimeException(
                                "Technical cause"
                        ),
                        404,
                        "Компонент не найден",
                        5
                );

        ChainElement first = element(
                "service-a",
                "ComponentService",
                "findComponent",
                "COMPONENT_NOT_FOUND"
        );

        ChainElement duplicate =
                ChainElement.builder()
                        .service("service-a")
                        .component("ComponentService")
                        .operation("findComponent")
                        .causeCode("UPSTREAM_ERROR")
                        .publicMessage(
                                "Повторная обработка"
                        )
                        .timestamp(
                                ERROR_TIME.plusSeconds(1)
                        )
                        .httpStatus(500)
                        .build();

        exception.addContext(first);
        exception.addContext(duplicate);

        assertThat(exception.getChainElements())
                .containsExactly(first);
    }

    private static ChainElement element(
            String service,
            String component,
            String operation,
            String errorCode
    ) {
        return ChainElement.builder()
                .service(service)
                .component(component)
                .operation(operation)
                .causeCode(errorCode)
                .publicMessage("Безопасное сообщение")
                .timestamp(ERROR_TIME)
                .httpStatus(404)
                .build();
    }
}