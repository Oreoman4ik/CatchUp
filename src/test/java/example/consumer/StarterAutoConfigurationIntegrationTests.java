package example.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
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
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

@SpringBootTest(
        classes =
                StarterAutoConfigurationIntegrationTests
                        .TestApplication.class,
        properties = {
                "spring.application.name=clean-test-service"
        }
)
@AutoConfigureMockMvc
class StarterAutoConfigurationIntegrationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void starterIsDiscoveredWithoutManualImport() {
        assertThat(
                context.getBeansOfType(
                        UnifiedErrorProperties.class
                )
        ).hasSize(1);

        assertThat(
                context.getBeansOfType(
                        CurrentServiceName.class
                )
        ).hasSize(1);

        assertThat(
                context.getBeansOfType(
                        TechnicalDetailsFactory.class
                )
        ).hasSize(1);

        assertThat(
                context.getBeansOfType(
                        RemoteErrorResponseDecoder.class
                )
        ).hasSize(1);

        assertThat(
                context.getBeansOfType(
                        OutgoingHttpExceptionMapper.class
                )
        ).hasSize(1);

        assertThat(
                context.getBeansOfType(
                        ErrorContextAspect.class
                )
        ).hasSize(1);

        assertThat(
                context.getBeansOfType(
                        ErrorLogSink.class
                )
        ).hasSize(1);

        assertThat(
                context.getBeansOfType(
                        UnifiedErrorLogger.class
                )
        ).hasSize(1);

        assertThat(
                context.getBeansOfType(
                        UnifiedGlobalExceptionHandler.class
                )
        ).hasSize(1);
    }

    @Test
    void serviceNameComesFromApplicationConfiguration() {
        assertThat(
                context.getBean(
                        CurrentServiceName.class
                ).value()
        ).isEqualTo(
                "clean-test-service"
        );
    }

    @Test
    void cleanApplicationGetsUnifiedErrorAutomatically()
            throws Exception {

        mockMvc.perform(
                        get("/test/error")
                )
                .andExpect(
                        status()
                                .isInternalServerError()
                )
                .andExpect(
                        jsonPath("$.errorId")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(500)
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value(
                                        "INTERNAL_ERROR"
                                )
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Внутренняя ошибка сервиса"
                                )
                )
                .andExpect(
                        jsonPath("$.currentService")
                                .value(
                                        "clean-test-service"
                                )
                )
                .andExpect(
                        jsonPath("$.chain[0].service")
                                .value(
                                        "clean-test-service"
                                )
                );
    }

    @Test
    void secretIsNotPublishedByDefault()
            throws Exception {

        mockMvc.perform(
                        get("/test/error")
                )
                .andExpect(
                        status()
                                .isInternalServerError()
                )
                .andExpect(
                        jsonPath(
                                "$.details.technical"
                        ).doesNotExist()
                );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static final class TestController {

        @GetMapping("/test/error")
        String error() {
            throw new IllegalStateException(
                    "jdbc://internal "
                            + "password=secret"
            );
        }
    }
}