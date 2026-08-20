package ru.oreoman4ik.catchup.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;

public final class RemoteBodyLimitingInterceptor
        implements ClientHttpRequestInterceptor {

    private final int maxRemoteBodyBytes;

    public RemoteBodyLimitingInterceptor(
            int maxRemoteBodyBytes
    ) {
        if (maxRemoteBodyBytes < 1) {
            throw new IllegalArgumentException(
                    "maxRemoteBodyBytes must be positive"
            );
        }

        this.maxRemoteBodyBytes =
                maxRemoteBodyBytes;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {

        ClientHttpResponse response =
                execution.execute(
                        request,
                        body
                );

        /*
         * Success body библиотека ошибок
         * ограничивать не должна.
         */
        if (!response
                .getStatusCode()
                .isError()) {

            return response;
        }

        /*
         * Читаем максимум limit + 1.
         *
         * Дополнительный байт нужен для того,
         * чтобы RemoteErrorResponseDecoder мог
         * отличить:
         *
         * body <= limit
         * от
         * body > limit.
         */
        return new LimitedClientHttpResponse(
                response,
                (long) maxRemoteBodyBytes + 1L
        );
    }

    private static final class
    LimitedClientHttpResponse
            implements ClientHttpResponse {

        private final ClientHttpResponse delegate;

        private final InputStream limitedBody;

        private LimitedClientHttpResponse(
                ClientHttpResponse delegate,
                long maxBytes
        ) throws IOException {

            this.delegate = delegate;

            this.limitedBody =
                    new LimitedInputStream(
                            delegate.getBody(),
                            maxBytes
                    );
        }

        @Override
        public HttpStatusCode getStatusCode()
                throws IOException {

            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText()
                throws IOException {

            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return limitedBody;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class
    LimitedInputStream
            extends InputStream {

        private final InputStream delegate;

        private long remaining;

        private LimitedInputStream(
                InputStream delegate,
                long maxBytes
        ) {
            this.delegate = delegate;
            this.remaining = maxBytes;
        }

        @Override
        public int read()
                throws IOException {

            if (remaining == 0) {
                return -1;
            }

            int value = delegate.read();

            if (value >= 0) {
                remaining--;
            }

            return value;
        }

        @Override
        public int read(
                byte[] buffer,
                int offset,
                int length
        ) throws IOException {

            if (length == 0) {
                return 0;
            }

            if (remaining == 0) {
                return -1;
            }

            int allowed =
                    (int) Math.min(
                            remaining,
                            length
                    );

            int read =
                    delegate.read(
                            buffer,
                            offset,
                            allowed
                    );

            if (read > 0) {
                remaining -= read;
            }

            return read;
        }

        @Override
        public long skip(long count)
                throws IOException {

            if (remaining == 0) {
                return 0;
            }

            long skipped =
                    delegate.skip(
                            Math.min(
                                    count,
                                    remaining
                            )
                    );

            remaining -= skipped;

            return skipped;
        }

        @Override
        public int available()
                throws IOException {

            return (int) Math.min(
                    delegate.available(),
                    remaining
            );
        }

        @Override
        public void close()
                throws IOException {

            delegate.close();
        }

        @Override
        public boolean markSupported() {
            return false;
        }
    }
}