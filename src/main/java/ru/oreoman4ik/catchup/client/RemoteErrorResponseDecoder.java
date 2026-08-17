package ru.oreoman4ik.catchup.client;

import org.springframework.web.client.RestClientResponseException;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

public final class RemoteErrorResponseDecoder {

    private static final int
            MAX_SUPPORTED_BODY_BYTES =
            64 * 1024;

    private final JsonMapper jsonMapper;

    public RemoteErrorResponseDecoder(
            JsonMapper jsonMapper
    ) {
        if (jsonMapper == null) {
            throw new IllegalArgumentException(
                    "jsonMapper is required"
            );
        }

        this.jsonMapper = jsonMapper;
    }

    public Optional<ErrorResponse> decode(
            RestClientResponseException exception
    ) {
        if (exception == null) {
            return Optional.empty();
        }

        byte[] body =
                exception
                        .getResponseBodyAsByteArray();

        if (body.length == 0
                || body.length
                > MAX_SUPPORTED_BODY_BYTES) {

            return Optional.empty();
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

                return Optional.empty();
            }

            return Optional.of(response);

        } catch (Exception ignored) {

            /*
             * Повреждённый или неизвестный JSON
             * не создаёт вторую ошибку.
             */
            return Optional.empty();
        }
    }
}