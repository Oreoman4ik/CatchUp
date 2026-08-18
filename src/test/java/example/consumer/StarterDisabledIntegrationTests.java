package example.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import ru.oreoman4ik.catchup.client.OutgoingHttpExceptionMapper;
import ru.oreoman4ik.catchup.client.RemoteErrorResponseDecoder;
import ru.oreoman4ik.catchup.config.CurrentServiceName;
import ru.oreoman4ik.catchup.config.TechnicalDetailsFactory;
import ru.oreoman4ik.catchup.config.UnifiedErrorProperties;
import ru.oreoman4ik.catchup.context.ErrorContextAspect;
import ru.oreoman4ik.catchup.logging.ErrorLogSink;
import ru.oreoman4ik.catchup.logging.UnifiedErrorLogger;
import ru.oreoman4ik.catchup.web.UnifiedGlobalExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes =
                StarterDisabledIntegrationTests
                        .TestApplication.class,
        properties = {
                "catchup.errors.enabled=false"
        }
)
class StarterDisabledIntegrationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void disabledStarterCreatesNoLibraryComponents() {
        assertThat(
                context.getBeansOfType(
                        UnifiedErrorProperties.class
                )
        ).isEmpty();

        assertThat(
                context.getBeansOfType(
                        CurrentServiceName.class
                )
        ).isEmpty();

        assertThat(
                context.getBeansOfType(
                        TechnicalDetailsFactory.class
                )
        ).isEmpty();

        assertThat(
                context.getBeansOfType(
                        RemoteErrorResponseDecoder.class
                )
        ).isEmpty();

        assertThat(
                context.getBeansOfType(
                        OutgoingHttpExceptionMapper.class
                )
        ).isEmpty();

        assertThat(
                context.getBeansOfType(
                        ErrorContextAspect.class
                )
        ).isEmpty();

        assertThat(
                context.getBeansOfType(
                        ErrorLogSink.class
                )
        ).isEmpty();

        assertThat(
                context.getBeansOfType(
                        UnifiedErrorLogger.class
                )
        ).isEmpty();

        assertThat(
                context.getBeansOfType(
                        UnifiedGlobalExceptionHandler.class
                )
        ).isEmpty();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}