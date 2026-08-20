package ru.oreoman4ik.catchup.autoconfigure;

import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.DispatcherServlet;
import ru.oreoman4ik.catchup.client.CatchUpRestClientCustomizer;
import ru.oreoman4ik.catchup.client.CatchUpRestTemplateCustomizer;
import ru.oreoman4ik.catchup.client.OutgoingHttpExceptionMapper;
import ru.oreoman4ik.catchup.client.RemoteBodyLimitingInterceptor;
import ru.oreoman4ik.catchup.client.RemoteErrorResponseDecoder;
import ru.oreoman4ik.catchup.config.CurrentServiceName;
import ru.oreoman4ik.catchup.config.TechnicalDetailsFactory;
import ru.oreoman4ik.catchup.config.UnifiedErrorProperties;
import ru.oreoman4ik.catchup.context.ErrorContextAspect;
import ru.oreoman4ik.catchup.logging.ErrorLogSink;
import ru.oreoman4ik.catchup.logging.Slf4jErrorLogSink;
import ru.oreoman4ik.catchup.logging.UnifiedErrorLogger;
import ru.oreoman4ik.catchup.web.UnifiedGlobalExceptionHandler;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
@ConditionalOnWebApplication(
        type =
                ConditionalOnWebApplication
                        .Type
                        .SERVLET
)
@ConditionalOnClass({
        DispatcherServlet.class,
        JsonMapper.class,
        Aspect.class
})
@ConditionalOnProperty(
        prefix = "catchup.errors",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(
        UnifiedErrorProperties.class
)
public class UnifiedErrorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(
            CurrentServiceName.class
    )
    CurrentServiceName currentServiceName(
            UnifiedErrorProperties properties,
            Environment environment
    ) {
        return new CurrentServiceName(
                properties,
                environment
        );
    }

    @Bean
    @ConditionalOnMissingBean(
            TechnicalDetailsFactory.class
    )
    TechnicalDetailsFactory
    technicalDetailsFactory(
            UnifiedErrorProperties properties
    ) {
        return new TechnicalDetailsFactory(
                properties
        );
    }

    @Bean
    @ConditionalOnMissingBean(
            RemoteBodyLimitingInterceptor.class
    )
    RemoteBodyLimitingInterceptor
    remoteBodyLimitingInterceptor(
            UnifiedErrorProperties properties
    ) {
        return new RemoteBodyLimitingInterceptor(
                properties.getMaxRemoteBodyBytes()
        );
    }

    @Bean
    @ConditionalOnMissingBean(
            CatchUpRestClientCustomizer.class
    )
    CatchUpRestClientCustomizer
    catchUpRestClientCustomizer(
            RemoteBodyLimitingInterceptor interceptor
    ) {
        return new CatchUpRestClientCustomizer(
                interceptor
        );
    }

    @Bean
    @ConditionalOnMissingBean(
            CatchUpRestTemplateCustomizer.class
    )
    CatchUpRestTemplateCustomizer
    catchUpRestTemplateCustomizer(
            RemoteBodyLimitingInterceptor interceptor
    ) {
        return new CatchUpRestTemplateCustomizer(
                interceptor
        );
    }

    @Bean
    @ConditionalOnMissingBean(
            RemoteErrorResponseDecoder.class
    )
    RemoteErrorResponseDecoder
    remoteErrorResponseDecoder(
            JsonMapper jsonMapper,
            UnifiedErrorProperties properties
    ) {
        return new RemoteErrorResponseDecoder(
                jsonMapper,
                properties
        );
    }

    @Bean
    @ConditionalOnMissingBean(
            OutgoingHttpExceptionMapper.class
    )
    OutgoingHttpExceptionMapper
    outgoingHttpExceptionMapper(
            CurrentServiceName
                    currentServiceName,
            UnifiedErrorProperties properties,
            RemoteErrorResponseDecoder decoder,
            TechnicalDetailsFactory
                    technicalDetailsFactory
    ) {
        return new OutgoingHttpExceptionMapper(
                currentServiceName,
                properties,
                decoder,
                technicalDetailsFactory
        );
    }

    @Bean
    @ConditionalOnMissingBean({
            UnifiedErrorLogger.class,
            ErrorLogSink.class
    })
    ErrorLogSink errorLogSink() {
        return new Slf4jErrorLogSink();
    }

    @Bean
    @ConditionalOnMissingBean(
            UnifiedErrorLogger.class
    )
    UnifiedErrorLogger unifiedErrorLogger(
            ErrorLogSink sink
    ) {
        return new UnifiedErrorLogger(
                sink
        );
    }

    @Bean
    @ConditionalOnMissingBean(
            ErrorContextAspect.class
    )
    ErrorContextAspect errorContextAspect(
            CurrentServiceName
                    currentServiceName,
            UnifiedErrorProperties properties,
            OutgoingHttpExceptionMapper mapper,
            TechnicalDetailsFactory
                    technicalDetailsFactory
    ) {
        return new ErrorContextAspect(
                currentServiceName,
                properties,
                mapper,
                technicalDetailsFactory
        );
    }

    @Bean
    @ConditionalOnMissingBean(
            UnifiedGlobalExceptionHandler.class
    )
    UnifiedGlobalExceptionHandler
    unifiedGlobalExceptionHandler(
            CurrentServiceName
                    currentServiceName,
            UnifiedErrorProperties properties,
            OutgoingHttpExceptionMapper mapper,
            TechnicalDetailsFactory
                    technicalDetailsFactory,
            UnifiedErrorLogger errorLogger
    ) {
        return new UnifiedGlobalExceptionHandler(
                currentServiceName,
                properties,
                mapper,
                technicalDetailsFactory,
                errorLogger
        );
    }
}