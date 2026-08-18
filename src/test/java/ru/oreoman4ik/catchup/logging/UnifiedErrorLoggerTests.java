package ru.oreoman4ik.catchup.logging;

import org.junit.jupiter.api.Test;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedErrorLoggerTests {

    @Test
    void logs500OnceWithSameErrorIdAndOriginalCause() {
        RecordingSink sink =
                new RecordingSink();

        UnifiedErrorLogger logger =
                new UnifiedErrorLogger(
                        sink
                );

        IllegalStateException cause =
                new IllegalStateException(
                        "password=secret"
                );

        UnifiedErrorException error =
                UnifiedErrorException.from(
                        cause,
                        500,
                        "INTERNAL_ERROR",
                        "Внутренняя ошибка сервиса",
                        5
                );

        error.addContext(
                ChainElement.builder()
                        .service("orders-service")
                        .component("OrderService")
                        .operation("createOrder")
                        .errorCode(
                                "INTERNAL_ERROR"
                        )
                        .message(
                                "Внутренняя ошибка сервиса"
                        )
                        .timestamp(
                                Instant.now()
                        )
                        .status(500)
                        .build()
        );

        logger.log(
                error,
                "orders-service"
        );

        logger.log(
                error,
                "orders-service"
        );

        assertThat(sink.calls)
                .isEqualTo(1);

        assertThat(sink.level)
                .isEqualTo(
                        ErrorLogLevel.ERROR
                );

        assertThat(sink.throwable)
                .isSameAs(cause);

        assertThat(sink.message)
                .contains(
                        error.getErrorId()
                                .toString()
                )
                .contains(
                        "service=orders-service"
                )
                .contains(
                        "operation=createOrder"
                )
                .contains(
                        "status=500"
                )
                .contains(
                        "OrderService"
                );
    }

    @Test
    void logs4xxAtWarnLevel() {
        RecordingSink sink =
                new RecordingSink();

        UnifiedErrorLogger logger =
                new UnifiedErrorLogger(
                        sink
                );

        UnifiedErrorException error =
                UnifiedErrorException.from(
                        new IllegalArgumentException(
                                "technical"
                        ),
                        404,
                        "RESOURCE_NOT_FOUND",
                        "Ресурс не найден",
                        5
                );

        error.addContext(
                ChainElement.builder()
                        .service("catalog-service")
                        .component("Catalog")
                        .operation("find")
                        .errorCode(
                                "RESOURCE_NOT_FOUND"
                        )
                        .message(
                                "Ресурс не найден"
                        )
                        .timestamp(
                                Instant.now()
                        )
                        .status(404)
                        .build()
        );

        logger.log(
                error,
                "catalog-service"
        );

        assertThat(sink.level)
                .isEqualTo(
                        ErrorLogLevel.WARN
                );
    }

    private static final class
    RecordingSink
            implements ErrorLogSink {

        int calls;

        ErrorLogLevel level;

        String message;

        Throwable throwable;

        @Override
        public void write(
                ErrorLogLevel level,
                String message,
                Throwable throwable
        ) {
            calls++;

            this.level = level;
            this.message = message;
            this.throwable = throwable;
        }
    }
}