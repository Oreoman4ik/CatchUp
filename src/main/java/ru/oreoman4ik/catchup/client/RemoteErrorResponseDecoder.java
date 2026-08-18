package ru.oreoman4ik.catchup.client;

import org.springframework.web.client.RestClientResponseException;
import ru.oreoman4ik.catchup.config.UnifiedErrorProperties;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

public final class RemoteErrorResponseDecoder {

    private static final int
            DEFAULT_MAX_REMOTE_BODY_BYTES =
            2 * 1024 * 1024;

    private final JsonMapper jsonMapper;

    private final int maxRemoteBodyBytes;

    public RemoteErrorResponseDecoder(
            JsonMapper jsonMapper,
            UnifiedErrorProperties properties
    ) {
        if (jsonMapper == null) {
            throw new IllegalArgumentException(
                    "jsonMapper is required"
            );
        }

        if (properties == null) {
            throw new IllegalArgumentException(
                    "properties is required"
            );
        }

        this.jsonMapper = jsonMapper;

        this.maxRemoteBodyBytes =
                properties
                        .getMaxRemoteBodyBytes();
    }

    /*
     * Для существующих unit-тестов.
     */
    public RemoteErrorResponseDecoder(
            JsonMapper jsonMapper
    ) {
        if (jsonMapper == null) {
            throw new IllegalArgumentException(
                    "jsonMapper is required"
            );
        }

        this.jsonMapper = jsonMapper;

        this.maxRemoteBodyBytes =
                DEFAULT_MAX_REMOTE_BODY_BYTES;
    }

    public DecodeResult decodeWithMetadata(
            RestClientResponseException exception
    ) {
        if (exception == null) {
            return DecodeResult.unsupported();
        }

        byte[] body =
                exception
                        .getResponseBodyAsByteArray();

        if (body.length == 0) {
            return DecodeResult.unsupported();
        }

        if (body.length
                > maxRemoteBodyBytes) {

            /*
             * Не пытаемся парсить большой JSON.
             */
            return DecodeResult.tooLarge();
        }

        try {
            ErrorResponse response =
                    jsonMapper.readValue(
                            body,
                            ErrorResponse.class
                    );

            if (response.getStatus()
                    != exception
                    .getStatusCode()
                    .value()) {

                return DecodeResult
                        .unsupported();
            }

            return DecodeResult.supported(
                    response
            );

        } catch (Exception ignored) {

            return DecodeResult.unsupported();
        }
    }

    /*
     * Сохраняем старый API.
     */
    public Optional<ErrorResponse> decode(
            RestClientResponseException exception
    ) {
        return decodeWithMetadata(
                exception
        ).response();
    }

    public record DecodeResult(
            Optional<ErrorResponse> response,
            boolean remoteBodyTruncated
    ) {

        static DecodeResult supported(
                ErrorResponse response
        ) {
            return new DecodeResult(
                    Optional.of(response),
                    false
            );
        }

        static DecodeResult unsupported() {
            return new DecodeResult(
                    Optional.empty(),
                    false
            );
        }

        static DecodeResult tooLarge() {
            return new DecodeResult(
                    Optional.empty(),
                    true
            );
        }
    }
}