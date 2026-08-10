package ru.oreoman4ik.catchup.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.web.client.ResourceAccessException;
import ru.oreoman4ik.catchup.annotation.ErrorContext;
import ru.oreoman4ik.catchup.client.OutgoingHttpExceptionMapper;
import ru.oreoman4ik.catchup.model.BusinessException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;

import java.net.ConnectException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ErrorContextAspectTests {

    private ErrorContextAspect aspect;

    @BeforeEach
    void setUp() {
        OutgoingHttpExceptionMapper httpMapper =
                new OutgoingHttpExceptionMapper(
                        "application-service",
                        5
                );

        aspect = new ErrorContextAspect(
                "application-service",
                5,
                httpMapper
        );
    }

    @Test
    void successfulMethodKeepsOriginalBehavior() {
        TestService proxy =
                proxy(new TestService());

        String result =
                proxy.success("value");

        assertThat(result)
                .isEqualTo("result:value");
    }

    @Test
    void ordinaryExceptionBecomesStructuredAndGetsMethodContext() {
        TestService proxy =
                proxy(new TestService());

        UnifiedErrorException exception =
                catchThrowableOfType(
                        proxy::ordinaryFailure,
                        UnifiedErrorException.class
                );

        assertThat(exception).isNotNull();

        assertThat(exception.getErrorId())
                .isNotNull();

        assertThat(exception.getStatus())
                .isEqualTo(500);

        assertThat(exception.getErrorCode())
                .isEqualTo("INTERNAL_ERROR");

        assertThat(exception.getMessage())
                .isEqualTo(
                        "Не удалось выполнить операцию"
                );

        assertThat(exception.getOriginalCause())
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "jdbc:postgresql://internal-db "
                                + "token=secret"
                );

        assertThat(exception.getChainElements())
                .hasSize(1);

        ChainElement context =
                exception
                        .getChainElements()
                        .getFirst();

        assertThat(context.getService())
                .isEqualTo("catalog-service");

        assertThat(context.getComponent())
                .isEqualTo("TestService");

        assertThat(context.getOperation())
                .isEqualTo("loadComponent");

        assertThat(context.getMessage())
                .isEqualTo(
                        "Не удалось выполнить операцию"
                );

        String publicResponse =
                exception
                        .toResponse("catalog-service")
                        .toString();

        assertThat(publicResponse)
                .doesNotContain("jdbc:postgresql")
                .doesNotContain("token=secret");
    }

    @Test
    void existingStructuredErrorKeepsIdCauseAndChain() {
        TestService proxy =
                proxy(new TestService());

        IllegalArgumentException originalCause =
                new IllegalArgumentException(
                        "technical details"
                );

        ChainElement origin =
                ChainElement.builder()
                        .service("database-service")
                        .component("Repository")
                        .operation("findById")
                        .errorCode(
                                "COMPONENT_NOT_FOUND"
                        )
                        .message(
                                "Компонент не найден"
                        )
                        .timestamp(
                                Instant.parse(
                                        "2026-08-10T08:00:00Z"
                                )
                        )
                        .status(404)
                        .build();

        UnifiedErrorException existing =
                UnifiedErrorException.from(
                        originalCause,
                        Instant.parse(
                                "2026-08-10T08:00:00Z"
                        ),
                        404,
                        "COMPONENT_NOT_FOUND",
                        "Компонент не найден",
                        null,
                        origin,
                        5
                );

        UUID errorId =
                existing.getErrorId();

        UnifiedErrorException thrown =
                catchThrowableOfType(
                        () -> proxy.rethrow(existing),
                        UnifiedErrorException.class
                );

        assertThat(thrown)
                .isSameAs(existing);

        assertThat(thrown.getErrorId())
                .isEqualTo(errorId);

        assertThat(thrown.getOriginalCause())
                .isSameAs(originalCause);

        assertThat(thrown.getMessage())
                .isEqualTo(
                        "Компонент не найден"
                );

        assertThat(thrown.getChainElements())
                .hasSize(2);

        assertThat(
                thrown.getChainElements()
                        .getFirst()
        ).isEqualTo(origin);

        ChainElement added =
                thrown
                        .getChainElements()
                        .getLast();

        assertThat(added.getService())
                .isEqualTo(
                        "application-service"
                );

        assertThat(added.getOperation())
                .isEqualTo("rethrow");

        assertThat(added.getMessage())
                .isEqualTo(
                        "Ошибка верхнего уровня"
                );
    }

    @Test
    void businessExceptionKeepsBusinessStatusCodeAndDetails() {
        TestService proxy =
                proxy(new TestService());

        UnifiedErrorException thrown =
                catchThrowableOfType(
                        proxy::businessFailure,
                        UnifiedErrorException.class
                );

        assertThat(thrown.getStatus())
                .isEqualTo(409);

        assertThat(thrown.getErrorCode())
                .isEqualTo(
                        "BOOK_ALREADY_EXISTS"
                );

        assertThat(thrown.getMessage())
                .isEqualTo(
                        "Не удалось сохранить книгу"
                );

        assertThat(thrown.getDetails())
                .isEqualTo(
                        ErrorDetails.builder()
                                .resource("BOOK")
                                .build()
                );

        assertThat(thrown.getOriginalCause())
                .isInstanceOf(
                        BusinessException.class
                );
    }

    @Test
    void outgoingHttpFailureUsesHttpCategoryAndMethodContext() {
        TestService proxy =
                proxy(new TestService());

        UnifiedErrorException thrown =
                catchThrowableOfType(
                        proxy::remoteFailure,
                        UnifiedErrorException.class
                );

        assertThat(thrown.getStatus())
                .isEqualTo(503);

        assertThat(thrown.getErrorCode())
                .isEqualTo(
                        "REMOTE_UNAVAILABLE"
                );

        assertThat(thrown.getMessage())
                .isEqualTo(
                        "Каталог временно недоступен"
                );

        assertThat(thrown.getOriginalCause())
                .isInstanceOf(
                        ResourceAccessException.class
                );

        ChainElement context =
                thrown
                        .getChainElements()
                        .getFirst();

        assertThat(context.getService())
                .isEqualTo("gateway-service");

        assertThat(context.getOperation())
                .isEqualTo("requestCatalog");

        assertThat(context.getErrorCode())
                .isEqualTo(
                        "REMOTE_UNAVAILABLE"
                );
    }

    private <T> T proxy(T target) {
        AspectJProxyFactory factory =
                new AspectJProxyFactory(target);

        factory.addAspect(aspect);

        @SuppressWarnings("unchecked")
        T proxy = (T) factory.getProxy();

        return proxy;
    }

    static class TestService {

        @ErrorContext(
                service = "catalog-service",
                operation = "successfulOperation"
        )
        public String success(String value) {
            return "result:" + value;
        }

        @ErrorContext(
                service = "catalog-service",
                operation = "loadComponent",
                message =
                        "Не удалось выполнить операцию"
        )
        public void ordinaryFailure() {
            throw new IllegalStateException(
                    "jdbc:postgresql://internal-db "
                            + "token=secret"
            );
        }

        @ErrorContext(
                message = "Ошибка верхнего уровня"
        )
        public void rethrow(
                UnifiedErrorException exception
        ) {
            throw exception;
        }

        @ErrorContext(
                service = "book-service",
                operation = "saveBook",
                message =
                        "Не удалось сохранить книгу"
        )
        public void businessFailure() {
            throw new BusinessException(
                    409,
                    "BOOK_ALREADY_EXISTS",
                    "Книга уже существует",
                    ErrorDetails.builder()
                            .resource("BOOK")
                            .build()
            );
        }

        @ErrorContext(
                service = "gateway-service",
                operation = "requestCatalog",
                message =
                        "Каталог временно недоступен"
        )
        public void remoteFailure() {
            throw new ResourceAccessException(
                    "GET http://internal-host"
                            + "?token=secret",
                    new ConnectException(
                            "Connection refused"
                    )
            );
        }
    }
}