package ru.oreoman4ik.catchup.client;

import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.core.Ordered;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

public final class CatchUpRestTemplateCustomizer
        implements RestTemplateCustomizer, Ordered {

    private final RemoteBodyLimitingInterceptor interceptor;

    public CatchUpRestTemplateCustomizer(
            RemoteBodyLimitingInterceptor interceptor
    ) {
        if (interceptor == null) {
            throw new IllegalArgumentException(
                    "interceptor is required"
            );
        }

        this.interceptor = interceptor;
    }

    @Override
    public void customize(
            RestTemplate restTemplate
    ) {
        List<ClientHttpRequestInterceptor> interceptors =
                new ArrayList<>(
                        restTemplate.getInterceptors()
                );

        interceptors.removeIf(
                RemoteBodyLimitingInterceptor.class
                        ::isInstance
        );

        interceptors.add(
                interceptor
        );

        restTemplate.setInterceptors(
                interceptors
        );
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}