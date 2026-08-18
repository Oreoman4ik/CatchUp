package example.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import ru.oreoman4ik.catchup.client.OutgoingHttpExceptionMapper;
import ru.oreoman4ik.catchup.client.RemoteErrorResponseDecoder;
import ru.oreoman4ik.catchup.logging.ErrorLogLevel;
import ru.oreoman4ik.catchup.logging.ErrorLogSink;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes =
                StarterUserOverrideIntegrationTests
                        .TestApplication.class,
        properties = {
                "spring.application.name=test-service"
        }
)
class StarterUserOverrideIntegrationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void userMapperPreventsDefaultMapperCreation() {
        assertThat(
                context.getBeanNamesForType(
                        OutgoingHttpExceptionMapper.class
                )
        ).containsExactly(
                "customOutgoingHttpExceptionMapper"
        );

        assertThat(
                context.getBean(
                        OutgoingHttpExceptionMapper.class
                )
        ).isSameAs(
                TestApplication.CUSTOM_MAPPER
        );
    }

    @Test
    void userDecoderPreventsDefaultDecoderCreation() {
        assertThat(
                context.getBeanNamesForType(
                        RemoteErrorResponseDecoder.class
                )
        ).containsExactly(
                "customRemoteErrorResponseDecoder"
        );

        assertThat(
                context.getBean(
                        RemoteErrorResponseDecoder.class
                )
        ).isSameAs(
                TestApplication.CUSTOM_DECODER
        );
    }

    @Test
    void userLoggingSinkIsUsedWithoutConflict() {
        assertThat(
                context.getBeansOfType(
                        ErrorLogSink.class
                )
        ).hasSize(1);

        assertThat(
                context.getBean(
                        ErrorLogSink.class
                )
        ).isSameAs(
                TestApplication.CUSTOM_SINK
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        private static final
        OutgoingHttpExceptionMapper CUSTOM_MAPPER =
                new OutgoingHttpExceptionMapper(
                        "custom-service",
                        3
                );

        private static final
        RemoteErrorResponseDecoder CUSTOM_DECODER =
                new RemoteErrorResponseDecoder(
                        JsonMapper.builder()
                                .build()
                );

        private static final
        RecordingSink CUSTOM_SINK =
                new RecordingSink();

        @Bean
        OutgoingHttpExceptionMapper
        customOutgoingHttpExceptionMapper() {
            return CUSTOM_MAPPER;
        }

        @Bean
        RemoteErrorResponseDecoder
        customRemoteErrorResponseDecoder() {
            return CUSTOM_DECODER;
        }

        @Bean
        ErrorLogSink customErrorLogSink() {
            return CUSTOM_SINK;
        }
    }

    private static final class RecordingSink
            implements ErrorLogSink {

        @Override
        public void write(
                ErrorLogLevel level,
                String message,
                Throwable throwable
        ) {
        }
    }
}