package ru.oreoman4ik.catchup.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.oreoman4ik.catchup.client.OutgoingHttpExceptionMapper;
import ru.oreoman4ik.catchup.client.RemoteErrorResponseDecoder;
import ru.oreoman4ik.catchup.config.CurrentServiceName;
import ru.oreoman4ik.catchup.config.TechnicalDetailsFactory;
import ru.oreoman4ik.catchup.config.UnifiedErrorProperties;
import ru.oreoman4ik.catchup.logging.ErrorLogLevel;
import ru.oreoman4ik.catchup.logging.ErrorLogSink;
import ru.oreoman4ik.catchup.logging.UnifiedErrorLogger;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedGlobalExceptionHandlerLoggingTests {

    private JsonMapper jsonMapper;

    private RecordingSink sink;

    private UnifiedGlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        jsonMapper =
                JsonMapper.builder().build();

        UnifiedErrorProperties properties =
                new UnifiedErrorProperties();

        CurrentServiceName service =
                CurrentServiceName.of(
                        "test-service"
                );

        TechnicalDetailsFactory detailsFactory =
                new TechnicalDetailsFactory(
                        properties
                );

        RemoteErrorResponseDecoder decoder =
                new RemoteErrorResponseDecoder(
                        jsonMapper,
                        properties
                );

        OutgoingHttpExceptionMapper mapper =
                new OutgoingHttpExceptionMapper(
                        service,
                        properties,
                        decoder,
                        detailsFactory
                );

        sink = new RecordingSink();

        handler =
                new UnifiedGlobalExceptionHandler(
                        service,
                        properties,
                        mapper,
                        detailsFactory,
                        new UnifiedErrorLogger(
                                sink
                        )
                );
    }

    @Test
    void responseErrorIdMatchesLogErrorId() {
        var response =
                handler.handleUnexpectedError(
                        new IllegalStateException(
                                "database password=secret"
                        ),
                        request()
                );

        ErrorResponse body =
                response.getBody();

        assertThat(body)
                .isNotNull();

        assertThat(sink.calls)
                .isEqualTo(1);

        assertThat(sink.message)
                .contains(
                        body.getErrorId()
                                .toString()
                );
    }

    @Test
    void secretRemainsInThrowableButNotPublicResponse() {
        IllegalStateException cause =
                new IllegalStateException(
                        "jdbc://internal-db "
                                + "password=secret"
                );

        var response =
                handler.handleUnexpectedError(
                        cause,
                        request()
                );

        String json =
                jsonMapper.writeValueAsString(
                        response.getBody()
                );

        assertThat(json)
                .doesNotContain(
                        "internal-db"
                )
                .doesNotContain(
                        "password=secret"
                );

        assertThat(sink.throwable)
                .isSameAs(cause);

        assertThat(
                sink.throwable.getMessage()
        ).contains(
                "password=secret"
        );
    }

    @Test
    void sameStructuredErrorIsNotLoggedTwice() {
        UnifiedErrorException error =
                UnifiedErrorException.from(
                        new IllegalStateException(
                                "technical"
                        ),
                        500,
                        "INTERNAL_ERROR",
                        "Внутренняя ошибка сервиса",
                        5
                );

        handler.handleUnifiedError(
                error,
                request()
        );

        handler.handleUnifiedError(
                error,
                request()
        );

        assertThat(sink.calls)
                .isEqualTo(1);
    }

    @Test
    void unexpected5xxIsLoggedAtErrorLevel() {
        handler.handleUnexpectedError(
                new IllegalStateException(
                        "technical"
                ),
                request()
        );

        assertThat(sink.level)
                .isEqualTo(
                        ErrorLogLevel.ERROR
                );
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest(
                "GET",
                "/test"
        );
    }

    private static final class RecordingSink
            implements ErrorLogSink {

        private int calls;

        private ErrorLogLevel level;

        private String message;

        private Throwable throwable;

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