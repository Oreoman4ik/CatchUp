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
import ru.oreoman4ik.catchup.client.OutgoingHttpExceptionMapper;
import ru.oreoman4ik.catchup.client.RemoteErrorResponseDecoder;
import ru.oreoman4ik.catchup.config.CurrentServiceName;
import ru.oreoman4ik.catchup.config.TechnicalDetailsFactory;
import ru.oreoman4ik.catchup.config.UnifiedErrorProperties;
import ru.oreoman4ik.catchup.context.ErrorContextAspect;
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
    @ConditionalOnMissingBean
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
    @ConditionalOnMissingBean
    TechnicalDetailsFactory
    technicalDetailsFactory(
            UnifiedErrorProperties properties
    ) {
        return new TechnicalDetailsFactory(
                properties
        );
    }

    @Bean
    @ConditionalOnMissingBean
    RemoteErrorResponseDecoder
    remoteErrorResponseDecoder(
            JsonMapper jsonMapper
    ) {
        return new RemoteErrorResponseDecoder(
                jsonMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean
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
    @ConditionalOnMissingBean
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
    @ConditionalOnMissingBean
    UnifiedGlobalExceptionHandler
    unifiedGlobalExceptionHandler(
            CurrentServiceName
                    currentServiceName,
            UnifiedErrorProperties properties,
            OutgoingHttpExceptionMapper mapper,
            TechnicalDetailsFactory
                    technicalDetailsFactory
    ) {
        return new UnifiedGlobalExceptionHandler(
                currentServiceName,
                properties,
                mapper,
                technicalDetailsFactory
        );
    }
}