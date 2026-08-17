package ru.oreoman4ik.catchup.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner
        .WebApplicationContextRunner;
import org.springframework.mock.web
        .MockHttpServletRequest;
import ru.oreoman4ik.catchup.client
        .OutgoingHttpExceptionMapper;
import ru.oreoman4ik.catchup.config
        .CurrentServiceName;
import ru.oreoman4ik.catchup.config
        .TechnicalDetailsFactory;
import ru.oreoman4ik.catchup.config
        .UnifiedErrorProperties;
import ru.oreoman4ik.catchup.context
        .ErrorContextAspect;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.web
        .UnifiedGlobalExceptionHandler;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedErrorAutoConfigurationTests {

    private final WebApplicationContextRunner
            contextRunner =
            new WebApplicationContextRunner()
                    .withUserConfiguration(
                            UnifiedErrorAutoConfiguration
                                    .class
                    )
                    .withBean(
                            JsonMapper.class,
                            () -> JsonMapper
                                    .builder()
                                    .build()
                    );

    @Test
    void worksWithoutMandatoryConfiguration() {
        contextRunner.run(context -> {
            assertThat(
                    context.getStartupFailure()
            ).isNull();

            assertThat(context)
                    .hasSingleBean(
                            UnifiedErrorProperties
                                    .class
                    );

            assertThat(context)
                    .hasSingleBean(
                            OutgoingHttpExceptionMapper
                                    .class
                    );

            assertThat(context)
                    .hasSingleBean(
                            ErrorContextAspect.class
                    );

            assertThat(context)
                    .hasSingleBean(
                            UnifiedGlobalExceptionHandler
                                    .class
                    );

            UnifiedErrorProperties properties =
                    context.getBean(
                            UnifiedErrorProperties.class
                    );

            assertThat(
                    properties.getMaxChainSize()
            ).isEqualTo(10);

            assertThat(
                    properties
                            .isIncludeTechnicalDetails()
            ).isFalse();

            assertThat(
                    properties
                            .isIncludeStackTrace()
            ).isFalse();

            assertThat(
                    context.getBean(
                            CurrentServiceName.class
                    ).value()
            ).isEqualTo("application");
        });
    }

    @Test
    void usesSpringApplicationName() {
        contextRunner
                .withPropertyValues(
                        "spring.application.name="
                                + "orders-service"
                )
                .run(context ->
                        assertThat(
                                context.getBean(
                                        CurrentServiceName
                                                .class
                                ).value()
                        ).isEqualTo(
                                "orders-service"
                        )
                );
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
                .run(context ->
                        assertThat(
                                context.getBean(
                                        CurrentServiceName
                                                .class
                                ).value()
                        ).isEqualTo(
                                "public-api"
                        )
                );
    }

    @Test
    void libraryCanBeDisabled() {
        contextRunner
                .withPropertyValues(
                        "catchup.errors.enabled=false"
                )
                .run(context -> {
                    assertThat(context)
                            .doesNotHaveBean(
                                    OutgoingHttpExceptionMapper
                                            .class
                            );

                    assertThat(context)
                            .doesNotHaveBean(
                                    ErrorContextAspect.class
                            );

                    assertThat(context)
                            .doesNotHaveBean(
                                    UnifiedGlobalExceptionHandler
                                            .class
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
                                    UnifiedGlobalExceptionHandler
                                            .class
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

                    assertThat(
                            response.getBody()
                    ).isNotNull();

                    assertThat(
                            response
                                    .getBody()
                                    .getMessage()
                    ).isEqualTo(
                            "Сервис временно недоступен"
                    );

                    /*
                     * Safe default:
                     * technical details выключены.
                     */
                    assertThat(
                            response
                                    .getBody()
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
                                    TechnicalDetailsFactory
                                            .class
                            );

                    ErrorDetails details =
                            factory.enrich(
                                    null,
                                    new IllegalStateException(
                                            "database secret"
                                    )
                            );

                    assertThat(
                            details.getTechnical()
                    ).isNotNull();

                    assertThat(
                            details.getTechnical()
                                    .getExceptionClass()
                    ).isEqualTo(
                            IllegalStateException
                                    .class
                                    .getName()
                    );

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
                    ErrorDetails details =
                            context.getBean(
                                            TechnicalDetailsFactory
                                                    .class
                                    )
                                    .enrich(
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