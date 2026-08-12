package ru.oreoman4ik.catchup.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.oreoman4ik.catchup.client.OutgoingHttpExceptionMapper;
import ru.oreoman4ik.catchup.model.BusinessException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
@Order(UnifiedGlobalExceptionHandler.HANDLER_ORDER)
public final class UnifiedGlobalExceptionHandler
        extends ResponseEntityExceptionHandler {

    public static final int HANDLER_ORDER = -1;

    private static final int ABSOLUTE_MAX_CHAIN_SIZE = 100;
    private static final int MAX_VALIDATION_ERRORS = 100;

    private static final String INTERNAL_ERROR_CODE =
            "INTERNAL_ERROR";

    private static final String INTERNAL_ERROR_MESSAGE =
            "Внутренняя ошибка сервиса";

    private final String currentService;
    private final int maxChainSize;

    private final OutgoingHttpExceptionMapper
            outgoingHttpExceptionMapper;

    public UnifiedGlobalExceptionHandler(
            @Value("${spring.application.name:application}")
            String currentService,

            @Value("${catchup.errors.max-chain-size:10}")
            int maxChainSize,

            OutgoingHttpExceptionMapper
                    outgoingHttpExceptionMapper
    ) {
        this.currentService =
                validateServiceName(currentService);

        this.maxChainSize =
                validateMaxChainSize(maxChainSize);

        if (outgoingHttpExceptionMapper == null) {
            throw new IllegalArgumentException(
                    "outgoingHttpExceptionMapper is required"
            );
        }

        this.outgoingHttpExceptionMapper =
                outgoingHttpExceptionMapper;
    }

    /*
     * ------------------------------------------------------------
     * Library exceptions
     * ------------------------------------------------------------
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

        return toResponseEntity(
                exception,
                HttpHeaders.EMPTY
        );
    }

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
                HttpHeaders.EMPTY,
                request
        );
    }

    /*
     * ------------------------------------------------------------
     * Validation
     * ------------------------------------------------------------
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
                HttpHeaders.EMPTY,
                request
        );
    }

    /*
     * ------------------------------------------------------------
     * Outgoing HTTP
     * ------------------------------------------------------------
     */

    /**
     * Вся классификация исходящих HTTP-ошибок выполняется
     * исключительно OutgoingHttpExceptionMapper.
     *
     * <p>Global handler не содержит собственной таблицы
     * REMOTE_* / OUTGOING_* ошибок.</p>
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse>
    handleOutgoingHttpError(
            RestClientException exception,
            HttpServletRequest request
    ) {
        RequestLocation location =
                resolveLocation(request);

        UnifiedErrorException unifiedError =
                outgoingHttpExceptionMapper.map(
                        exception,
                        currentService,
                        location.component(),
                        location.operation(),
                        null
                );

        return toResponseEntity(
                unifiedError,
                HttpHeaders.EMPTY
        );
    }

    /*
     * ------------------------------------------------------------
     * Standard Spring MVC exceptions
     * ------------------------------------------------------------
     */

    @Override
    protected ResponseEntity<Object>
    handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return createObjectResponse(
                exception,
                400,
                "INVALID_REQUEST_BODY",
                "Тело запроса имеет некорректный формат",
                null,
                headers,
                servletRequest(request)
        );
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return createObjectResponse(
                exception,
                400,
                "INVALID_PARAMETER",
                "Параметр запроса имеет некорректный формат",
                typeMismatchDetails(exception),
                headers,
                servletRequest(request)
        );
    }

    @Override
    protected ResponseEntity<Object>
    handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
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

        return createObjectResponse(
                exception,
                400,
                "VALIDATION_ERROR",
                "Переданные данные некорректны",
                validationDetails(violations),
                headers,
                servletRequest(request)
        );
    }

    @Override
    protected ResponseEntity<Object>
    handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        if (exception.isForReturnValue()) {
            return createObjectResponse(
                    exception,
                    500,
                    "RESPONSE_VALIDATION_ERROR",
                    "Ошибка формирования ответа сервиса",
                    null,
                    headers,
                    servletRequest(request)
            );
        }

        List<ErrorDetails.FieldViolation> violations =
                methodValidationViolations(exception);

        return createObjectResponse(
                exception,
                400,
                "VALIDATION_ERROR",
                "Переданные данные некорректны",
                validationDetails(violations),
                headers,
                servletRequest(request)
        );
    }

    /**
     * Общий путь для остальных стандартных MVC-ошибок,
     * которые обрабатывает ResponseEntityExceptionHandler.
     *
     * <p>Исходный HTTP status и headers сохраняются.</p>
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        PublicError publicError =
                publicErrorForStatus(
                        statusCode.value()
                );

        return createObjectResponse(
                exception,
                publicError.status(),
                publicError.errorCode(),
                publicError.message(),
                null,
                headers,
                servletRequest(request)
        );
    }

    /*
     * ------------------------------------------------------------
     * Fallback
     * ------------------------------------------------------------
     */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse>
    handleUnexpectedError(
            Exception exception,
            HttpServletRequest request
    ) {
        /*
         * Сохраняем status и headers стандартных
         * Spring ErrorResponse.
         */
        if (exception
                instanceof org.springframework.web.ErrorResponse
                springError) {

            PublicError publicError =
                    publicErrorForStatus(
                            springError
                                    .getStatusCode()
                                    .value()
                    );

            return createResponse(
                    exception,
                    publicError.status(),
                    publicError.errorCode(),
                    publicError.message(),
                    null,
                    springError.getHeaders(),
                    request
            );
        }

        /*
         * Не ломаем пользовательские исключения,
         * помеченные @ResponseStatus.
         */
        ResponseStatus responseStatus =
                AnnotatedElementUtils
                        .findMergedAnnotation(
                                exception.getClass(),
                                ResponseStatus.class
                        );

        if (responseStatus != null) {
            int status =
                    responseStatus.code().value();

            if (status >= 400 && status <= 599) {
                PublicError publicError =
                        publicErrorForStatus(status);

                return createResponse(
                        exception,
                        publicError.status(),
                        publicError.errorCode(),
                        publicError.message(),
                        null,
                        HttpHeaders.EMPTY,
                        request
                );
            }
        }

        return createResponse(
                exception,
                500,
                INTERNAL_ERROR_CODE,
                INTERNAL_ERROR_MESSAGE,
                null,
                HttpHeaders.EMPTY,
                request
        );
    }

    /*
     * ------------------------------------------------------------
     * Response creation
     * ------------------------------------------------------------
     */

    private ResponseEntity<ErrorResponse> createResponse(
            Exception cause,
            int status,
            String errorCode,
            String message,
            ErrorDetails details,
            HttpHeaders headers,
            HttpServletRequest request
    ) {
        UnifiedErrorException unifiedError =
                createUnifiedError(
                        cause,
                        status,
                        errorCode,
                        message,
                        details,
                        request
                );

        return toResponseEntity(
                unifiedError,
                headers
        );
    }

    private ResponseEntity<Object> createObjectResponse(
            Exception cause,
            int status,
            String errorCode,
            String message,
            ErrorDetails details,
            HttpHeaders headers,
            HttpServletRequest request
    ) {
        UnifiedErrorException unifiedError =
                createUnifiedError(
                        cause,
                        status,
                        errorCode,
                        message,
                        details,
                        request
                );

        ErrorResponse body =
                unifiedError.toResponse(
                        currentService
                );

        return ResponseEntity
                .status(body.getStatus())
                .headers(copyHeaders(headers))
                .body(body);
    }

    private UnifiedErrorException createUnifiedError(
            Exception cause,
            int status,
            String errorCode,
            String message,
            ErrorDetails details,
            HttpServletRequest request
    ) {
        Instant timestamp = Instant.now();

        ChainElement context =
                createContext(
                        request,
                        status,
                        errorCode,
                        message,
                        timestamp
                );

        return UnifiedErrorException.from(
                cause,
                timestamp,
                status,
                errorCode,
                message,
                details,
                context,
                maxChainSize
        );
    }

    private ResponseEntity<ErrorResponse> toResponseEntity(
            UnifiedErrorException exception,
            HttpHeaders headers
    ) {
        ErrorResponse body =
                exception.toResponse(
                        currentService
                );

        return ResponseEntity
                .status(body.getStatus())
                .headers(copyHeaders(headers))
                .body(body);
    }

    /*
     * ------------------------------------------------------------
     * Request context
     * ------------------------------------------------------------
     */

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

        return new RequestLocation(
                "REST",
                request.getMethod()
        );
    }

    /*
     * ------------------------------------------------------------
     * Headers
     * ------------------------------------------------------------
     */

    private static HttpHeaders copyHeaders(
            HttpHeaders headers
    ) {
        if (headers == null || headers.isEmpty()) {
            return new HttpHeaders();
        }

        return HttpHeaders.copyOf(headers);
    }

    private static HttpServletRequest servletRequest(
            WebRequest request
    ) {
        if (request
                instanceof ServletWebRequest servletWebRequest) {

            return servletWebRequest.getRequest();
        }

        throw new IllegalStateException(
                "Servlet WebRequest is required"
        );
    }

    /*
     * ------------------------------------------------------------
     * Type mismatch
     * ------------------------------------------------------------
     */

    private static ErrorDetails typeMismatchDetails(
            TypeMismatchException exception
    ) {
        String field;

        if (exception
                instanceof
                MethodArgumentTypeMismatchException mismatch) {

            field = safeFieldName(
                    mismatch.getName()
            );

        } else {
            field = safeFieldName(
                    exception.getPropertyName()
            );
        }

        return ErrorDetails.builder()
                .violations(
                        List.of(
                                ErrorDetails
                                        .FieldViolation
                                        .of(
                                                field,
                                                "INVALID_TYPE",
                                                "Некорректный "
                                                        + "тип значения"
                                        )
                        )
                )
                .build();
    }

    /*
     * ------------------------------------------------------------
     * Method validation
     * ------------------------------------------------------------
     */

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

            String field =
                    parameterName(
                            validationResult
                                    .getMethodParameter()
                    );

            for (MessageSourceResolvable error
                    : validationResult
                    .getResolvableErrors()) {

                addViolation(
                        result,
                        toFieldViolation(
                                field,
                                error
                        )
                );
            }
        }

        for (MessageSourceResolvable error
                : exception
                .getCrossParameterValidationResults()) {

            addViolation(
                    result,
                    toFieldViolation(
                            "request",
                            error
                    )
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

    /*
     * ------------------------------------------------------------
     * Validation mapping
     * ------------------------------------------------------------
     */

    private static ErrorDetails.FieldViolation
    toFieldViolation(ObjectError error) {

        String field =
                error instanceof FieldError fieldError
                        ? safeFieldName(
                        fieldError.getField()
                )
                        : "request";

        String reasonCode =
                validationReasonCode(
                        error.getCode()
                );

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
                        lastCode(
                                error.getCodes()
                        )
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
        String field =
                lastPathElement(
                        violation
                                .getPropertyPath()
                                .toString()
                );

        String constraintName =
                violation
                        .getConstraintDescriptor()
                        .getAnnotation()
                        .annotationType()
                        .getSimpleName();

        String reasonCode =
                validationReasonCode(
                        constraintName
                );

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
        String name =
                parameter.getParameterName();

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
                    "Некорректный адрес "
                            + "электронной почты";

            case "INVALID_FORMAT" ->
                    "Некорректный формат значения";

            default ->
                    "Некорректное значение";
        };
    }

    /*
     * ------------------------------------------------------------
     * Standard Spring status mapping
     * ------------------------------------------------------------
     */

    private static PublicError publicErrorForStatus(
            int status
    ) {
        if (status < 400 || status > 599) {
            return new PublicError(
                    500,
                    INTERNAL_ERROR_CODE,
                    INTERNAL_ERROR_MESSAGE
            );
        }

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

            case 406 -> new PublicError(
                    406,
                    "NOT_ACCEPTABLE",
                    "Запрошенный формат ответа "
                            + "не поддерживается"
            );

            case 409 -> new PublicError(
                    409,
                    "CONFLICT",
                    "Запрос конфликтует "
                            + "с текущим состоянием ресурса"
            );

            case 413 -> new PublicError(
                    413,
                    "PAYLOAD_TOO_LARGE",
                    "Размер запроса превышает "
                            + "допустимый предел"
            );

            case 415 -> new PublicError(
                    415,
                    "UNSUPPORTED_MEDIA_TYPE",
                    "Формат тела запроса "
                            + "не поддерживается"
            );

            case 422 -> new PublicError(
                    422,
                    "UNPROCESSABLE_CONTENT",
                    "Запрос не может быть обработан"
            );

            case 429 -> new PublicError(
                    429,
                    "TOO_MANY_REQUESTS",
                    "Превышено допустимое "
                            + "количество запросов"
            );

            case 500 -> new PublicError(
                    500,
                    INTERNAL_ERROR_CODE,
                    INTERNAL_ERROR_MESSAGE
            );

            default -> {
                if (status < 500) {
                    yield new PublicError(
                            status,
                            "CLIENT_ERROR",
                            "Ошибка клиентского запроса"
                    );
                }

                yield new PublicError(
                        status,
                        "SERVER_ERROR",
                        INTERNAL_ERROR_MESSAGE
                );
            }
        };
    }

    /*
     * ------------------------------------------------------------
     * Utility
     * ------------------------------------------------------------
     */

    private static String lastCode(
            String[] codes
    ) {
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

        int separator =
                path.lastIndexOf('.');

        String result =
                separator >= 0
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

        String normalized =
                field
                        .replace('\r', '_')
                        .replace('\n', '_')
                        .replace('\t', '_')
                        .trim();

        if (normalized.isEmpty()) {
            return "request";
        }

        if (normalized.length() > 160) {
            return normalized.substring(
                    0,
                    160
            );
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
                    "spring.application.name must not "
                            + "exceed 120 characters"
            );
        }

        if (normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\t') >= 0) {

            throw new IllegalArgumentException(
                    "spring.application.name must not "
                            + "contain control characters"
            );
        }

        return normalized;
    }

    private static int validateMaxChainSize(
            int value
    ) {
        if (value < 1
                || value > ABSOLUTE_MAX_CHAIN_SIZE) {

            throw new IllegalArgumentException(
                    "catchup.errors.max-chain-size "
                            + "must be from 1 to "
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