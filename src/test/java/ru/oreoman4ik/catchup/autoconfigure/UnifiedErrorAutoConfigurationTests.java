package ru.oreoman4ik.catchup.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.oreoman4ik.catchup.client.OutgoingHttpExceptionMapper;
import ru.oreoman4ik.catchup.client.RemoteErrorResponseDecoder;
import ru.oreoman4ik.catchup.config.CurrentServiceName;
import ru.oreoman4ik.catchup.config.TechnicalDetailsFactory;
import ru.oreoman4ik.catchup.config.UnifiedErrorProperties;
import ru.oreoman4ik.catchup.context.ErrorContextAspect;
import ru.oreoman4ik.catchup.logging.ErrorLogSink;
import ru.oreoman4ik.catchup.logging.Slf4jErrorLogSink;
import ru.oreoman4ik.catchup.logging.UnifiedErrorLogger;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.web.UnifiedGlobalExceptionHandler;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedErrorAutoConfigurationTests {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withUserConfiguration(
                            UnifiedErrorAutoConfiguration.class
                    )
                    .withBean(
                            JsonMapper.class,
                            () -> JsonMapper.builder().build()
                    );

    @Test
    void worksWithoutMandatoryConfiguration() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure())
                    .isNull();

            assertThat(context)
                    .hasSingleBean(
                            UnifiedErrorProperties.class
                    );

            assertThat(context)
                    .hasSingleBean(
                            CurrentServiceName.class
                    );

            assertThat(context)
                    .hasSingleBean(
                            TechnicalDetailsFactory.class
                    );

            assertThat(context)
                    .hasSingleBean(
                            RemoteErrorResponseDecoder.class
                    );

            assertThat(context)
                    .hasSingleBean(
                            OutgoingHttpExceptionMapper.class
                    );

            assertThat(context)
                    .hasSingleBean(
                            ErrorContextAspect.class
                    );

            assertThat(context)
                    .hasSingleBean(
                            ErrorLogSink.class
                    );

            assertThat(context)
                    .hasSingleBean(
                            UnifiedErrorLogger.class
                    );

            assertThat(context)
                    .hasSingleBean(
                            UnifiedGlobalExceptionHandler.class
                    );

            assertThat(
                    context.getBean(
                            ErrorLogSink.class
                    )
            ).isInstanceOf(
                    Slf4jErrorLogSink.class
            );
        });
    }

    @Test
    void safeDefaultsAreApplied() {
        contextRunner.run(context -> {
            UnifiedErrorProperties properties =
                    context.getBean(
                            UnifiedErrorProperties.class
                    );

            assertThat(properties.isEnabled())
                    .isTrue();

            assertThat(properties.getMaxChainSize())
                    .isEqualTo(10);

            assertThat(
                    properties.isIncludeTechnicalDetails()
            ).isFalse();

            assertThat(
                    properties.isIncludeStackTrace()
            ).isFalse();

            assertThat(
                    properties.getUnknownErrorMessage()
            ).isEqualTo(
                    "Внутренняя ошибка сервиса"
            );

            assertThat(
                    properties.getMaxRemoteBodyBytes()
            ).isEqualTo(
                    2 * 1024 * 1024
            );

            assertThat(
                    properties.getMaxStackTraceLines()
            ).isEqualTo(100);

            assertThat(
                    properties.getMaxTechnicalTextLength()
            ).isEqualTo(1000);
        });
    }

    @Test
    void usesSpringApplicationName() {
        contextRunner
                .withPropertyValues(
                        "spring.application.name="
                                + "orders-service"
                )
                .run(context -> {
                    CurrentServiceName service =
                            context.getBean(
                                    CurrentServiceName.class
                            );

                    assertThat(service.value())
                            .isEqualTo(
                                    "orders-service"
                            );
                });
    }

    @Test
    void explicitServiceNameHasPriority() {
        contextRunner
                .withPropertyValues(
                        "spring.application.name="
                                + "orders-service",

                        "catchup.errors.service-name="
                                + "public-api"
                )
                .run(context -> {
                    CurrentServiceName service =
                            context.getBean(
                                    CurrentServiceName.class
                            );

                    assertThat(service.value())
                            .isEqualTo(
                                    "public-api"
                            );
                });
    }

    @Test
    void libraryCanBeCompletelyDisabled() {
        contextRunner
                .withPropertyValues(
                        "catchup.errors.enabled=false"
                )
                .run(context -> {
                    assertThat(context)
                            .doesNotHaveBean(
                                    UnifiedErrorProperties.class
                            );

                    assertThat(context)
                            .doesNotHaveBean(
                                    CurrentServiceName.class
                            );

                    assertThat(context)
                            .doesNotHaveBean(
                                    TechnicalDetailsFactory.class
                            );

                    assertThat(context)
                            .doesNotHaveBean(
                                    RemoteErrorResponseDecoder.class
                            );

                    assertThat(context)
                            .doesNotHaveBean(
                                    OutgoingHttpExceptionMapper.class
                            );

                    assertThat(context)
                            .doesNotHaveBean(
                                    ErrorContextAspect.class
                            );

                    assertThat(context)
                            .doesNotHaveBean(
                                    ErrorLogSink.class
                            );

                    assertThat(context)
                            .doesNotHaveBean(
                                    UnifiedErrorLogger.class
                            );

                    assertThat(context)
                            .doesNotHaveBean(
                                    UnifiedGlobalExceptionHandler.class
                            );
                });
    }

    @Test
    void invalidChainSizeFailsFast() {
        contextRunner
                .withPropertyValues(
                        "catchup.errors.max-chain-size=0"
                )
                .run(context -> {
                    assertThat(
                            context.getStartupFailure()
                    ).isNotNull();

                    assertThat(
                            context.getStartupFailure()
                    ).hasRootCauseMessage(
                            "catchup.errors.max-chain-size "
                                    + "must be from 1 to 100"
                    );
                });
    }

    @Test
    void oversizedChainLimitFailsFast() {
        contextRunner
                .withPropertyValues(
                        "catchup.errors.max-chain-size=101"
                )
                .run(context -> {
                    assertThat(
                            context.getStartupFailure()
                    ).isNotNull();
                });
    }

    @Test
    void tooSmallRemoteBodyLimitFailsFast() {
        contextRunner
                .withPropertyValues(
                        "catchup.errors."
                                + "max-remote-body-bytes=100"
                )
                .run(context -> {
                    assertThat(
                            context.getStartupFailure()
                    ).isNotNull();
                });
    }

    @Test
    void tooLargeRemoteBodyLimitFailsFast() {
        contextRunner
                .withPropertyValues(
                        "catchup.errors."
                                + "max-remote-body-bytes="
                                + (9 * 1024 * 1024)
                )
                .run(context -> {
                    assertThat(
                            context.getStartupFailure()
                    ).isNotNull();
                });
    }

    @Test
    void invalidStackTraceLinesFailsFast() {
        contextRunner
                .withPropertyValues(
                        "catchup.errors."
                                + "max-stack-trace-lines=0"
                )
                .run(context -> {
                    assertThat(
                            context.getStartupFailure()
                    ).isNotNull();
                });
    }

    @Test
    void invalidTechnicalTextLengthFailsFast() {
        contextRunner
                .withPropertyValues(
                        "catchup.errors."
                                + "max-technical-text-length=10"
                )
                .run(context -> {
                    assertThat(
                            context.getStartupFailure()
                    ).isNotNull();
                });
    }

    @Test
    void stackTraceRequiresTechnicalDetails() {
        contextRunner
                .withPropertyValues(
                        "catchup.errors."
                                + "include-stack-trace=true",

                        "catchup.errors."
                                + "include-technical-details=false"
                )
                .run(context -> {
                    assertThat(
                            context.getStartupFailure()
                    ).isNotNull();
                });
    }

    @Test
    void customUnknownMessageIsApplied() {
        contextRunner
                .withPropertyValues(
                        "catchup.errors."
                                + "unknown-error-message="
                                + "Сервис временно недоступен"
                )
                .run(context -> {
                    UnifiedGlobalExceptionHandler handler =
                            context.getBean(
                                    UnifiedGlobalExceptionHandler.class
                            );

                    var response =
                            handler.handleUnexpectedError(
                                    new IllegalStateException(
                                            "secret database error"
                                    ),
                                    new MockHttpServletRequest(
                                            "GET",
                                            "/test"
                                    )
                            );

                    assertThat(response.getBody())
                            .isNotNull();

                    assertThat(
                            response.getBody()
                                    .getMessage()
                    ).isEqualTo(
                            "Сервис временно недоступен"
                    );

                    assertThat(
                            response.getBody()
                                    .getDetails()
                    ).isNull();
                });
    }

    @Test
    void technicalDetailsCanBeEnabled() {
        contextRunner
                .withPropertyValues(
                        "catchup.errors."
                                + "include-technical-details=true"
                )
                .run(context -> {
                    TechnicalDetailsFactory factory =
                            context.getBean(
                                    TechnicalDetailsFactory.class
                            );

                    ErrorDetails details =
                            factory.enrich(
                                    null,
                                    new IllegalStateException(
                                            "database password=secret"
                                    )
                            );

                    assertThat(details)
                            .isNotNull();

                    assertThat(
                            details.getTechnical()
                    ).isNotNull();

                    assertThat(
                            details.getTechnical()
                                    .getExceptionClass()
                    ).isEqualTo(
                            IllegalStateException.class
                                    .getName()
                    );

                    /*
                     * Throwable message не публикуется.
                     */
                    assertThat(
                            details.getTechnical()
                                    .getExceptionMessage()
                    ).isNull();

                    assertThat(
                            details.getTechnical()
                                    .getStackTrace()
                    ).isEmpty();
                });
    }

    @Test
    void stackTraceCanBeEnabledExplicitly() {
        contextRunner
                .withPropertyValues(
                        "catchup.errors."
                                + "include-technical-details=true",

                        "catchup.errors."
                                + "include-stack-trace=true"
                )
                .run(context -> {
                    TechnicalDetailsFactory factory =
                            context.getBean(
                                    TechnicalDetailsFactory.class
                            );

                    ErrorDetails details =
                            factory.enrich(
                                    null,
                                    new IllegalStateException(
                                            "technical"
                                    )
                            );

                    assertThat(
                            details.getTechnical()
                                    .getStackTrace()
                    ).isNotEmpty();
                });
    }
}