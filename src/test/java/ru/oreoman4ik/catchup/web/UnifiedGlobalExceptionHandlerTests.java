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
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import ru.oreoman4ik.catchup.client.OutgoingHttpExceptionMapper;
import ru.oreoman4ik.catchup.model.BusinessException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;
import tools.jackson.databind.json.JsonMapper;

import java.io.EOFException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.SocketException;
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

    private static final Instant ERROR_TIME =
            Instant.parse(
                    "2026-07-31T10:20:30.123Z"
            );

    private JsonMapper jsonMapper;
    private MockMvc mockMvc;
    private UnifiedGlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        jsonMapper =
                JsonMapper.builder().build();

        OutgoingHttpExceptionMapper mapper =
                new OutgoingHttpExceptionMapper(
                        "test-service",
                        5
                );

        handler =
                new UnifiedGlobalExceptionHandler(
                        "test-service",
                        5,
                        mapper
                );

        mockMvc = createMockMvc();
    }

    /*
     * ------------------------------------------------------------
     * Business error
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
                .isEqualTo(
                        "BOOK_ALREADY_EXISTS"
                );

        assertThat(response.getMessage())
                .isEqualTo(
                        "Книга уже существует"
                );

        assertThat(response.getCurrentService())
                .isEqualTo(
                        "test-service"
                );

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
        ).isEqualTo(
                "TestController"
        );

        assertThat(
                response.getChain()
                        .getFirst()
                        .getOperation()
        ).isEqualTo(
                "business"
        );
    }

    /*
     * ------------------------------------------------------------
     * UnifiedErrorException
     * ------------------------------------------------------------
     */

    @Test
    void preservesUnifiedErrorAndAddsRestContext()
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
                .isEqualTo(
                        "COMPONENT_NOT_FOUND"
                );

        assertThat(response.getMessage())
                .isEqualTo(
                        "Компонент не найден"
                );

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
        ).isEqualTo(
                "remote-service"
        );

        assertThat(
                response.getChain()
                        .getLast()
                        .getService()
        ).isEqualTo(
                "test-service"
        );

        assertThat(
                response.getChain()
                        .getLast()
                        .getOperation()
        ).isEqualTo(
                "unified"
        );

        assertThat(
                jsonMapper.writeValueAsString(response)
        )
                .doesNotContain(
                        "secret database details"
                )
                .doesNotContain(
                        "IllegalStateException"
                );
    }

    /*
     * ------------------------------------------------------------
     * Validation
     * ------------------------------------------------------------
     */

    @Test
    void validationDoesNotPublishRejectedValue()
            throws Exception {

        MvcResult result =
                mockMvc
                        .perform(
                                get("/validation")
                        )
                        .andExpect(
                                status().isBadRequest()
                        )
                        .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getStatus())
                .isEqualTo(400);

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "VALIDATION_ERROR"
                );

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

        assertThat(
                result.getResponse()
                        .getContentAsString()
        )
                .doesNotContain(
                        "secret-password"
                )
                .doesNotContain(
                        "must not be blank"
                );
    }

    /*
     * ------------------------------------------------------------
     * Invalid request body
     * ------------------------------------------------------------
     */

    @Test
    void malformedJsonReturns400()
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/json")
                                        .contentType(
                                                MediaType
                                                        .APPLICATION_JSON
                                        )
                                        .content(
                                                "{broken-json"
                                        )
                        )
                        .andExpect(
                                status().isBadRequest()
                        )
                        .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getStatus())
                .isEqualTo(400);

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "INVALID_REQUEST_BODY"
                );

        assertThat(response.getMessage())
                .isEqualTo(
                        "Тело запроса имеет "
                                + "некорректный формат"
                );

        assertThat(
                result.getResponse()
                        .getContentAsString()
        )
                .doesNotContain("broken-json")
                .doesNotContain(
                        "JsonParseException"
                );
    }

    @Test
    void wrongJsonValueTypeReturns400()
            throws Exception {

        ErrorResponse response =
                performError(
                        post("/json")
                                .contentType(
                                        MediaType
                                                .APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "count": "not-a-number"
                                        }
                                        """
                                ),
                        400
                );

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "INVALID_REQUEST_BODY"
                );
    }

    @Test
    void emptyRequiredBodyReturns400()
            throws Exception {

        ErrorResponse response =
                performError(
                        post("/json")
                                .contentType(
                                        MediaType
                                                .APPLICATION_JSON
                                ),
                        400
                );

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "INVALID_REQUEST_BODY"
                );
    }

    /*
     * ------------------------------------------------------------
     * Parameter conversion
     * ------------------------------------------------------------
     */

    @Test
    void queryParameterConversionReturns400()
            throws Exception {

        assertInvalidParameter(
                get("/convert/query")
                        .param(
                                "page",
                                "abc"
                        ),
                "page"
        );
    }

    @Test
    void pathVariableConversionReturns400()
            throws Exception {

        assertInvalidParameter(
                get(
                        "/convert/path/not-a-uuid"
                ),
                "id"
        );
    }

    @Test
    void enumConversionReturns400()
            throws Exception {

        assertInvalidParameter(
                get("/convert/enum")
                        .param(
                                "mode",
                                "UNKNOWN"
                        ),
                "mode"
        );
    }

    @Test
    void dateConversionReturns400()
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
    void uuidConversionReturns400()
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
    void numberConversionReturns400()
            throws Exception {

        assertInvalidParameter(
                get("/convert/number")
                        .param(
                                "value",
                                "abc"
                        ),
                "value"
        );
    }

    @Test
    void booleanConversionReturns400()
            throws Exception {

        assertInvalidParameter(
                get("/convert/boolean")
                        .param(
                                "flag",
                                "maybe"
                        ),
                "flag"
        );
    }

    /*
     * ------------------------------------------------------------
     * Outgoing HTTP - common mapper
     * ------------------------------------------------------------
     */

    @Test
    void remote4xxUsesOutgoingMapper()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/remote-client"),
                        404
                );

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "REMOTE_CLIENT_ERROR"
                );

        assertThat(response.getMessage())
                .isEqualTo(
                        "Удалённый сервис отклонил запрос"
                );

        assertThat(
                jsonMapper.writeValueAsString(response)
        )
                .doesNotContain("secret-token")
                .doesNotContain("internal_table");
    }

    @Test
    void remote5xxUsesOutgoingMapper()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/remote-server"),
                        503
                );

        assertThat(response.getStatus())
                .isEqualTo(503);

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "REMOTE_SERVER_ERROR"
                );
    }

    @Test
    void timeoutUsesOutgoingMapper()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/timeout"),
                        504
                );

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "REMOTE_TIMEOUT"
                );

        assertThat(
                jsonMapper.writeValueAsString(response)
        )
                .doesNotContain("internal-host")
                .doesNotContain("secret");
    }

    @Test
    void connectionFailureUsesOutgoingMapper()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/connection-refused"),
                        503
                );

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "REMOTE_UNAVAILABLE"
                );
    }

    /**
     * Ключевой regression-тест:
     * тот же Connection reset должен классифицироваться
     * одинаково и через aspect, и через global handler.
     */
    @Test
    void networkResetUsesRemoteNetworkError()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/network-reset"),
                        502
                );

        assertThat(response.getStatus())
                .isEqualTo(502);

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "REMOTE_NETWORK_ERROR"
                );

        assertThat(response.getMessage())
                .isEqualTo(
                        "Ошибка сети при обращении "
                                + "к удалённому сервису"
                );
    }

    @Test
    void responseReadFailureUsesOutgoingMapper()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/response-read-error"),
                        502
                );

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "REMOTE_RESPONSE_READ_ERROR"
                );
    }

    @Test
    void requestBodySerializationFailureIsLocal500()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/request-body-error"),
                        500
                );

        assertThat(response.getStatus())
                .isEqualTo(500);

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "OUTGOING_REQUEST_BODY_ERROR"
                );

        assertThat(response.getMessage())
                .isEqualTo(
                        "Не удалось сформировать "
                                + "исходящий запрос"
                );

        assertThat(
                jsonMapper.writeValueAsString(response)
        )
                .doesNotContain(
                        "secret request token"
                );
    }

    @Test
    void responseBodyDeserializationFailureIs502()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/response-body-error"),
                        502
                );

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "REMOTE_BODY_CONVERSION_ERROR"
                );

        assertThat(response.getMessage())
                .isEqualTo(
                        "Не удалось преобразовать ответ "
                                + "удалённого сервиса"
                );
    }

    @Test
    void ambiguousConversionFailureIsLocal500()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/generic-conversion-error"),
                        500
                );

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "OUTGOING_HTTP_CONVERSION_ERROR"
                );
    }

    @Test
    void genericRestClientExceptionUsesNeutralCode()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/generic-http-error"),
                        502
                );

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "OUTGOING_HTTP_ERROR"
                );
    }

    /*
     * ------------------------------------------------------------
     * Standard Spring errors
     * ------------------------------------------------------------
     */

    @Test
    void responseStatusExceptionPreserves404()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/spring-not-found"),
                        404
                );

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "RESOURCE_NOT_FOUND"
                );

        assertThat(response.getMessage())
                .isEqualTo(
                        "Ресурс не найден"
                );
    }

    @Test
    void responseStatusAnnotationPreserves404()
            throws Exception {

        ErrorResponse response =
                performError(
                        get("/annotated-not-found"),
                        404
                );

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "RESOURCE_NOT_FOUND"
                );

        assertThat(
                jsonMapper.writeValueAsString(response)
        )
                .doesNotContain(
                        "technical user lookup details"
                );
    }

    @Test
    void preservesAllowHeaderFor405()
            throws Exception {

        MvcResult result =
                mockMvc
                        .perform(
                                post("/get-only")
                        )
                        .andExpect(
                                status()
                                        .isMethodNotAllowed()
                        )
                        .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "METHOD_NOT_ALLOWED"
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

        MvcResult result =
                mockMvc.perform(
                                post("/json")
                                        .contentType(
                                                MediaType
                                                        .APPLICATION_XML
                                        )
                                        .content(
                                                "<request/>"
                                        )
                        )
                        .andExpect(
                                status()
                                        .isUnsupportedMediaType()
                        )
                        .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "UNSUPPORTED_MEDIA_TYPE"
                );

        assertThat(
                result.getResponse()
                        .getHeader(
                                HttpHeaders.ACCEPT
                        )
        ).isNotNull();
    }

    @Test
    void preservesRetryAfterHeader()
            throws Exception {

        MvcResult result =
                mockMvc
                        .perform(
                                get("/spring-header")
                        )
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
                .isEqualTo(
                        "SERVER_ERROR"
                );

        assertThat(
                result.getResponse()
                        .getHeader(
                                HttpHeaders.RETRY_AFTER
                        )
        ).isEqualTo("30");
    }

    @Test
    void standardSpring5xxKeepsOriginalStatus()
            throws Exception {

        for (int expected
                : List.of(
                501,
                502,
                503,
                504
        )) {

            ErrorResponse response =
                    performError(
                            get(
                                    "/spring-5xx/{status}",
                                    expected
                            ),
                            expected
                    );

            assertThat(response.getStatus())
                    .isEqualTo(expected);

            assertThat(response.getErrorCode())
                    .isEqualTo(
                            "SERVER_ERROR"
                    );
        }
    }

    /*
     * ------------------------------------------------------------
     * Unknown error safety
     * ------------------------------------------------------------
     */

    @Test
    void unexpectedExceptionReturnsSafe500()
            throws Exception {

        MvcResult result =
                mockMvc
                        .perform(
                                get("/unexpected")
                        )
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
                .isEqualTo(
                        "INTERNAL_ERROR"
                );

        assertThat(response.getMessage())
                .isEqualTo(
                        "Внутренняя ошибка сервиса"
                );

        assertThat(
                result.getResponse()
                        .getContentAsString()
        )
                .doesNotContain("select *")
                .doesNotContain("secret_token")
                .doesNotContain(
                        "IllegalStateException"
                );
    }

    /*
     * ------------------------------------------------------------
     * Handler priority
     * ------------------------------------------------------------
     */

    @Test
    void localExceptionHandlerHasPriority()
            throws Exception {

        MvcResult result =
                mockMvc
                        .perform(
                                get("/custom")
                        )
                        .andExpect(
                                status().isIAmATeapot()
                        )
                        .andReturn();

        assertThat(
                result.getResponse()
                        .getContentAsString()
        )
                .contains(
                        "\"source\":\"custom-handler\""
                )
                .doesNotContain("\"errorId\"");
    }

    @Test
    void libraryAdviceHasExpectedOrder() {
        Order order =
                UnifiedGlobalExceptionHandler
                        .class
                        .getAnnotation(Order.class);

        assertThat(order).isNotNull();

        assertThat(order.value())
                .isEqualTo(-1);
    }

    @Test
    void unorderedAdviceDoesNotOverrideLibrary()
            throws Exception {

        MockMvc mvc =
                createMockMvc(
                        new UnorderedApplicationAdvice()
                );

        ErrorResponse response =
                readResponse(
                        mvc.perform(
                                        get("/advice-target")
                                )
                                .andExpect(
                                        status()
                                                .isInternalServerError()
                                )
                                .andReturn()
                );

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "INTERNAL_ERROR"
                );
    }

    @Test
    void higherPriorityAdviceOverridesLibrary()
            throws Exception {

        MockMvc mvc =
                createMockMvc(
                        new HigherPriorityApplicationAdvice()
                );

        MvcResult result =
                mvc.perform(
                                get("/advice-target")
                        )
                        .andExpect(
                                status()
                                        .isUnprocessableContent()
                        )
                        .andReturn();

        assertThat(
                result.getResponse()
                        .getContentAsString()
        )
                .contains(
                        "\"source\":"
                                + "\"high-priority-advice\""
                )
                .doesNotContain("\"errorId\"");
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
                .standaloneSetup(
                        new TestController()
                )
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

        MvcResult result =
                mockMvc
                        .perform(request)
                        .andExpect(
                                status().isBadRequest()
                        )
                        .andReturn();

        ErrorResponse response =
                readResponse(result);

        assertThat(response.getErrorCode())
                .isEqualTo(
                        "INVALID_PARAMETER"
                );

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

        assertThat(
                result.getResponse()
                        .getContentAsString()
        )
                .doesNotContain("Integer")
                .doesNotContain("UUID")
                .doesNotContain("LocalDate")
                .doesNotContain(
                        "MethodArgumentTypeMismatchException"
                );
    }

    private ErrorResponse performError(
            RequestBuilder request,
            int expectedStatus
    ) throws Exception {

        MvcResult result =
                mockMvc
                        .perform(request)
                        .andExpect(
                                status()
                                        .is(expectedStatus)
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
                    ValidationMethodHolder
                            .class
                            .getDeclaredMethod(
                                    "validate",
                                    String.class
                            );

            return new MethodArgumentNotValidException(
                    new MethodParameter(
                            method,
                            0
                    ),
                    bindingResult
            );

        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    private static ChainElement originContext() {
        return ChainElement.builder()
                .service("remote-service")
                .component(
                        "ComponentRepository"
                )
                .operation("findById")
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
                    "database_password=secret"
                            .getBytes(
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
                            "Read timed out"
                    )
            );
        }

        @GetMapping("/connection-refused")
        String connectionRefused() {
            throw new ResourceAccessException(
                    "connection failed",
                    new ConnectException(
                            "Connection refused"
                    )
            );
        }

        @GetMapping("/network-reset")
        String networkReset() {
            throw new ResourceAccessException(
                    "network failed",
                    new SocketException(
                            "Connection reset"
                    )
            );
        }

        @GetMapping("/response-read-error")
        String responseReadError() {
            throw new ResourceAccessException(
                    "response interrupted",
                    new EOFException(
                            "secret response"
                    )
            );
        }

        @GetMapping("/request-body-error")
        String requestBodyError() {
            throw new RestClientException(
                    "outgoing conversion failed",
                    new HttpMessageNotWritableException(
                            "secret request token"
                    )
            );
        }

        @GetMapping("/response-body-error")
        String responseBodyError() {
            throw new RestClientException(
                    "response conversion failed",
                    new HttpMessageNotReadableException(
                            "secret response body",
                            new TestHttpInputMessage()
                    )
            );
        }

        @GetMapping("/generic-conversion-error")
        String genericConversionError() {
            throw new RestClientException(
                    "conversion failed",
                    new HttpMessageConversionException(
                            "unknown conversion phase"
                    )
            );
        }

        @GetMapping("/generic-http-error")
        String genericHttpError() {
            throw new RestClientException(
                    "generic secret client error"
            );
        }

        @GetMapping("/spring-not-found")
        String springNotFound() {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "internal path"
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
            throw new
                    RetryAfterServiceUnavailableException();
        }

        @GetMapping("/spring-5xx/{status}")
        String spring5xx(
                @PathVariable("status")
                int status
        ) {
            throw new ErrorResponseException(
                    HttpStatusCode.valueOf(
                            status
                    )
            );
        }

        @GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException(
                    "select * from users "
                            + "where secret_token='secret'"
            );
        }

        @GetMapping("/custom")
        String custom() {
            throw new
                    CustomApplicationException();
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
     * Supporting types
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

    private static final class
    ValidationMethodHolder {

        private static void validate(
                String value
        ) {
        }
    }

    private static final class
    CustomApplicationException
            extends RuntimeException {
    }

    private static final class
    AdviceTargetException
            extends RuntimeException {
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static final class
    UserNotFoundException
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

            headers = new HttpHeaders();

            headers.set(
                    HttpHeaders.RETRY_AFTER,
                    "30"
            );
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

    private static final class
    TestHttpInputMessage
            implements org.springframework.http.HttpInputMessage {

        @Override
        public java.io.InputStream getBody() {
            return java.io.InputStream
                    .nullInputStream();
        }

        @Override
        public HttpHeaders getHeaders() {
            return HttpHeaders.EMPTY;
        }
    }

    /*
     * ------------------------------------------------------------
     * User advices
     * ------------------------------------------------------------
     */

    @RestControllerAdvice
    private static final class
    UnorderedApplicationAdvice {

        @ExceptionHandler(
                AdviceTargetException.class
        )
        ResponseEntity<Map<String, String>>
        handle(
                AdviceTargetException exception
        ) {
            return ResponseEntity
                    .status(
                            HttpStatus
                                    .UNPROCESSABLE_CONTENT
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
        handle(
                AdviceTargetException exception
        ) {
            return ResponseEntity
                    .status(
                            HttpStatus
                                    .UNPROCESSABLE_CONTENT
                    )
                    .body(
                            Map.of(
                                    "source",
                                    "high-priority-advice"
                            )
                    );
        }
    }
}