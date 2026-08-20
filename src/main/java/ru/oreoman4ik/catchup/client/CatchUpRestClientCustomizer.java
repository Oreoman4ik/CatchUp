package ru.oreoman4ik.catchup.client;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;

public final class CatchUpRestClientCustomizer
        implements RestClientCustomizer, Ordered {

    private final RemoteBodyLimitingInterceptor
            interceptor;

    public CatchUpRestClientCustomizer(
            RemoteBodyLimitingInterceptor interceptor
    ) {
        this.interceptor = interceptor;
    }

    @Override
    public void customize(
            RestClient.Builder builder
    ) {
        builder.requestInterceptors(
                interceptors -> {

                    /*
                     * Не допускаем повторной регистрации.
                     */
                    interceptors.removeIf(
                            RemoteBodyLimitingInterceptor.class
                                    ::isInstance
                    );

                    /*
                     * Ставим последним interceptor'ом,
                     * максимально близко к HTTP transport.
                     */
                    interceptors.add(
                            interceptor
                    );
                }
        );
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}