package ru.oreoman4ik.catchup.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.HandlerMapping;
import ru.oreoman4ik.catchup.model.BusinessException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Глобальный обработчик ошибок Spring MVC.
 *
 * <p>Использует минимальный приоритет, чтобы приложение могло
 * объявлять собственные более приоритетные обработчики.</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public final class UnifiedGlobalExceptionHandler {

    private static final int ABSOLUTE_MAX_CHAIN_SIZE = 100;
    private static final int MAX_VALIDATION_ERRORS = 100;

    private static final String INTERNAL_ERROR_CODE =
            "INTERNAL_ERROR";

    private static final String INTERNAL_ERROR_MESSAGE =
            "Внутренняя ошибка сервиса";

    private final String currentService;
    private final int maxChainSize;

    public UnifiedGlobalExceptionHandler(
            @Value("${spring.application.name:application}")
            String currentService,

            @Value("${catchup.errors.max-chain-size:10}")
            int maxChainSize
    ) {
        this.currentService =
                validateServiceName(currentService);

        this.maxChainSize =
                validateMaxChainSize(maxChainSize);
    }

    /**
     * Обрабатывает уже сформированную структурированную ошибку.
     *
     * <p>Идентификатор, timestamp, код, details и исходная
     * цепочка сохраняются.</p>
     */
    @ExceptionHandler(UnifiedErrorException.class)
    public ResponseEntity<ErrorResponse> handleUnifiedError(
            UnifiedErrorException exception,
            HttpServletRequest request
    ) {
        exception.addContext(
                createContext(
                        request,
                        exception.getStatus(),
                        exception.getErrorCode(),
                        exception.getMessage(),
                        Instant.now()
                )
        );

        return toResponseEntity(exception);
    }

    /**
     * Обрабатывает контролируемые бизнес-ошибки.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessError(
            BusinessException exception,
            HttpServletRequest request
    ) {
        return createResponse(
                exception,
                exception.getStatus(),
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getDetails(),
                request
        );
    }

    /**
     * Обрабатывает ошибки @Valid для RequestBody и ModelAttribute.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ErrorDetails.FieldViolation> violations =
                exception.getBindingResult()
                        .getAllErrors()
                        .stream()
                        .limit(MAX_VALIDATION_ERRORS)
                        .map(
                                UnifiedGlobalExceptionHandler
                                        ::toFieldViolation
                        )
                        .toList();

        return createResponse(
                exception,
                400,
                "VALIDATION_ERROR",
                "Переданные данные некорректны",
                validationDetails(violations),
                request
        );
    }

    /**
     * Обрабатывает валидацию параметров методов контроллера.
     *
     * <p>Spring использует статус 400 для ошибок входных
     * параметров и 500 для ошибок проверки возвращаемого
     * значения.</p>
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        if (exception.isForReturnValue()) {
            return createResponse(
                    exception,
                    500,
                    "RESPONSE_VALIDATION_ERROR",
                    "Ошибка формирования ответа сервиса",
                    null,
                    request
            );
        }

        List<ErrorDetails.FieldViolation> violations =
                methodValidationViolations(exception);

        return createResponse(
                exception,
                400,
                "VALIDATION_ERROR",
                "Переданные данные некорректны",
                validationDetails(violations),
                request
        );
    }

    /**
     * Обрабатывает ConstraintViolationException, например
     * ошибки валидации request parameter и path variable.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse>
    handleConstraintValidation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ErrorDetails.FieldViolation> violations =
                exception.getConstraintViolations()
                        .stream()
                        .limit(MAX_VALIDATION_ERRORS)
                        .map(
                                UnifiedGlobalExceptionHandler
                                        ::toFieldViolation
                        )
                        .toList();

        return createResponse(
                exception,
                400,
                "VALIDATION_ERROR",
                "Переданные данные некорректны",
                validationDetails(violations),
                request
        );
    }

    /**
     * Обрабатывает HTTP 4xx и 5xx, полученные RestClient или
     * RestTemplate.
     *
     * <p>Тело ответа удалённого сервиса и сообщение исключения
     * наружу не передаются.</p>
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ErrorResponse>
    handleRemoteHttpStatus(
            RestClientResponseException exception,
            HttpServletRequest request
    ) {
        int remoteStatus =
                exception.getStatusCode().value();

        if (remoteStatus < 400 || remoteStatus > 599) {
            return createResponse(
                    exception,
                    502,
                    "REMOTE_REQUEST_ERROR",
                    "Не удалось выполнить запрос "
                            + "к удалённому сервису",
                    null,
                    request
            );
        }

        if (remoteStatus < 500) {
            return createResponse(
                    exception,
                    remoteStatus,
                    "REMOTE_CLIENT_ERROR",
                    "Удалённый сервис отклонил запрос",
                    null,
                    request
            );
        }

        return createResponse(
                exception,
                remoteStatus,
                "REMOTE_SERVER_ERROR",
                "Удалённый сервис завершил запрос "
                        + "с ошибкой",
                null,
                request
        );
    }

    /**
     * Обрабатывает сетевые ошибки и таймауты.
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse>
    handleRemoteAccessError(
            ResourceAccessException exception,
            HttpServletRequest request
    ) {
        if (isTimeout(exception)) {
            return createResponse(
                    exception,
                    504,
                    "REMOTE_TIMEOUT",
                    "Истекло время ожидания ответа "
                            + "удалённого сервиса",
                    null,
                    request
            );
        }

        return createResponse(
                exception,
                503,
                "REMOTE_UNAVAILABLE",
                "Удалённый сервис недоступен",
                null,
                request
        );
    }

    /**
     * Обрабатывает остальные ошибки синхронных HTTP-клиентов,
     * включая ошибки преобразования ответа.
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse>
    handleRemoteClientError(
            RestClientException exception,
            HttpServletRequest request
    ) {
        return createResponse(
                exception,
                502,
                "REMOTE_REQUEST_ERROR",
                "Не удалось обработать ответ "
                        + "удалённого сервиса",
                null,
                request
        );
    }

    /**
     * Последний безопасный fallback.
     *
     * <p>Если исключение является стандартным Spring
     * ErrorResponse, его HTTP-статус сохраняется, но техническое
     * сообщение Spring наружу не передаётся.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse>
    handleUnexpectedError(
            Exception exception,
            HttpServletRequest request
    ) {
        if (exception
                instanceof org.springframework.web.ErrorResponse
                springError) {

            int status =
                    springError.getStatusCode().value();

            PublicError publicError =
                    publicErrorForStatus(status);

            return createResponse(
                    exception,
                    publicError.status(),
                    publicError.errorCode(),
                    publicError.message(),
                    null,
                    request
            );
        }

        return createResponse(
                exception,
                500,
                INTERNAL_ERROR_CODE,
                INTERNAL_ERROR_MESSAGE,
                null,
                request
        );
    }

    private ResponseEntity<ErrorResponse> createResponse(
            Throwable cause,
            int status,
            String errorCode,
            String message,
            ErrorDetails details,
            HttpServletRequest request
    ) {
        Instant timestamp = Instant.now();

        ChainElement context = createContext(
                request,
                status,
                errorCode,
                message,
                timestamp
        );

        UnifiedErrorException unifiedError =
                UnifiedErrorException.from(
                        cause,
                        timestamp,
                        status,
                        errorCode,
                        message,
                        details,
                        context,
                        maxChainSize
                );

        return toResponseEntity(unifiedError);
    }

    private ResponseEntity<ErrorResponse> toResponseEntity(
            UnifiedErrorException exception
    ) {
        ErrorResponse body =
                exception.toResponse(currentService);

        return ResponseEntity
                .status(body.getStatus())
                .body(body);
    }

    private ChainElement createContext(
            HttpServletRequest request,
            int status,
            String errorCode,
            String message,
            Instant timestamp
    ) {
        RequestLocation location =
                resolveLocation(request);

        return ChainElement.builder()
                .service(currentService)
                .component(location.component())
                .operation(location.operation())
                .errorCode(errorCode)
                .message(message)
                .timestamp(timestamp)
                .status(status)
                .build();
    }

    private static RequestLocation resolveLocation(
            HttpServletRequest request
    ) {
        Object handler = request.getAttribute(
                HandlerMapping
                        .BEST_MATCHING_HANDLER_ATTRIBUTE
        );

        if (handler instanceof HandlerMethod handlerMethod) {
            return new RequestLocation(
                    handlerMethod
                            .getBeanType()
                            .getSimpleName(),

                    handlerMethod
                            .getMethod()
                            .getName()
            );
        }

        /*
         * URI намеренно не включается:
         * путь запроса может содержать идентификаторы
         * или чувствительные значения.
         */
        return new RequestLocation(
                "REST",
                request.getMethod()
        );
    }

    private static List<ErrorDetails.FieldViolation>
    methodValidationViolations(
            HandlerMethodValidationException exception
    ) {
        List<ErrorDetails.FieldViolation> result =
                new ArrayList<>();

        for (ParameterValidationResult validationResult
                : exception.getParameterValidationResults()) {

            if (result.size() >= MAX_VALIDATION_ERRORS) {
                break;
            }

            if (validationResult
                    instanceof ParameterErrors errors) {

                for (ObjectError error
                        : errors.getAllErrors()) {

                    addViolation(
                            result,
                            toFieldViolation(error)
                    );
                }

                continue;
            }

            String field = parameterName(
                    validationResult.getMethodParameter()
            );

            for (MessageSourceResolvable error
                    : validationResult
                    .getResolvableErrors()) {

                addViolation(
                        result,
                        toFieldViolation(field, error)
                );
            }
        }

        for (MessageSourceResolvable error
                : exception
                .getCrossParameterValidationResults()) {

            addViolation(
                    result,
                    toFieldViolation("request", error)
            );
        }

        return List.copyOf(result);
    }

    private static void addViolation(
            List<ErrorDetails.FieldViolation> target,
            ErrorDetails.FieldViolation violation
    ) {
        if (target.size() < MAX_VALIDATION_ERRORS) {
            target.add(violation);
        }
    }

    private static ErrorDetails.FieldViolation
    toFieldViolation(ObjectError error) {
        String field =
                error instanceof FieldError fieldError
                        ? safeFieldName(fieldError.getField())
                        : "request";

        String reasonCode =
                validationReasonCode(error.getCode());

        return ErrorDetails.FieldViolation.of(
                field,
                reasonCode,
                validationMessage(reasonCode)
        );
    }

    private static ErrorDetails.FieldViolation
    toFieldViolation(
            String field,
            MessageSourceResolvable error
    ) {
        String reasonCode =
                validationReasonCode(
                        lastCode(error.getCodes())
                );

        return ErrorDetails.FieldViolation.of(
                safeFieldName(field),
                reasonCode,
                validationMessage(reasonCode)
        );
    }

    private static ErrorDetails.FieldViolation
    toFieldViolation(
            ConstraintViolation<?> violation
    ) {
        String field = lastPathElement(
                violation
                        .getPropertyPath()
                        .toString()
        );

        String constraintName =
                violation.getConstraintDescriptor()
                        .getAnnotation()
                        .annotationType()
                        .getSimpleName();

        String reasonCode =
                validationReasonCode(constraintName);

        return ErrorDetails.FieldViolation.of(
                field,
                reasonCode,
                validationMessage(reasonCode)
        );
    }

    private static ErrorDetails validationDetails(
            List<ErrorDetails.FieldViolation> violations
    ) {
        if (violations.isEmpty()) {
            return null;
        }

        return ErrorDetails.builder()
                .violations(violations)
                .build();
    }

    private static String parameterName(
            MethodParameter parameter
    ) {
        String name = parameter.getParameterName();

        if (name == null || name.isBlank()) {
            return "argument"
                    + parameter.getParameterIndex();
        }

        return safeFieldName(name);
    }

    private static String validationReasonCode(
            String springCode
    ) {
        if (springCode == null) {
            return "INVALID";
        }

        if (springCode.contains("NotNull")
                || springCode.contains("NotBlank")
                || springCode.contains("NotEmpty")) {

            return "REQUIRED";
        }

        if (springCode.contains("Size")
                || springCode.contains("Length")) {

            return "INVALID_SIZE";
        }

        if (springCode.contains("Min")
                || springCode.contains("DecimalMin")
                || springCode.contains("Positive")) {

            return "TOO_SMALL";
        }

        if (springCode.contains("Max")
                || springCode.contains("DecimalMax")
                || springCode.contains("Negative")) {

            return "TOO_LARGE";
        }

        if (springCode.contains("Email")) {
            return "INVALID_EMAIL";
        }

        if (springCode.contains("Pattern")) {
            return "INVALID_FORMAT";
        }

        return "INVALID";
    }

    private static String validationMessage(
            String reasonCode
    ) {
        return switch (reasonCode) {
            case "REQUIRED" ->
                    "Поле обязательно";

            case "INVALID_SIZE" ->
                    "Некорректный размер значения";

            case "TOO_SMALL" ->
                    "Значение слишком мало";

            case "TOO_LARGE" ->
                    "Значение слишком велико";

            case "INVALID_EMAIL" ->
                    "Некорректный адрес электронной почты";

            case "INVALID_FORMAT" ->
                    "Некорректный формат значения";

            default ->
                    "Некорректное значение";
        };
    }

    private static PublicError publicErrorForStatus(
            int status
    ) {
        return switch (status) {
            case 400 -> new PublicError(
                    400,
                    "INVALID_REQUEST",
                    "Некорректный запрос"
            );

            case 401 -> new PublicError(
                    401,
                    "AUTHENTICATION_REQUIRED",
                    "Требуется аутентификация"
            );

            case 403 -> new PublicError(
                    403,
                    "ACCESS_DENIED",
                    "Доступ запрещён"
            );

            case 404 -> new PublicError(
                    404,
                    "RESOURCE_NOT_FOUND",
                    "Ресурс не найден"
            );

            case 405 -> new PublicError(
                    405,
                    "METHOD_NOT_ALLOWED",
                    "HTTP-метод не поддерживается"
            );

            case 409 -> new PublicError(
                    409,
                    "CONFLICT",
                    "Запрос конфликтует "
                            + "с текущим состоянием ресурса"
            );

            case 415 -> new PublicError(
                    415,
                    "UNSUPPORTED_MEDIA_TYPE",
                    "Формат тела запроса не поддерживается"
            );

            case 422 -> new PublicError(
                    422,
                    "UNPROCESSABLE_CONTENT",
                    "Запрос не может быть обработан"
            );

            case 429 -> new PublicError(
                    429,
                    "TOO_MANY_REQUESTS",
                    "Превышено допустимое количество запросов"
            );

            default -> {
                if (status >= 400 && status < 500) {
                    yield new PublicError(
                            status,
                            "CLIENT_ERROR",
                            "Ошибка клиентского запроса"
                    );
                }

                yield new PublicError(
                        500,
                        INTERNAL_ERROR_CODE,
                        INTERNAL_ERROR_MESSAGE
                );
            }
        };
    }

    private static boolean isTimeout(
            Throwable throwable
    ) {
        return hasCause(
                throwable,
                SocketTimeoutException.class
        )
                || hasCause(
                throwable,
                HttpTimeoutException.class
        )
                || hasCause(
                throwable,
                TimeoutException.class
        );
    }

    private static boolean hasCause(
            Throwable throwable,
            Class<? extends Throwable> causeType
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private static String lastCode(String[] codes) {
        if (codes == null || codes.length == 0) {
            return null;
        }

        return codes[codes.length - 1];
    }

    private static String lastPathElement(
            String path
    ) {
        if (path == null || path.isBlank()) {
            return "request";
        }

        int separator = path.lastIndexOf('.');

        String result = separator >= 0
                ? path.substring(separator + 1)
                : path;

        return safeFieldName(result);
    }

    private static String safeFieldName(
            String field
    ) {
        if (field == null || field.isBlank()) {
            return "request";
        }

        String normalized = field
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\t', '_')
                .trim();

        if (normalized.isEmpty()) {
            return "request";
        }

        if (normalized.length() > 160) {
            return normalized.substring(0, 160);
        }

        return normalized;
    }

    private static String validateServiceName(
            String value
    ) {
        String normalized =
                value == null || value.isBlank()
                        ? "application"
                        : value.trim();

        if (normalized.length() > 120) {
            throw new IllegalArgumentException(
                    "spring.application.name must not exceed "
                            + "120 characters"
            );
        }

        if (normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\t') >= 0) {

            throw new IllegalArgumentException(
                    "spring.application.name must not contain "
                            + "control characters"
            );
        }

        return normalized;
    }

    private static int validateMaxChainSize(
            int value
    ) {
        if (value < 1 || value > ABSOLUTE_MAX_CHAIN_SIZE) {
            throw new IllegalArgumentException(
                    "catchup.errors.max-chain-size must be "
                            + "from 1 to "
                            + ABSOLUTE_MAX_CHAIN_SIZE
            );
        }

        return value;
    }

    private record RequestLocation(
            String component,
            String operation
    ) {
    }

    private record PublicError(
            int status,
            String errorCode,
            String message
    ) {
    }
}