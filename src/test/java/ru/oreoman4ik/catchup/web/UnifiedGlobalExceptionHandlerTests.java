package ru.oreoman4ik.catchup.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json
        .JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import ru.oreoman4ik.catchup.model.BusinessException;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

class UnifiedGlobalExceptionHandlerTests {

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

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(handler)
                .setMessageConverters(
                        new JacksonJsonHttpMessageConverter(
                                jsonMapper
                        )
                )
                .build();
    }

    @Test
    void handlesBusinessErrorWithDeclaredStatusAndDetails()
            throws Exception {

        ErrorResponse response =
                performError("/business", 409);

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

    @Test
    void handlesValidationWithoutPublishingRejectedValue()
            throws Exception {

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

        Method method = ValidationMethodHolder.class
                .getDeclaredMethod(
                        "validate",
                        String.class
                );

        MethodParameter parameter =
                new MethodParameter(method, 0);

        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(
                        parameter,
                        bindingResult
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        "/validation"
                );

        ResponseEntity<ErrorResponse> entity =
                handler.handleBodyValidation(
                        exception,
                        request
                );

        assertThat(entity.getStatusCode().value())
                .isEqualTo(400);

        ErrorResponse response = entity.getBody();

        assertThat(response).isNotNull();
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
                jsonMapper.writeValueAsString(response);

        assertThat(json)
                .doesNotContain("secret-password")
                .doesNotContain("must not be blank");
    }

    @Test
    void preservesRemoteClientStatusButHidesRemoteBody()
            throws Exception {

        ErrorResponse response =
                performError("/remote-client", 404);

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
                .doesNotContain("Remote database failure");
    }

    @Test
    void preservesRemoteServerStatusButHidesRemoteBody()
            throws Exception {

        ErrorResponse response =
                performError("/remote-server", 503);

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
                .doesNotContain("database_password");
    }

    @Test
    void mapsRemoteTimeoutToGatewayTimeout()
            throws Exception {

        ErrorResponse response =
                performError("/timeout", 504);

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
                .doesNotContain("secret");
    }

    @Test
    void handlesStandardSpring404WithSafeMessage()
            throws Exception {

        ErrorResponse response =
                performError("/spring-not-found", 404);

        assertThat(response.getStatus())
                .isEqualTo(404);

        assertThat(response.getErrorCode())
                .isEqualTo("RESOURCE_NOT_FOUND");

        assertThat(response.getMessage())
                .isEqualTo("Ресурс не найден");

        String json =
                jsonMapper.writeValueAsString(response);

        assertThat(json)
                .doesNotContain("/internal/storage/path")
                .doesNotContain("database-id");
    }

    @Test
    void unexpectedExceptionReturnsSafe500()
            throws Exception {

        ErrorResponse response =
                performError("/unexpected", 500);

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
                jsonMapper.writeValueAsString(response);

        assertThat(json)
                .doesNotContain("select *")
                .doesNotContain("secret_token")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void controllerLocalHandlerHasPriorityOverGlobalHandler()
            throws Exception {

        MvcResult result = mockMvc
                .perform(get("/custom"))
                .andExpect(status().isIAmATeapot())
                .andReturn();

        String body =
                result.getResponse()
                        .getContentAsString();

        assertThat(body)
                .contains("\"source\":\"custom-handler\"")
                .doesNotContain("\"errorId\"")
                .doesNotContain("\"errorCode\"");
    }

    @Test
    void globalHandlerHasLowestAdvicePriority() {
        Order order = UnifiedGlobalExceptionHandler
                .class
                .getAnnotation(Order.class);

        assertThat(order).isNotNull();

        assertThat(order.value())
                .isEqualTo(
                        Ordered.LOWEST_PRECEDENCE
                );
    }

    private ErrorResponse performError(
            String path,
            int expectedStatus
    ) throws Exception {
        MvcResult result = mockMvc
                .perform(get(path))
                .andExpect(
                        status().is(expectedStatus)
                )
                .andReturn();

        return jsonMapper.readValue(
                result.getResponse()
                        .getContentAsString(),
                ErrorResponse.class
        );
    }

    private static final class ValidationMethodHolder {

        private static void validate(String value) {
        }
    }

    private static final class CustomApplicationException
            extends RuntimeException {
    }

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

        @GetMapping("/remote-client")
        String remoteClient() {
            throw new HttpClientErrorException(
                    HttpStatus.NOT_FOUND,
                    "Remote database failure",
                    HttpHeaders.EMPTY,
                    (
                            "select * from internal_table "
                                    + "where token=secret-token"
                    ).getBytes(StandardCharsets.UTF_8),
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
                    ).getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8
            );
        }

        @GetMapping("/timeout")
        String timeout() {
            throw new ResourceAccessException(
                    "GET http://internal-host?token=secret",
                    new SocketTimeoutException(
                            "Read timed out for internal-host"
                    )
            );
        }

        @GetMapping("/spring-not-found")
        String springNotFound() {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Missing /internal/storage/path "
                            + "with database-id=42"
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
            throw new CustomApplicationException();
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
}