package ru.oreoman4ik.catchup.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json
        .JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import ru.oreoman4ik.catchup.model.BusinessException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;
import tools.jackson.databind.json.JsonMapper;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

class UnifiedGlobalExceptionHandlerTests {

    private static final Instant ERROR_TIME = Instant.parse(
            "2026-07-31T10:20:30.123Z"
    );

    private JsonMapper jsonMapper;
    private MockMvc mockMvc;
    private UnifiedGlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();

        handler = new UnifiedGlobalExceptionHandler(
                "test-service",
                5
        );

        mockMvc = createMockMvc();
    }

    /*
     * ------------------------------------------------------------
     * BusinessException
     * ------------------------------------------------------------
     */

    @Test
    void handlesBusinessErrorWithDeclaredStatusAndDetails()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/business"),
                        409
                );

        assertThat(response.getStatus())
                .isEqualTo(409);

        assertThat(response.getErrorCode())
                .isEqualTo("BOOK_ALREADY_EXISTS");

        assertThat(response.getMessage())
                .isEqualTo("Книга уже существует");

        assertThat(response.getCurrentService())
                .isEqualTo("test-service");

        assertThat(response.getDetails())
                .isEqualTo(
                        ErrorDetails.builder()
                                .resource("BOOK")
                                .build()
                );

        assertThat(response.getChain())
                .hasSize(1);

        assertThat(
                response.getChain()
                        .getFirst()
                        .getComponent()
        ).isEqualTo("TestController");

        assertThat(
                response.getChain()
                        .getFirst()
                        .getOperation()
        ).isEqualTo("business");
    }

    /*
     * ------------------------------------------------------------
     * UnifiedErrorException
     * ------------------------------------------------------------
     */

    @Test
    void preservesStructuredUnifiedErrorAndAddsRestContext()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/unified"),
                        404
                );

        assertThat(response.getTimestamp())
                .isEqualTo(ERROR_TIME);

        assertThat(response.getStatus())
                .isEqualTo(404);

        assertThat(response.getErrorCode())
                .isEqualTo("COMPONENT_NOT_FOUND");

        assertThat(response.getMessage())
                .isEqualTo("Компонент не найден");

        assertThat(response.getCurrentService())
                .isEqualTo("test-service");

        assertThat(response.getDetails())
                .isEqualTo(
                        ErrorDetails.builder()
                                .resource("COMPONENT")
                                .build()
                );

        assertThat(response.getChain())
                .hasSize(2);

        assertThat(
                response.getChain()
                        .getFirst()
                        .getService()
        ).isEqualTo("remote-service");

        assertThat(
                response.getChain()
                        .getFirst()
                        .getComponent()
        ).isEqualTo("ComponentRepository");

        assertThat(
                response.getChain()
                        .getLast()
                        .getService()
        ).isEqualTo("test-service");

        assertThat(
                response.getChain()
                        .getLast()
                        .getComponent()
        ).isEqualTo("TestController");

        assertThat(
                response.getChain()
                        .getLast()
                        .getOperation()
        ).isEqualTo("unified");

        String json =
                jsonMapper.writeValueAsString(response);

        assertThat(json)
                .doesNotContain("secret database details")
                .doesNotContain("IllegalStateException");
    }

    /*
     * ------------------------------------------------------------
     * Bean validation
     * ------------------------------------------------------------
     */

    @Test
    void handlesValidationWithoutPublishingRejectedValue()
            throws Exception {

        MvcResult result = mockMvc
                .perform(get("/validation"))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getStatus())
                .isEqualTo(400);

        assertThat(response.getErrorCode())
                .isEqualTo("VALIDATION_ERROR");

        assertThat(response.getMessage())
                .isEqualTo(
                        "Переданные данные некорректны"
                );

        assertThat(response.getDetails())
                .isNotNull();

        assertThat(
                response.getDetails()
                        .getViolations()
        ).containsExactly(
                ErrorDetails.FieldViolation.of(
                        "password",
                        "REQUIRED",
                        "Поле обязательно"
                )
        );

        String json =
                result.getResponse()
                        .getContentAsString();

        assertThat(json)
                .doesNotContain("secret-password")
                .doesNotContain("must not be blank");
    }

    /*
     * ------------------------------------------------------------
     * HttpMessageNotReadableException
     * ------------------------------------------------------------
     */

    @Test
    void malformedJsonReturns400InsteadOf500()
            throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/json")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{broken-json")
                )
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getStatus())
                .isEqualTo(400);

        assertThat(response.getErrorCode())
                .isEqualTo("INVALID_REQUEST_BODY");

        assertThat(response.getMessage())
                .isEqualTo(
                        "Тело запроса имеет "
                                + "некорректный формат"
                );

        String json =
                result.getResponse()
                        .getContentAsString();

        assertThat(json)
                .doesNotContain("JsonParseException")
                .doesNotContain("Jackson")
                .doesNotContain("broken-json");
    }

    @Test
    void wrongJsonFieldTypeReturns400()
            throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/json")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "count": "not-a-number"
                                        }
                                        """
                                )
                )
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getStatus())
                .isEqualTo(400);

        assertThat(response.getErrorCode())
                .isEqualTo("INVALID_REQUEST_BODY");

        assertThat(response.getMessage())
                .isEqualTo(
                        "Тело запроса имеет "
                                + "некорректный формат"
                );

        assertThat(
                result.getResponse()
                        .getContentAsString()
        )
                .doesNotContain("not-a-number")
                .doesNotContain(
                        "NumberFormatException"
                );
    }

    @Test
    void emptyRequiredRequestBodyReturns400()
            throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/json")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                )
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getStatus())
                .isEqualTo(400);

        assertThat(response.getErrorCode())
                .isEqualTo("INVALID_REQUEST_BODY");

        assertThat(response.getMessage())
                .isEqualTo(
                        "Тело запроса имеет "
                                + "некорректный формат"
                );
    }

    /*
     * ------------------------------------------------------------
     * MethodArgumentTypeMismatchException
     * ------------------------------------------------------------
     */

    @Test
    void invalidQueryParameterReturns400()
            throws Exception {

        assertInvalidParameter(
                get("/convert/query")
                        .param("page", "abc"),
                "page"
        );
    }

    @Test
    void invalidPathVariableReturns400()
            throws Exception {

        assertInvalidParameter(
                get("/convert/path/not-a-uuid"),
                "id"
        );
    }

    @Test
    void invalidEnumReturns400()
            throws Exception {

        assertInvalidParameter(
                get("/convert/enum")
                        .param(
                                "mode",
                                "UNKNOWN_VALUE"
                        ),
                "mode"
        );
    }

    @Test
    void invalidDateReturns400()
            throws Exception {

        assertInvalidParameter(
                get("/convert/date")
                        .param(
                                "date",
                                "not-a-date"
                        ),
                "date"
        );
    }

    @Test
    void invalidUuidReturns400()
            throws Exception {

        assertInvalidParameter(
                get("/convert/uuid")
                        .param(
                                "id",
                                "not-a-uuid"
                        ),
                "id"
        );
    }

    @Test
    void invalidNumberReturns400()
            throws Exception {

        assertInvalidParameter(
                get("/convert/number")
                        .param(
                                "value",
                                "not-a-number"
                        ),
                "value"
        );
    }

    @Test
    void invalidBooleanReturns400()
            throws Exception {

        assertInvalidParameter(
                get("/convert/boolean")
                        .param(
                                "flag",
                                "definitely"
                        ),
                "flag"
        );
    }

    @Test
    void allSupportedConversionFailuresUseSameContract()
            throws Exception {

        List<ConversionCase> cases =
                List.of(
                        new ConversionCase(
                                get("/convert/query")
                                        .param(
                                                "page",
                                                "abc"
                                        ),
                                "page"
                        ),

                        new ConversionCase(
                                get(
                                        "/convert/path/"
                                                + "not-a-uuid"
                                ),
                                "id"
                        ),

                        new ConversionCase(
                                get("/convert/enum")
                                        .param(
                                                "mode",
                                                "UNKNOWN"
                                        ),
                                "mode"
                        ),

                        new ConversionCase(
                                get("/convert/date")
                                        .param(
                                                "date",
                                                "32-99-2026"
                                        ),
                                "date"
                        ),

                        new ConversionCase(
                                get("/convert/uuid")
                                        .param(
                                                "id",
                                                "invalid"
                                        ),
                                "id"
                        ),

                        new ConversionCase(
                                get("/convert/number")
                                        .param(
                                                "value",
                                                "abc"
                                        ),
                                "value"
                        ),

                        new ConversionCase(
                                get("/convert/boolean")
                                        .param(
                                                "flag",
                                                "maybe"
                                        ),
                                "flag"
                        )
                );

        for (ConversionCase conversionCase : cases) {
            MvcResult result = mockMvc
                    .perform(conversionCase.request())
                    .andExpect(status().isBadRequest())
                    .andReturn();

            ErrorResponse response =
                    readResponse(result);

            assertThat(response.getStatus())
                    .isEqualTo(400);

            assertThat(response.getErrorCode())
                    .isEqualTo("INVALID_PARAMETER");

            assertThat(response.getMessage())
                    .isEqualTo(
                            "Параметр запроса имеет "
                                    + "некорректный формат"
                    );

            assertThat(response.getDetails())
                    .isNotNull();

            assertThat(
                    response.getDetails()
                            .getViolations()
            ).containsExactly(
                    ErrorDetails.FieldViolation.of(
                            conversionCase.field(),
                            "INVALID_TYPE",
                            "Некорректный тип значения"
                    )
            );
        }
    }

    /*
     * ------------------------------------------------------------
     * Outgoing HTTP errors
     * ------------------------------------------------------------
     */

    @Test
    void preservesRemoteClientStatusButHidesRemoteBody()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/remote-client"),
                        404
                );

        assertThat(response.getStatus())
                .isEqualTo(404);

        assertThat(response.getErrorCode())
                .isEqualTo("REMOTE_CLIENT_ERROR");

        assertThat(response.getMessage())
                .isEqualTo(
                        "Удалённый сервис отклонил запрос"
                );

        String json =
                jsonMapper.writeValueAsString(response);

        assertThat(json)
                .doesNotContain("secret-token")
                .doesNotContain("internal_table")
                .doesNotContain(
                        "Remote database failure"
                );
    }

    @Test
    void preservesRemoteServerStatusButHidesRemoteBody()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/remote-server"),
                        503
                );

        assertThat(response.getStatus())
                .isEqualTo(503);

        assertThat(response.getErrorCode())
                .isEqualTo("REMOTE_SERVER_ERROR");

        assertThat(response.getMessage())
                .isEqualTo(
                        "Удалённый сервис завершил запрос "
                                + "с ошибкой"
                );

        String json =
                jsonMapper.writeValueAsString(response);

        assertThat(json)
                .doesNotContain("jdbc:postgresql")
                .doesNotContain("database_password")
                .doesNotContain(
                        "Remote internal error"
                );
    }

    @Test
    void mapsRemoteTimeoutToGatewayTimeout()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/timeout"),
                        504
                );

        assertThat(response.getStatus())
                .isEqualTo(504);

        assertThat(response.getErrorCode())
                .isEqualTo("REMOTE_TIMEOUT");

        assertThat(response.getMessage())
                .isEqualTo(
                        "Истекло время ожидания ответа "
                                + "удалённого сервиса"
                );

        String json =
                jsonMapper.writeValueAsString(response);

        assertThat(json)
                .doesNotContain("internal-host")
                .doesNotContain("secret")
                .doesNotContain("SocketTimeoutException");
    }

    /*
     * ------------------------------------------------------------
     * Standard Spring MVC errors
     * ------------------------------------------------------------
     */

    @Test
    void handlesResponseStatusException404WithSafeMessage()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/spring-not-found"),
                        404
                );

        assertThat(response.getStatus())
                .isEqualTo(404);

        assertThat(response.getErrorCode())
                .isEqualTo("RESOURCE_NOT_FOUND");

        assertThat(response.getMessage())
                .isEqualTo("Ресурс не найден");

        String json =
                jsonMapper.writeValueAsString(response);

        assertThat(json)
                .doesNotContain(
                        "/internal/storage/path"
                )
                .doesNotContain("database-id");
    }

    @Test
    void responseStatusAnnotationIsNotConvertedTo500()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/annotated-not-found"),
                        404
                );

        assertThat(response.getStatus())
                .isEqualTo(404);

        assertThat(response.getErrorCode())
                .isEqualTo("RESOURCE_NOT_FOUND");

        assertThat(response.getMessage())
                .isEqualTo("Ресурс не найден");

        String json =
                jsonMapper.writeValueAsString(response);

        assertThat(json)
                .doesNotContain(
                        "technical user lookup details"
                )
                .doesNotContain(
                        "UserNotFoundException"
                );
    }

    @Test
    void preservesAllowHeaderFor405()
            throws Exception {

        MvcResult result = mockMvc
                .perform(post("/get-only"))
                .andExpect(
                        status().isMethodNotAllowed()
                )
                .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getStatus())
                .isEqualTo(405);

        assertThat(response.getErrorCode())
                .isEqualTo("METHOD_NOT_ALLOWED");

        assertThat(response.getMessage())
                .isEqualTo(
                        "HTTP-метод не поддерживается"
                );

        assertThat(
                result.getResponse()
                        .getHeader(
                                HttpHeaders.ALLOW
                        )
        )
                .isNotNull()
                .contains("GET");
    }

    @Test
    void preservesAcceptHeaderFor415()
            throws Exception {

        MvcResult result = mockMvc.perform(
                        post("/json")
                                .contentType(
                                        MediaType.APPLICATION_XML
                                )
                                .content("<request/>")
                )
                .andExpect(
                        status()
                                .isUnsupportedMediaType()
                )
                .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getStatus())
                .isEqualTo(415);

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "UNSUPPORTED_MEDIA_TYPE"
                );

        String acceptHeader =
                result.getResponse()
                        .getHeader(
                                HttpHeaders.ACCEPT
                        );

        assertThat(acceptHeader)
                .isNotNull()
                .contains("application/json");
    }

    @Test
    void preservesHeadersFromSpringErrorResponse()
            throws Exception {

        MvcResult result = mockMvc
                .perform(get("/spring-header"))
                .andExpect(
                        status()
                                .isServiceUnavailable()
                )
                .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getStatus())
                .isEqualTo(503);

        assertThat(response.getErrorCode())
                .isEqualTo("SERVER_ERROR");

        assertThat(response.getMessage())
                .isEqualTo(
                        "Внутренняя ошибка сервиса"
                );

        assertThat(
                result.getResponse()
                        .getHeader(
                                HttpHeaders.RETRY_AFTER
                        )
        ).isEqualTo("30");
    }

    @Test
    void preservesUnknownSpring5xxStatuses()
            throws Exception {

        for (int expectedStatus
                : List.of(501, 502, 503, 504)) {

            ErrorResponse response =
                    performError(
                            get(
                                    "/spring-5xx/{status}",
                                    expectedStatus
                            ),
                            expectedStatus
                    );

            assertThat(response.getStatus())
                    .isEqualTo(expectedStatus);

            assertThat(response.getErrorCode())
                    .isEqualTo("SERVER_ERROR");

            assertThat(response.getMessage())
                    .isEqualTo(
                            "Внутренняя ошибка сервиса"
                    );
        }
    }

    /*
     * ------------------------------------------------------------
     * Unexpected errors
     * ------------------------------------------------------------
     */

    @Test
    void unexpectedExceptionReturnsSafe500()
            throws Exception {

        MvcResult result = mockMvc
                .perform(get("/unexpected"))
                .andExpect(
                        status()
                                .isInternalServerError()
                )
                .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getStatus())
                .isEqualTo(500);

        assertThat(response.getErrorCode())
                .isEqualTo("INTERNAL_ERROR");

        assertThat(response.getMessage())
                .isEqualTo(
                        "Внутренняя ошибка сервиса"
                );

        assertThat(response.getDetails())
                .isNull();

        String json =
                result.getResponse()
                        .getContentAsString();

        assertThat(json)
                .doesNotContain("select *")
                .doesNotContain("secret_token")
                .doesNotContain("IllegalStateException");
    }

    /*
     * ------------------------------------------------------------
     * Handler priorities
     * ------------------------------------------------------------
     */

    @Test
    void controllerLocalHandlerHasPriorityOverGlobalHandler()
            throws Exception {

        MvcResult result = mockMvc
                .perform(get("/custom"))
                .andExpect(
                        status().isIAmATeapot()
                )
                .andReturn();

        String body =
                result.getResponse()
                        .getContentAsString();

        assertThat(body)
                .contains(
                        "\"source\":\"custom-handler\""
                )
                .doesNotContain("\"errorId\"")
                .doesNotContain("\"errorCode\"");
    }

    @Test
    void globalHandlerRunsBeforeBootProblemDetailsHandler() {
        Order order = UnifiedGlobalExceptionHandler
                .class
                .getAnnotation(Order.class);

        assertThat(order).isNotNull();

        assertThat(order.value())
                .isEqualTo(
                        UnifiedGlobalExceptionHandler
                                .HANDLER_ORDER
                );

        assertThat(order.value())
                .isEqualTo(-1);
    }

    @Test
    void unorderedUserAdviceDoesNotOverrideLibraryHandler()
            throws Exception {

        MockMvc mvc = createMockMvc(
                new UnorderedApplicationAdvice()
        );

        MvcResult result = mvc
                .perform(get("/advice-target"))
                .andExpect(
                        status()
                                .isInternalServerError()
                )
                .andReturn();

        ErrorResponse response =
                jsonMapper.readValue(
                        result.getResponse()
                                .getContentAsString(),
                        ErrorResponse.class
                );

        assertThat(response.getStatus())
                .isEqualTo(500);

        assertThat(response.getErrorCode())
                .isEqualTo("INTERNAL_ERROR");

        assertThat(
                result.getResponse()
                        .getContentAsString()
        )
                .doesNotContain(
                        "unordered-user-advice"
                );
    }

    @Test
    void higherPriorityUserAdviceOverridesLibraryHandler()
            throws Exception {

        MockMvc mvc = createMockMvc(
                new HigherPriorityApplicationAdvice()
        );

        MvcResult result = mvc
                .perform(get("/advice-target"))
                .andExpect(
                        status()
                                .isUnprocessableContent()
                )
                .andReturn();

        String json =
                result.getResponse()
                        .getContentAsString();

        assertThat(json)
                .contains(
                        "\"source\":"
                                + "\"high-priority-advice\""
                )
                .doesNotContain("\"errorId\"")
                .doesNotContain("\"errorCode\"");
    }

    /*
     * ------------------------------------------------------------
     * Helpers
     * ------------------------------------------------------------
     */

    private MockMvc createMockMvc(
            Object... additionalAdvices
    ) {
        Object[] advices =
                new Object[
                        additionalAdvices.length + 1
                        ];

        advices[0] = handler;

        System.arraycopy(
                additionalAdvices,
                0,
                advices,
                1,
                additionalAdvices.length
        );

        return MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(advices)
                .setMessageConverters(
                        new JacksonJsonHttpMessageConverter(
                                jsonMapper
                        )
                )
                .build();
    }

    private void assertInvalidParameter(
            RequestBuilder request,
            String expectedField
    ) throws Exception {

        MvcResult result = mockMvc
                .perform(request)
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getStatus())
                .isEqualTo(400);

        assertThat(response.getErrorCode())
                .isEqualTo("INVALID_PARAMETER");

        assertThat(response.getMessage())
                .isEqualTo(
                        "Параметр запроса имеет "
                                + "некорректный формат"
                );

        assertThat(response.getDetails())
                .isNotNull();

        assertThat(
                response.getDetails()
                        .getViolations()
        ).containsExactly(
                ErrorDetails.FieldViolation.of(
                        expectedField,
                        "INVALID_TYPE",
                        "Некорректный тип значения"
                )
        );

        String json =
                result.getResponse()
                        .getContentAsString();

        /*
         * Проверяем, что технический Java-тип
         * не попадает в ответ.
         */
        assertThat(json)
                .doesNotContain("Integer")
                .doesNotContain("UUID")
                .doesNotContain("LocalDate")
                .doesNotContain("IllegalArgumentException")
                .doesNotContain(
                        "MethodArgumentTypeMismatchException"
                );
    }

    private ErrorResponse performError(
            RequestBuilder request,
            int expectedStatus
    ) throws Exception {

        MvcResult result = mockMvc
                .perform(request)
                .andExpect(
                        status().is(expectedStatus)
                )
                .andReturn();

        return readResponse(result);
    }

    private ErrorResponse readResponse(
            MvcResult result
    ) throws UnsupportedEncodingException {
        return jsonMapper.readValue(
                result.getResponse()
                        .getContentAsString(),
                ErrorResponse.class
        );
    }

    private static MethodArgumentNotValidException
    createValidationException() {

        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(
                        new Object(),
                        "request"
                );

        bindingResult.addError(
                new FieldError(
                        "request",
                        "password",
                        "secret-password",
                        false,
                        new String[]{"NotBlank"},
                        null,
                        "must not be blank"
                )
        );

        try {
            Method method =
                    ValidationMethodHolder.class
                            .getDeclaredMethod(
                                    "validate",
                                    String.class
                            );

            MethodParameter parameter =
                    new MethodParameter(
                            method,
                            0
                    );

            return new MethodArgumentNotValidException(
                    parameter,
                    bindingResult
            );
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(
                    "Validation test method not found",
                    exception
            );
        }
    }

    private static ChainElement originContext() {
        return ChainElement.builder()
                .service("remote-service")
                .component("ComponentRepository")
                .operation("findById")
                .errorCode("COMPONENT_NOT_FOUND")
                .message("Компонент не найден")
                .timestamp(ERROR_TIME)
                .status(404)
                .build();
    }

    /*
     * ------------------------------------------------------------
     * Test controller
     * ------------------------------------------------------------
     */

    @RestController
    private static final class TestController {

        @GetMapping("/business")
        String business() {
            throw new BusinessException(
                    409,
                    "BOOK_ALREADY_EXISTS",
                    "Книга уже существует",
                    ErrorDetails.builder()
                            .resource("BOOK")
                            .build()
            );
        }

        @GetMapping("/unified")
        String unified() {
            throw UnifiedErrorException.from(
                    new IllegalStateException(
                            "secret database details"
                    ),
                    ERROR_TIME,
                    404,
                    "COMPONENT_NOT_FOUND",
                    "Компонент не найден",
                    ErrorDetails.builder()
                            .resource("COMPONENT")
                            .build(),
                    originContext(),
                    5
            );
        }

        @GetMapping("/validation")
        String validation()
                throws MethodArgumentNotValidException {

            throw createValidationException();
        }

        @PostMapping("/json")
        String json(
                @RequestBody JsonRequest request
        ) {
            return "ok";
        }

        @GetMapping("/convert/query")
        String query(
                @RequestParam("page")
                Integer page
        ) {
            return "ok";
        }

        @GetMapping("/convert/path/{id}")
        String path(
                @PathVariable("id")
                UUID id
        ) {
            return "ok";
        }

        @GetMapping("/convert/enum")
        String enumValue(
                @RequestParam("mode")
                TestMode mode
        ) {
            return "ok";
        }

        @GetMapping("/convert/date")
        String date(
                @RequestParam("date")
                @DateTimeFormat(
                        iso =
                                DateTimeFormat.ISO.DATE
                )
                LocalDate date
        ) {
            return "ok";
        }

        @GetMapping("/convert/uuid")
        String uuid(
                @RequestParam("id")
                UUID id
        ) {
            return "ok";
        }

        @GetMapping("/convert/number")
        String number(
                @RequestParam("value")
                Long value
        ) {
            return "ok";
        }

        @GetMapping("/convert/boolean")
        String bool(
                @RequestParam("flag")
                Boolean flag
        ) {
            return "ok";
        }

        @GetMapping("/remote-client")
        String remoteClient() {
            throw new HttpClientErrorException(
                    HttpStatus.NOT_FOUND,
                    "Remote database failure",
                    HttpHeaders.EMPTY,
                    (
                            "select * from internal_table "
                                    + "where "
                                    + "token=secret-token"
                    ).getBytes(
                            StandardCharsets.UTF_8
                    ),
                    StandardCharsets.UTF_8
            );
        }

        @GetMapping("/remote-server")
        String remoteServer() {
            throw new RestClientResponseException(
                    "Remote internal error",
                    503,
                    "Service Unavailable",
                    HttpHeaders.EMPTY,
                    (
                            "jdbc:postgresql://internal-db "
                                    + "database_password=secret"
                    ).getBytes(
                            StandardCharsets.UTF_8
                    ),
                    StandardCharsets.UTF_8
            );
        }

        @GetMapping("/timeout")
        String timeout() {
            throw new ResourceAccessException(
                    "GET http://internal-host"
                            + "?token=secret",
                    new SocketTimeoutException(
                            "Read timed out "
                                    + "for internal-host"
                    )
            );
        }

        @GetMapping("/spring-not-found")
        String springNotFound() {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Missing "
                            + "/internal/storage/path "
                            + "with database-id=42"
            );
        }

        @GetMapping("/annotated-not-found")
        String annotatedNotFound() {
            throw new UserNotFoundException(
                    "technical user lookup details"
            );
        }

        @GetMapping("/get-only")
        String getOnly() {
            return "ok";
        }

        @GetMapping("/spring-header")
        String springHeader() {
            throw new RetryAfterServiceUnavailableException();
        }

        @GetMapping("/spring-5xx/{status}")
        String spring5xx(
                @PathVariable("status")
                int status
        ) {
            throw new ErrorResponseException(
                    HttpStatusCode.valueOf(status)
            );
        }

        @GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException(
                    "select * from users "
                            + "where "
                            + "secret_token='secret'"
            );
        }

        @GetMapping("/custom")
        String custom() {
            throw new CustomApplicationException();
        }

        @GetMapping("/advice-target")
        String adviceTarget() {
            throw new AdviceTargetException();
        }

        @ExceptionHandler(
                CustomApplicationException.class
        )
        ResponseEntity<Map<String, String>>
        handleCustomApplicationError() {

            return ResponseEntity
                    .status(
                            HttpStatus.I_AM_A_TEAPOT
                    )
                    .body(
                            Map.of(
                                    "source",
                                    "custom-handler"
                            )
                    );
        }
    }

    /*
     * ------------------------------------------------------------
     * Test DTOs and exceptions
     * ------------------------------------------------------------
     */

    private record JsonRequest(
            Integer count
    ) {
    }

    private enum TestMode {
        FIRST,
        SECOND
    }

    private static final class ValidationMethodHolder {

        private static void validate(
                String value
        ) {
        }
    }

    private static final class CustomApplicationException
            extends RuntimeException {
    }

    private static final class AdviceTargetException
            extends RuntimeException {
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static final class UserNotFoundException
            extends RuntimeException {

        private UserNotFoundException(
                String message
        ) {
            super(message);
        }
    }

    private static final class
    RetryAfterServiceUnavailableException
            extends ErrorResponseException {

        private final HttpHeaders headers;

        private RetryAfterServiceUnavailableException() {
            super(
                    HttpStatus.SERVICE_UNAVAILABLE
            );

            this.headers = new HttpHeaders();
            this.headers.set(
                    HttpHeaders.RETRY_AFTER,
                    "30"
            );
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

    /*
     * ------------------------------------------------------------
     * Test ControllerAdvice implementations
     * ------------------------------------------------------------
     */

    @RestControllerAdvice
    private static final class
    UnorderedApplicationAdvice {

        @ExceptionHandler(
                AdviceTargetException.class
        )
        ResponseEntity<Map<String, String>>
        handle(AdviceTargetException exception) {

            return ResponseEntity
                    .status(
                            HttpStatus.UNPROCESSABLE_CONTENT
                    )
                    .body(
                            Map.of(
                                    "source",
                                    "unordered-user-advice"
                            )
                    );
        }
    }

    @RestControllerAdvice
    @Order(-2)
    private static final class
    HigherPriorityApplicationAdvice {

        @ExceptionHandler(
                AdviceTargetException.class
        )
        ResponseEntity<Map<String, String>>
        handle(AdviceTargetException exception) {

            return ResponseEntity
                    .status(
                            HttpStatus.UNPROCESSABLE_CONTENT
                    )
                    .body(
                            Map.of(
                                    "source",
                                    "high-priority-advice"
                            )
                    );
        }
    }

    private record ConversionCase(
            RequestBuilder request,
            String field
    ) {
    }
}