package ru.oreoman4ik.catchup.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrorResponseJsonTests {

    private static final UUID ERROR_ID =
            UUID.fromString(
                    "7c12c42e-86ee-43b0-8324-9a56bf633ed4"
            );

    private static final Instant ERROR_TIME =
            Instant.parse(
                    "2026-07-30T10:42:15.018Z"
            );

    private final JsonMapper jsonMapper =
            JsonMapper.builder().build();

    @Test
    void serializesCanonicalContract() {
        ErrorResponse response =
                validResponse(
                        ErrorDetails.builder()
                                .resource(
                                        "COMPONENT"
                                )
                                .build()
                );

        String json =
                jsonMapper.writeValueAsString(
                        response
                );

        assertThat(json)
                .contains(
                        "\"errorId\":\""
                                + ERROR_ID
                                + "\""
                )
                .contains(
                        "\"timestamp\":"
                                + "\"2026-07-30T10:42:15.018Z\""
                )
                .contains(
                        "\"status\":404"
                )
                .contains(
                        "\"message\":"
                                + "\"Компонент не найден\""
                )
                .contains(
                        "\"errorCode\":"
                                + "\"COMPONENT_NOT_FOUND\""
                )
                .contains(
                        "\"currentService\":"
                                + "\"service-a\""
                )
                .contains(
                        "\"resource\":"
                                + "\"COMPONENT\""
                )
                .doesNotContain(
                        "\"errorType\""
                )
                .doesNotContain(
                        "\"exceptionType\""
                )
                .doesNotContain(
                        "\"causeCode\""
                )
                .doesNotContain(
                        "\"publicMessage\""
                )
                .doesNotContain(
                        "\"httpStatus\""
                );
    }

    @Test
    void timestampFormatIsIndependentFromMapperConfiguration() {
        JsonMapper timestampMapper =
                JsonMapper.builder()
                        .enable(
                                DateTimeFeature
                                        .WRITE_DATES_AS_TIMESTAMPS
                        )
                        .build();

        String json =
                timestampMapper
                        .writeValueAsString(
                                validResponse(null)
                        );

        assertThat(json)
                .contains(
                        "\"timestamp\":"
                                + "\"2026-07-30T10:42:15.018Z\""
                );
    }

    @Test
    void unknownFieldsAreIgnored() {
        String json = """
                {
                  "errorId": "7c12c42e-86ee-43b0-8324-9a56bf633ed4",
                  "timestamp": "2026-07-30T10:42:15.018Z",
                  "status": 404,
                  "message": "Компонент не найден",
                  "errorCode": "COMPONENT_NOT_FOUND",
                  "currentService": "service-a",
                  "futureRoot": true,
                  "chain": [
                    {
                      "service": "service-b",
                      "component": "ComponentCatalog",
                      "operation": "findComponent",
                      "errorCode": "COMPONENT_NOT_FOUND",
                      "message": "Компонент не найден",
                      "timestamp": "2026-07-30T10:42:15.018Z",
                      "status": 404,
                      "futureChain": "ignored"
                    }
                  ]
                }
                """;

        ErrorResponse response =
                jsonMapper.readValue(
                        json,
                        ErrorResponse.class
                );

        assertThat(response.getErrorId())
                .isEqualTo(ERROR_ID);

        assertThat(response.getStatus())
                .isEqualTo(404);

        assertThat(response.getDetails())
                .isNull();

        assertThat(response.getChain())
                .hasSize(1);

        assertThat(
                response.getChain()
                        .getFirst()
                        .getErrorCode()
        ).isEqualTo(
                "COMPONENT_NOT_FOUND"
        );
    }

    @Test
    void truncationInfoIsSerialized() {
        ErrorDetails details =
                ErrorDetails.builder()
                        .truncation(
                                new TruncationInfo(
                                        true,
                                        false,
                                        true,
                                        true
                                )
                        )
                        .build();

        String json =
                jsonMapper.writeValueAsString(
                        validResponse(details)
                );

        assertThat(json)
                .contains(
                        "\"truncation\""
                )
                .contains(
                        "\"chain\":true"
                )
                .contains(
                        "\"technicalDetails\":true"
                )
                .contains(
                        "\"remoteBody\":true"
                );
    }

    @Test
    void truncationInfoRoundTripPreservesValue() {
        ErrorDetails details =
                ErrorDetails.builder()
                        .resource("ITEM")
                        .truncation(
                                new TruncationInfo(
                                        true,
                                        true,
                                        false,
                                        false
                                )
                        )
                        .build();

        ErrorResponse original =
                validResponse(details);

        ErrorResponse restored =
                jsonMapper.readValue(
                        jsonMapper
                                .writeValueAsString(
                                        original
                                ),
                        ErrorResponse.class
                );

        assertThat(restored)
                .isEqualTo(original);
    }

    @Test
    void technicalDetailsAreSerializedOnlyAtRootDetails() {
        TechnicalDetails technical =
                new TechnicalDetails(
                        "java.lang.IllegalStateException",
                        null,
                        List.of(
                                "example.Service.call(Service.java:10)"
                        )
                );

        ErrorResponse response =
                validResponse(
                        ErrorDetails.builder()
                                .technical(technical)
                                .build()
                );

        String json =
                jsonMapper.writeValueAsString(
                        response
                );

        assertThat(json)
                .contains(
                        "\"technical\""
                )
                .contains(
                        "\"exceptionClass\""
                )
                .contains(
                        "\"stackTrace\""
                );

        /*
         * ChainElement не содержит stack trace.
         */
        assertThat(
                countOccurrences(
                        json,
                        "\"stackTrace\""
                )
        ).isEqualTo(1);
    }

    @Test
    void publicMessageIsLimited() {
        ErrorResponse response =
                ErrorResponse.builder()
                        .errorId(ERROR_ID)
                        .timestamp(ERROR_TIME)
                        .status(500)
                        .message(
                                "x".repeat(900)
                        )
                        .errorCode(
                                "INTERNAL_ERROR"
                        )
                        .currentService(
                                "service-a"
                        )
                        .chain(
                                List.of(
                                        validChainElement()
                                )
                        )
                        .build();

        assertThat(response.getMessage())
                .hasSize(500);
    }

    @Test
    void snapshotsInputCollections() {
        List<ChainElement> chain =
                new ArrayList<>();

        chain.add(
                validChainElement()
        );

        ErrorResponse response =
                ErrorResponse.builder()
                        .errorId(ERROR_ID)
                        .timestamp(ERROR_TIME)
                        .status(404)
                        .message(
                                "Компонент не найден"
                        )
                        .errorCode(
                                "COMPONENT_NOT_FOUND"
                        )
                        .currentService(
                                "service-a"
                        )
                        .chain(chain)
                        .build();

        chain.clear();

        assertThat(response.getChain())
                .hasSize(1);

        assertThatThrownBy(
                () ->
                        response.getChain()
                                .clear()
        )
                .isInstanceOf(
                        UnsupportedOperationException.class
                );
    }

    @Test
    void fieldViolationCanBeCreatedFromApplicationCode() {
        ErrorDetails.FieldViolation violation =
                ErrorDetails.FieldViolation.of(
                        "name",
                        "REQUIRED",
                        "Название обязательно"
                );

        assertThat(violation.getField())
                .isEqualTo("name");

        assertThat(violation.getReasonCode())
                .isEqualTo("REQUIRED");

        assertThat(violation.getMessage())
                .isEqualTo(
                        "Название обязательно"
                );
    }

    @Test
    void rejectsEmptyDetails() {
        assertThatThrownBy(
                () ->
                        ErrorDetails.builder()
                                .build()
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "at least one value"
                );
    }

    @Test
    void rejectsNonErrorStatus() {
        assertThatThrownBy(
                () ->
                        ErrorResponse.builder()
                                .errorId(ERROR_ID)
                                .timestamp(ERROR_TIME)
                                .status(200)
                                .message("Ошибка")
                                .errorCode(
                                        "INTERNAL_ERROR"
                                )
                                .currentService(
                                        "service-a"
                                )
                                .chain(
                                        List.of(
                                                validChainElement()
                                        )
                                )
                                .build()
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "400 to 599"
                );
    }

    @Test
    void rejectsExceptionTypeAsPublicCode() {
        assertThatThrownBy(
                () ->
                        ErrorResponse.builder()
                                .errorId(ERROR_ID)
                                .timestamp(ERROR_TIME)
                                .status(500)
                                .message("Ошибка")
                                .errorCode(
                                        "NULLPOINTEREXCEPTION"
                                )
                                .currentService(
                                        "service-a"
                                )
                                .chain(
                                        List.of(
                                                validChainElement()
                                        )
                                )
                                .build()
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "Java exception type"
                );
    }

    private static ErrorResponse validResponse(
            ErrorDetails details
    ) {
        return ErrorResponse.builder()
                .errorId(ERROR_ID)
                .timestamp(ERROR_TIME)
                .status(404)
                .message(
                        "Компонент не найден"
                )
                .errorCode(
                        "COMPONENT_NOT_FOUND"
                )
                .currentService(
                        "service-a"
                )
                .chain(
                        List.of(
                                validChainElement()
                        )
                )
                .details(details)
                .build();
    }

    private static ChainElement validChainElement() {
        return ChainElement.builder()
                .service("service-b")
                .component(
                        "ComponentCatalog"
                )
                .operation(
                        "findComponent"
                )
                .errorCode(
                        "COMPONENT_NOT_FOUND"
                )
                .message(
                        "Компонент не найден"
                )
                .timestamp(ERROR_TIME)
                .status(404)
                .build();
    }

    private static int countOccurrences(
            String text,
            String needle
    ) {
        int count = 0;
        int index = 0;

        while ((index =
                text.indexOf(
                        needle,
                        index
                )) >= 0) {

            count++;
            index += needle.length();
        }

        return count;
    }
}