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

    private static final UUID ERROR_ID = UUID.fromString(
            "7c12c42e-86ee-43b0-8324-9a56bf633ed4"
    );

    private static final Instant ERROR_TIME = Instant.parse(
            "2026-07-30T10:42:15.018Z"
    );

    private final JsonMapper jsonMapper =
            JsonMapper.builder().build();

    @Test
    void serializesCanonicalPublicContract() {
        ErrorResponse response = validResponse(
                ErrorDetails.builder()
                        .resource("COMPONENT")
                        .build()
        );

        String json = jsonMapper.writeValueAsString(response);

        assertThat(json)
                .contains("\"errorId\":\"" + ERROR_ID + "\"")
                .contains(
                        "\"timestamp\":"
                                + "\"2026-07-30T10:42:15.018Z\""
                )
                .contains("\"status\":404")
                .contains(
                        "\"errorType\":"
                                + "\"COMPONENT_NOT_FOUND\""
                )
                .contains(
                        "\"exceptionType\":"
                                + "\"COMPONENT_NOT_FOUND\""
                )
                .contains(
                        "\"currentService\":\"service-a\""
                )
                .contains("\"resource\":\"COMPONENT\"")
                .doesNotContain("\"status\":\"404\"")
                .doesNotContain("org.springframework")
                .doesNotContain("java.lang");
    }

    @Test
    void timestampFormatOverridesMapperTimestampConfiguration() {
        JsonMapper timestampMapper = JsonMapper.builder()
                .enable(
                        DateTimeFeature
                                .WRITE_DATES_AS_TIMESTAMPS
                )
                .build();

        String json = timestampMapper.writeValueAsString(
                validResponse(null)
        );

        assertThat(json)
                .contains(
                        "\"timestamp\":"
                                + "\"2026-07-30T10:42:15.018Z\""
                );
    }

    @Test
    void readsUnknownFieldsAndMissingOptionalDetails() {
        String json = """
                {
                  "errorId": "7c12c42e-86ee-43b0-8324-9a56bf633ed4",
                  "timestamp": "2026-07-30T10:42:15.018Z",
                  "status": 404,
                  "message": "Компонент не найден",
                  "errorType": "COMPONENT_NOT_FOUND",
                  "currentService": "service-a",
                  "futureRootField": "ignored",
                  "chain": [
                    {
                      "service": "service-b",
                      "component": "ComponentCatalog",
                      "operation": "findComponentById",
                      "exceptionType": "COMPONENT_NOT_FOUND",
                      "message": "Компонент не найден",
                      "timestamp": "2026-07-30T10:42:15.018Z",
                      "futureChainField": true
                    }
                  ]
                }
                """;

        ErrorResponse response = jsonMapper.readValue(
                json,
                ErrorResponse.class
        );

        assertThat(response.getErrorId()).isEqualTo(ERROR_ID);
        assertThat(response.getHttpStatus()).isEqualTo(404);
        assertThat(response.getDetails()).isNull();
        assertThat(response.getChain()).hasSize(1);
        assertThat(
                response.getChain()
                        .getFirst()
                        .getHttpStatus()
        ).isNull();
    }

    @Test
    void readsLegacyAliases() {
        String legacyJson = """
                {
                  "error_id": "7c12c42e-86ee-43b0-8324-9a56bf633ed4",
                  "timestamp": "2026-07-30T10:42:15.018Z",
                  "status": 404,
                  "message": "Компонент не найден",
                  "type_exception": "COMPONENT_NOT_FOUND",
                  "current_service": "service-a",
                  "chain": [
                    {
                      "service": "service-b",
                      "component": "ComponentCatalog",
                      "operation": "findComponentById",
                      "exception_type": "COMPONENT_NOT_FOUND",
                      "publicMessage": "Компонент не найден",
                      "timestamp": "2026-07-30T10:42:15.018Z",
                      "httpStatus": 404
                    }
                  ],
                  "details": {
                    "fieldErrors": [
                      {
                        "field": "name",
                        "code": "REQUIRED",
                        "publicMessage": "Название обязательно"
                      }
                    ],
                    "retry_after_seconds": 10
                  }
                }
                """;

        ErrorResponse response = jsonMapper.readValue(
                legacyJson,
                ErrorResponse.class
        );

        assertThat(response.getErrorCode())
                .isEqualTo("COMPONENT_NOT_FOUND");

        assertThat(response.getCurrentService())
                .isEqualTo("service-a");

        assertThat(
                response.getChain()
                        .getFirst()
                        .getCauseCode()
        ).isEqualTo("COMPONENT_NOT_FOUND");

        assertThat(
                response.getDetails().getViolations()
        ).containsExactly(
                ErrorDetails.FieldViolation.of(
                        "name",
                        "REQUIRED",
                        "Название обязательно"
                )
        );

        assertThat(
                response.getDetails()
                        .getRetryAfterSeconds()
        ).isEqualTo(10L);
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

        assertThat(violation.getPublicMessage())
                .isEqualTo("Название обязательно");
    }

    @Test
    void snapshotsInputCollectionsAndReturnsUnmodifiableLists() {
        List<ChainElement> mutableChain =
                new ArrayList<>();

        mutableChain.add(validChainElement());

        ErrorResponse response = ErrorResponse.builder()
                .errorId(ERROR_ID)
                .timestamp(ERROR_TIME)
                .httpStatus(404)
                .publicMessage("Компонент не найден")
                .errorCode("COMPONENT_NOT_FOUND")
                .currentService("service-a")
                .chain(mutableChain)
                .build();

        mutableChain.clear();

        assertThat(response.getChain()).hasSize(1);

        assertThatThrownBy(
                () -> response.getChain().clear()
        ).isInstanceOf(
                UnsupportedOperationException.class
        );
    }

    @Test
    void snapshotsViolationsAndReturnsUnmodifiableList() {
        List<ErrorDetails.FieldViolation> violations =
                new ArrayList<>();

        violations.add(
                ErrorDetails.FieldViolation.of(
                        "name",
                        "REQUIRED",
                        "Название обязательно"
                )
        );

        ErrorDetails details = ErrorDetails.builder()
                .violations(violations)
                .build();

        violations.clear();

        assertThat(details.getViolations()).hasSize(1);

        assertThatThrownBy(
                () -> details.getViolations().clear()
        ).isInstanceOf(
                UnsupportedOperationException.class
        );
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> ErrorResponse.builder()
                .timestamp(ERROR_TIME)
                .httpStatus(404)
                .publicMessage("Компонент не найден")
                .errorCode("COMPONENT_NOT_FOUND")
                .currentService("service-a")
                .chain(List.of(validChainElement()))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorId");

        assertThatThrownBy(() -> ErrorResponse.builder()
                .errorId(ERROR_ID)
                .timestamp(ERROR_TIME)
                .httpStatus(404)
                .publicMessage("Компонент не найден")
                .errorCode("COMPONENT_NOT_FOUND")
                .currentService("service-a")
                .chain(List.of())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "chain must not be empty"
                );
    }

    @Test
    void rejectsNonErrorHttpStatuses() {
        assertThatThrownBy(() -> ErrorResponse.builder()
                .errorId(ERROR_ID)
                .timestamp(ERROR_TIME)
                .httpStatus(200)
                .publicMessage("Ошибка")
                .errorCode("INTERNAL_ERROR")
                .currentService("service-a")
                .chain(List.of(validChainElement()))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("400 to 599");

        assertThatThrownBy(() -> ChainElement.builder()
                .service("service-b")
                .component("ComponentCatalog")
                .operation("findComponentById")
                .causeCode("COMPONENT_NOT_FOUND")
                .publicMessage("Компонент не найден")
                .timestamp(ERROR_TIME)
                .httpStatus(302)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("400 to 599");
    }

    @Test
    void rejectsJavaExceptionTypesAndInvalidCodes() {
        assertThatThrownBy(() -> ChainElement.builder()
                .service("service-b")
                .component("ComponentCatalog")
                .operation("findComponentById")
                .causeCode(
                        "DataIntegrityViolationException"
                )
                .publicMessage("Компонент не найден")
                .timestamp(ERROR_TIME)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("public code");

        assertThatThrownBy(() -> ErrorResponse.builder()
                .errorId(ERROR_ID)
                .timestamp(ERROR_TIME)
                .httpStatus(500)
                .publicMessage("Внутренняя ошибка")
                .errorCode("NULLPOINTEREXCEPTION")
                .currentService("service-a")
                .chain(List.of(validChainElement()))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Java exception type"
                );

        assertThatThrownBy(
                () -> ErrorDetails.FieldViolation.of(
                        "name",
                        "not-valid",
                        "Некорректное значение"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("public code");
    }

    @Test
    void rejectsControlCharacters() {
        assertThatThrownBy(() -> ChainElement.builder()
                .service("service-b")
                .component("ComponentCatalog")
                .operation("findComponentById")
                .causeCode("COMPONENT_NOT_FOUND")
                .publicMessage("Компонент\nне найден")
                .timestamp(ERROR_TIME)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "control characters"
                );
    }

    @Test
    void rejectsEmptyDetailsNegativeRetryAndOversizedLists() {
        assertThatThrownBy(
                () -> ErrorDetails.builder().build()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "at least one public value"
                );

        assertThatThrownBy(() -> ErrorDetails.builder()
                .retryAfterSeconds(-1L)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "greater than or equal to zero"
                );

        List<ErrorDetails.FieldViolation>
                tooManyViolations = new ArrayList<>();

        for (int index = 0; index < 101; index++) {
            tooManyViolations.add(
                    ErrorDetails.FieldViolation.of(
                            "field" + index,
                            "INVALID",
                            "Некорректное значение"
                    )
            );
        }

        assertThatThrownBy(() -> ErrorDetails.builder()
                .violations(tooManyViolations)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "more than 100 elements"
                );
    }

    @Test
    void supportsValueSemanticsAfterJsonRoundTrip() {
        ErrorResponse original = validResponse(
                ErrorDetails.builder()
                        .resource("COMPONENT")
                        .build()
        );

        ErrorResponse restored = jsonMapper.readValue(
                jsonMapper.writeValueAsString(original),
                ErrorResponse.class
        );

        assertThat(restored).isEqualTo(original);
        assertThat(restored.hashCode())
                .isEqualTo(original.hashCode());
    }

    private static ErrorResponse validResponse(
            ErrorDetails details
    ) {
        return ErrorResponse.builder()
                .errorId(ERROR_ID)
                .timestamp(ERROR_TIME)
                .httpStatus(404)
                .publicMessage("Компонент не найден")
                .errorCode("COMPONENT_NOT_FOUND")
                .currentService("service-a")
                .chain(List.of(validChainElement()))
                .details(details)
                .build();
    }

    private static ChainElement validChainElement() {
        return ChainElement.builder()
                .service("service-b")
                .component("ComponentCatalog")
                .operation("findComponentById")
                .causeCode("COMPONENT_NOT_FOUND")
                .publicMessage("Компонент не найден")
                .timestamp(ERROR_TIME)
                .httpStatus(404)
                .build();
    }
}