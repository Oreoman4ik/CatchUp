package ru.oreoman4ik.catchup.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ErrorResponseJsonTests {

    private static final UUID ERROR_ID = UUID.fromString(
            "7c12c42e-86ee-43b0-8324-9a56bf633ed4"
    );

    private static final Instant ERROR_TIME = Instant.parse(
            "2026-07-30T10:42:15.018Z"
    );

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void serializesCanonicalPublicContract() throws Exception {
        ErrorResponse response = validResponse(
                ErrorDetails.builder()
                        .resource("COMPONENT")
                        .build()
        );

        String json = jsonMapper.writeValueAsString(response);

        assertThat(json)
                .contains("\"errorId\":\"" + ERROR_ID + "\"")
                .contains("\"timestamp\":\"2026-07-30T10:42:15.018Z\"")
                .contains("\"status\":404")
                .contains("\"message\":\"Компонент не найден\"")
                .contains("\"errorType\":\"COMPONENT_NOT_FOUND\"")
                .contains("\"currentService\":\"service-a\"")
                .contains("\"exceptionType\":\"COMPONENT_NOT_FOUND\"")
                .contains("\"resource\":\"COMPONENT\"")
                .doesNotContain("\"status\":\"404\"")
                .doesNotContain("\"httpStatus\"")
                .doesNotContain("\"publicMessage\"")
                .doesNotContain("\"errorCode\"")
                .doesNotContain("\"causeCode\"")
                .doesNotContain("org.springframework")
                .doesNotContain("java.lang");
    }

    @Test
    void omitsOptionalFieldsWhenTheyAreAbsent() throws Exception {
        ChainElement chainElement = ChainElement.builder()
                .service("service-b")
                .component("ComponentCatalog")
                .operation("findComponentById")
                .causeCode("COMPONENT_NOT_FOUND")
                .publicMessage("Компонент не найден")
                .timestamp(ERROR_TIME)
                .build();

        ErrorResponse response = ErrorResponse.builder()
                .errorId(ERROR_ID)
                .timestamp(ERROR_TIME)
                .httpStatus(404)
                .publicMessage("Компонент не найден")
                .errorCode("COMPONENT_NOT_FOUND")
                .currentService("service-a")
                .chain(List.of(chainElement))
                .build();

        String json = jsonMapper.writeValueAsString(response);

        assertThat(json)
                .doesNotContain("\"details\"")
                .doesNotContain("\"status\":null");
    }

    @Test
    void readsUnknownFieldsAndMissingOptionalDetails()
            throws Exception {

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
        assertThat(response.getErrorCode())
                .isEqualTo("COMPONENT_NOT_FOUND");
        assertThat(response.getDetails()).isNull();
        assertThat(response.getChain()).hasSize(1);
        assertThat(response.getChain().getFirst().getHttpStatus())
                .isNull();
    }

    @Test
    void readsUnknownFieldsInsideDetailsAndViolations()
            throws Exception {

        String json = """
                {
                  "errorId": "7c12c42e-86ee-43b0-8324-9a56bf633ed4",
                  "timestamp": "2026-07-30T10:42:15.018Z",
                  "status": 400,
                  "message": "Ошибка валидации",
                  "errorType": "VALIDATION_ERROR",
                  "currentService": "service-a",
                  "chain": [
                    {
                      "service": "service-a",
                      "component": "ComponentController",
                      "operation": "createComponent",
                      "exceptionType": "VALIDATION_ERROR",
                      "message": "Ошибка валидации",
                      "timestamp": "2026-07-30T10:42:15.018Z",
                      "status": 400
                    }
                  ],
                  "details": {
                    "resource": "COMPONENT",
                    "futureDetailsField": "ignored",
                    "violations": [
                      {
                        "field": "name",
                        "reasonCode": "REQUIRED",
                        "message": "Название обязательно",
                        "rejectedValue": "ignored"
                      }
                    ]
                  }
                }
                """;

        ErrorResponse response = jsonMapper.readValue(
                json,
                ErrorResponse.class
        );

        assertThat(response.getDetails()).isNotNull();
        assertThat(response.getDetails().getResource())
                .isEqualTo("COMPONENT");
        assertThat(response.getDetails().getViolations())
                .hasSize(1);

        ErrorDetails.FieldViolation violation =
                response.getDetails().getViolations().getFirst();

        assertThat(violation.getField()).isEqualTo("name");
        assertThat(violation.getReasonCode()).isEqualTo("REQUIRED");
        assertThat(violation.getPublicMessage())
                .isEqualTo("Название обязательно");
    }

    @Test
    void readsLegacyAliases() throws Exception {
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
                    "resource": "COMPONENT",
                    "fieldErrors": [
                      {
                        "field": "name",
                        "code": "REQUIRED",
                        "publicMessage": "Название обязательно"
                      }
                    ],
                    "retry_after_seconds": 30
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

        assertThat(response.getChain().getFirst().getCauseCode())
                .isEqualTo("COMPONENT_NOT_FOUND");

        assertThat(response.getChain().getFirst().getPublicMessage())
                .isEqualTo("Компонент не найден");

        assertThat(response.getDetails()).isNotNull();
        assertThat(response.getDetails().getRetryAfterSeconds())
                .isEqualTo(30L);
        assertThat(response.getDetails().getViolations())
                .hasSize(1);
    }

    @Test
    void olderTolerantClientCanReadNewResponse() throws Exception {
        String newJson = jsonMapper.writeValueAsString(
                validResponse(
                        ErrorDetails.builder()
                                .resource("COMPONENT")
                                .retryAfterSeconds(30L)
                                .build()
                )
        );

        LegacyClientView legacyView = jsonMapper.readValue(
                newJson,
                LegacyClientView.class
        );

        assertThat(legacyView.errorId).isEqualTo(ERROR_ID);
        assertThat(legacyView.status).isEqualTo(404);
        assertThat(legacyView.errorType)
                .isEqualTo("COMPONENT_NOT_FOUND");
    }

    @Test
    void snapshotsChainAndReturnsUnmodifiableList() {
        List<ChainElement> mutableChain = new ArrayList<>();
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

        assertThatThrownBy(() -> response.getChain().clear())
                .isInstanceOf(UnsupportedOperationException.class);
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
                .hasMessageContaining("chain must not be empty");

        assertThatThrownBy(() -> ChainElement.builder()
                .service("service-b")
                .component("ComponentCatalog")
                .operation("findComponentById")
                .causeCode("COMPONENT_NOT_FOUND")
                .publicMessage("Компонент не найден")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    void rejectsInvalidHttpStatuses() {
        assertThatThrownBy(() -> ErrorResponse.builder()
                .errorId(ERROR_ID)
                .timestamp(ERROR_TIME)
                .httpStatus(99)
                .publicMessage("Компонент не найден")
                .errorCode("COMPONENT_NOT_FOUND")
                .currentService("service-a")
                .chain(List.of(validChainElement()))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100 to 599");

        assertThatThrownBy(() -> ChainElement.builder()
                .service("service-b")
                .component("ComponentCatalog")
                .operation("findComponentById")
                .causeCode("COMPONENT_NOT_FOUND")
                .publicMessage("Компонент не найден")
                .timestamp(ERROR_TIME)
                .httpStatus(600)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100 to 599");
    }

    @Test
    void rejectsInvalidPublicCodes() {
        assertThatThrownBy(() -> ChainElement.builder()
                .service("service-b")
                .component("ComponentCatalog")
                .operation("findComponentById")
                .causeCode(
                        "org.springframework.dao."
                                + "DataIntegrityViolationException"
                )
                .publicMessage("Компонент не найден")
                .timestamp(ERROR_TIME)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceptionType");

        assertThatThrownBy(() -> ErrorResponse.builder()
                .errorId(ERROR_ID)
                .timestamp(ERROR_TIME)
                .httpStatus(404)
                .publicMessage("Компонент не найден")
                .errorCode("NOT-FOUND")
                .currentService("service-a")
                .chain(List.of(validChainElement()))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorType");
    }

    @Test
    void rejectsBlankTextsAndControlCharacters() {
        assertThatThrownBy(() -> ChainElement.builder()
                .service(" ")
                .component("ComponentCatalog")
                .operation("findComponentById")
                .causeCode("COMPONENT_NOT_FOUND")
                .publicMessage("Компонент не найден")
                .timestamp(ERROR_TIME)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service must not be blank");

        assertThatThrownBy(() -> ChainElement.builder()
                .service("service-b")
                .component("ComponentCatalog")
                .operation("findComponentById")
                .causeCode("COMPONENT_NOT_FOUND")
                .publicMessage("Компонент\nне найден")
                .timestamp(ERROR_TIME)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control characters");
    }

    @Test
    void rejectsEmptyDetailsAndNegativeRetryDelay() {
        assertThatThrownBy(() -> ErrorDetails.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one public value");

        assertThatThrownBy(() -> ErrorDetails.builder()
                .resource("COMPONENT")
                .retryAfterSeconds(-1L)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "must be greater than or equal to zero"
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
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void doesNotSerializeRejectedFieldValues() throws Exception {
        ErrorDetails details = ErrorDetails.builder()
                .violations(
                        List.of(
                                ErrorDetails.FieldViolation.of(
                                        "password",
                                        "INVALID",
                                        "Некорректное значение"
                                )
                        )
                )
                .build();

        String json = jsonMapper.writeValueAsString(
                validResponse(details)
        );

        assertThat(json)
                .contains("\"field\":\"password\"")
                .contains("\"reasonCode\":\"INVALID\"")
                .contains("\"message\":\"Некорректное значение\"")
                .doesNotContain("rejectedValue")
                .doesNotContain("fieldValue");
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

    /**
     * Имитация старого клиента, который игнорирует неизвестные
     * необязательные поля нового ответа.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class LegacyClientView {

        private final UUID errorId;
        private final int status;
        private final String errorType;

        @JsonCreator
        private LegacyClientView(
                @JsonProperty("errorId")
                UUID errorId,

                @JsonProperty("status")
                int status,

                @JsonProperty("errorType")
                String errorType
        ) {
            this.errorId = errorId;
            this.status = status;
            this.errorType = errorType;
        }
    }
}