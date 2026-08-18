package ru.oreoman4ik.catchup.client;

import org.springframework.web.client.RestClientResponseException;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

public final class RemoteErrorResponseDecoder {

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

    /**
     * Пытается прочитать ErrorResponse другого сервиса.
     *
     * <p>Повреждённый JSON, неизвестный формат или
     * несовпадение HTTP-статуса не выбрасываются наружу:
     * вызывающий код сможет применить обычную fallback
     * классификацию HTTP-ошибки.</p>
     */
    public Optional<ErrorResponse> decode(
            RestClientResponseException exception
    ) {
        if (exception == null) {
            return Optional.empty();
        }

        byte[] body =
                exception
                        .getResponseBodyAsByteArray();

        if (body.length == 0) {
            return Optional.empty();
        }

        try {
            ErrorResponse response =
                    jsonMapper.readValue(
                            body,
                            ErrorResponse.class
                    );

            /*
             * Не доверяем JSON, если заявленный status
             * отличается от реального HTTP status.
             */
            if (response.getStatus()
                    != exception
                    .getStatusCode()
                    .value()) {

                return Optional.empty();
            }

            return Optional.of(response);

        } catch (Exception ignored) {

            /*
             * Повреждённый JSON или чужой формат
             * не должны создавать вторую
             * необработанную ошибку.
             */
            return Optional.empty();
        }
    }
}