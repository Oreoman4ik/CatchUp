package ru.oreoman4ik.catchup.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import ru.oreoman4ik.catchup.config.CurrentServiceName;
import ru.oreoman4ik.catchup.config.TechnicalDetailsFactory;
import ru.oreoman4ik.catchup.config.UnifiedErrorProperties;
import ru.oreoman4ik.catchup.logging.Slf4jErrorLogSink;
import ru.oreoman4ik.catchup.logging.UnifiedErrorLogger;
import ru.oreoman4ik.catchup.model.BusinessException;
import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.ErrorDetails;
import ru.oreoman4ik.catchup.model.ErrorResponse;
import ru.oreoman4ik.catchup.model.TruncationInfo;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
@Order(
        UnifiedGlobalExceptionHandler
                .HANDLER_ORDER
)
@ConditionalOnProperty(
        prefix = "catchup.errors",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public final class UnifiedGlobalExceptionHandler
        extends ResponseEntityExceptionHandler {

    public static final int HANDLER_ORDER = -1;

    private static final int
            MAX_VALIDATION_ERRORS = 100;

    private static final String
            INTERNAL_ERROR_CODE =
            "INTERNAL_ERROR";

    private static final String
            DEFAULT_UNKNOWN_ERROR_MESSAGE =
            "Внутренняя ошибка сервиса";

    private final String currentService;

    private final int maxChainSize;

    private final String unknownErrorMessage;

    private final OutgoingHttpExceptionMapper
            outgoingHttpExceptionMapper;

    private final TechnicalDetailsFactory
            technicalDetailsFactory;

    private final UnifiedErrorLogger
            errorLogger;

    /**
     * Основной production constructor.
     */
    public UnifiedGlobalExceptionHandler(
            CurrentServiceName currentService,
            UnifiedErrorProperties properties,
            OutgoingHttpExceptionMapper
                    outgoingHttpExceptionMapper,
            TechnicalDetailsFactory
                    technicalDetailsFactory,
            UnifiedErrorLogger errorLogger
    ) {
        if (currentService == null) {
            throw new IllegalArgumentException(
                    "currentService is required"
            );
        }

        if (properties == null) {
            throw new IllegalArgumentException(
                    "properties is required"
            );
        }

        if (outgoingHttpExceptionMapper == null) {
            throw new IllegalArgumentException(
                    "outgoingHttpExceptionMapper "
                            + "is required"
            );
        }

        if (technicalDetailsFactory == null) {
            throw new IllegalArgumentException(
                    "technicalDetailsFactory "
                            + "is required"
            );
        }

        if (errorLogger == null) {
            throw new IllegalArgumentException(
                    "errorLogger is required"
            );
        }

        this.currentService =
                currentService.value();

        this.maxChainSize =
                properties.getMaxChainSize();

        this.unknownErrorMessage =
                properties
                        .getUnknownErrorMessage();

        this.outgoingHttpExceptionMapper =
                outgoingHttpExceptionMapper;

        this.technicalDetailsFactory =
                technicalDetailsFactory;

        this.errorLogger =
                errorLogger;
    }

    /**
     * Совместимость с тестами задачи 8/9.
     */
    public UnifiedGlobalExceptionHandler(
            CurrentServiceName currentService,
            UnifiedErrorProperties properties,
            OutgoingHttpExceptionMapper
                    outgoingHttpExceptionMapper,
            TechnicalDetailsFactory
                    technicalDetailsFactory
    ) {
        this(
                currentService,
                properties,
                outgoingHttpExceptionMapper,
                technicalDetailsFactory,
                new UnifiedErrorLogger(
                        new Slf4jErrorLogSink()
                )
        );
    }

    /**
     * Совместимость со старыми unit tests.
     */
    public UnifiedGlobalExceptionHandler(
            String currentService,
            int maxChainSize,
            OutgoingHttpExceptionMapper
                    outgoingHttpExceptionMapper
    ) {
        this.currentService =
                validateServiceName(
                        currentService
                );

        this.maxChainSize =
                validateMaxChainSize(
                        maxChainSize
                );

        this.unknownErrorMessage =
                DEFAULT_UNKNOWN_ERROR_MESSAGE;

        if (outgoingHttpExceptionMapper == null) {
            throw new IllegalArgumentException(
                    "outgoingHttpExceptionMapper "
                            + "is required"
            );
        }

        this.outgoingHttpExceptionMapper =
                outgoingHttpExceptionMapper;

        UnifiedErrorProperties properties =
                new UnifiedErrorProperties();

        properties.setMaxChainSize(
                maxChainSize
        );

        this.technicalDetailsFactory =
                new TechnicalDetailsFactory(
                        properties
                );

        this.errorLogger =
                new UnifiedErrorLogger(
                        new Slf4jErrorLogSink()
                );
    }

    @ExceptionHandler(
            UnifiedErrorException.class
    )
    public ResponseEntity<ErrorResponse>
    handleUnifiedError(
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

    @ExceptionHandler(
            BusinessException.class
    )
    public ResponseEntity<ErrorResponse>
    handleBusinessError(
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

    @ExceptionHandler(
            ConstraintViolationException.class
    )
    public ResponseEntity<ErrorResponse>
    handleConstraintValidation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        var allViolations =
                exception
                        .getConstraintViolations();

        boolean violationsTruncated =
                allViolations.size()
                        > MAX_VALIDATION_ERRORS;

        List<ErrorDetails.FieldViolation>
                violations =
                allViolations
                        .stream()
                        .limit(
                                MAX_VALIDATION_ERRORS
                        )
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
                validationDetails(
                        violations,
                        violationsTruncated
                ),
                HttpHeaders.EMPTY,
                request
        );
    }

    @ExceptionHandler(
            RestClientException.class
    )
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
                "Тело запроса имеет "
                        + "некорректный формат",
                null,
                headers,
                servletRequest(request)
        );
    }

    @Override
    protected ResponseEntity<Object>
    handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return createObjectResponse(
                exception,
                400,
                "INVALID_PARAMETER",
                "Параметр запроса имеет "
                        + "некорректный формат",
                typeMismatchDetails(
                        exception
                ),
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
        List<ObjectError> allErrors =
                exception
                        .getBindingResult()
                        .getAllErrors();

        boolean violationsTruncated =
                allErrors.size()
                        > MAX_VALIDATION_ERRORS;

        List<ErrorDetails.FieldViolation>
                violations =
                allErrors
                        .stream()
                        .limit(
                                MAX_VALIDATION_ERRORS
                        )
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
                validationDetails(
                        violations,
                        violationsTruncated
                ),
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
                    "Ошибка формирования "
                            + "ответа сервиса",
                    null,
                    headers,
                    servletRequest(request)
            );
        }

        LimitedViolations violations =
                methodValidationViolations(
                        exception
                );

        return createObjectResponse(
                exception,
                400,
                "VALIDATION_ERROR",
                "Переданные данные некорректны",
                validationDetails(
                        violations.values(),
                        violations.truncated()
                ),
                headers,
                servletRequest(request)
        );
    }

    @Override
    protected ResponseEntity<Object>
    handleExceptionInternal(
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse>
    handleUnexpectedError(
            Exception exception,
            HttpServletRequest request
    ) {
        if (exception
                instanceof
                org.springframework.web.ErrorResponse
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

        ResponseStatus responseStatus =
                AnnotatedElementUtils
                        .findMergedAnnotation(
                                exception.getClass(),
                                ResponseStatus.class
                        );

        if (responseStatus != null) {
            int status =
                    responseStatus
                            .code()
                            .value();

            if (status >= 400
                    && status <= 599) {

                PublicError publicError =
                        publicErrorForStatus(
                                status
                        );

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
                unknownErrorMessage,
                null,
                HttpHeaders.EMPTY,
                request
        );
    }

    private ResponseEntity<ErrorResponse>
    createResponse(
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

    private ResponseEntity<Object>
    createObjectResponse(
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

        /*
         * Логируем уже после формирования body:
         * errorId в response и log гарантированно
         * один и тот же.
         */
        errorLogger.log(
                unifiedError,
                currentService
        );

        return ResponseEntity
                .status(
                        body.getStatus()
                )
                .headers(
                        copyHeaders(headers)
                )
                .body(body);
    }

    private UnifiedErrorException
    createUnifiedError(
            Exception cause,
            int status,
            String errorCode,
            String message,
            ErrorDetails details,
            HttpServletRequest request
    ) {
        Instant timestamp =
                Instant.now();

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
                technicalDetailsFactory.enrich(
                        details,
                        cause
                ),
                context,
                maxChainSize
        );
    }

    private ResponseEntity<ErrorResponse>
    toResponseEntity(
            UnifiedErrorException exception,
            HttpHeaders headers
    ) {
        ErrorResponse body =
                exception.toResponse(
                        currentService
                );

        errorLogger.log(
                exception,
                currentService
        );

        return ResponseEntity
                .status(
                        body.getStatus()
                )
                .headers(
                        copyHeaders(headers)
                )
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
                .service(
                        currentService
                )
                .component(
                        location.component()
                )
                .operation(
                        location.operation()
                )
                .errorCode(
                        errorCode
                )
                .message(
                        message
                )
                .timestamp(
                        timestamp
                )
                .status(
                        status
                )
                .build();
    }

    private static RequestLocation
    resolveLocation(
            HttpServletRequest request
    ) {
        Object handler =
                request.getAttribute(
                        HandlerMapping
                                .BEST_MATCHING_HANDLER_ATTRIBUTE
                );

        if (handler
                instanceof
                HandlerMethod handlerMethod) {

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

    private static HttpHeaders copyHeaders(
            HttpHeaders headers
    ) {
        if (headers == null
                || headers.isEmpty()) {

            return new HttpHeaders();
        }

        return HttpHeaders.copyOf(
                headers
        );
    }

    private static HttpServletRequest
    servletRequest(
            WebRequest request
    ) {
        if (request
                instanceof
                ServletWebRequest
                        servletWebRequest) {

            return servletWebRequest
                    .getRequest();
        }

        throw new IllegalStateException(
                "Servlet WebRequest is required"
        );
    }

    private static ErrorDetails
    typeMismatchDetails(
            TypeMismatchException exception
    ) {
        String rawField;

        if (exception
                instanceof
                MethodArgumentTypeMismatchException
                        mismatch) {

            rawField = mismatch.getName();

        } else {

            rawField =
                    exception.getPropertyName();
        }

        LimitedFieldName field =
                limitedFieldName(
                        rawField
                );

        return ErrorDetails.builder()
                .violations(
                        List.of(
                                ErrorDetails
                                        .FieldViolation
                                        .of(
                                                field.value(),
                                                "INVALID_TYPE",
                                                "Некорректный "
                                                        + "тип значения",
                                                field.truncated()
                                        )
                        )
                )
                .build();
    }

    private static LimitedViolations
    methodValidationViolations(
            HandlerMethodValidationException exception
    ) {
        List<ErrorDetails.FieldViolation>
                result =
                new ArrayList<>(
                        MAX_VALIDATION_ERRORS
                );

        for (ParameterValidationResult
                validationResult
                : exception
                .getParameterValidationResults()) {

            if (validationResult
                    instanceof
                    ParameterErrors errors) {

                for (ObjectError error
                        : errors.getAllErrors()) {

                    if (result.size()
                            >= MAX_VALIDATION_ERRORS) {

                        return new LimitedViolations(
                                List.copyOf(result),
                                true
                        );
                    }

                    result.add(
                            toFieldViolation(
                                    error
                            )
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

                if (result.size()
                        >= MAX_VALIDATION_ERRORS) {

                    return new LimitedViolations(
                            List.copyOf(result),
                            true
                    );
                }

                result.add(
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

            if (result.size()
                    >= MAX_VALIDATION_ERRORS) {

                return new LimitedViolations(
                        List.copyOf(result),
                        true
                );
            }

            result.add(
                    toFieldViolation(
                            "request",
                            error
                    )
            );
        }

        return new LimitedViolations(
                List.copyOf(result),
                false
        );
    }

    private static ErrorDetails.FieldViolation
    toFieldViolation(
            ObjectError error
    ) {
        LimitedFieldName field =
                error
                        instanceof
                        FieldError fieldError
                        ? limitedFieldName(
                        fieldError.getField()
                )
                        : new LimitedFieldName(
                        "request",
                        false
                );

        String reasonCode =
                validationReasonCode(
                        error.getCode()
                );

        return ErrorDetails
                .FieldViolation
                .of(
                        field.value(),
                        reasonCode,
                        validationMessage(
                                reasonCode
                        ),
                        field.truncated()
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

        LimitedFieldName limitedField =
                limitedFieldName(
                        field
                );

        return ErrorDetails
                .FieldViolation
                .of(
                        limitedField.value(),
                        reasonCode,
                        validationMessage(
                                reasonCode
                        ),
                        limitedField.truncated()
                );
    }

    private static ErrorDetails.FieldViolation
    toFieldViolation(
            ConstraintViolation<?> violation
    ) {
        LimitedFieldName field =
                limitedFieldName(
                        lastPathElement(
                                violation
                                        .getPropertyPath()
                                        .toString()
                        )
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

        return ErrorDetails
                .FieldViolation
                .of(
                        field.value(),
                        reasonCode,
                        validationMessage(
                                reasonCode
                        ),
                        field.truncated()
                );
    }

    private static ErrorDetails
    validationDetails(
            List<ErrorDetails.FieldViolation>
                    violations,
            boolean truncated
    ) {
        ErrorDetails details =
                violations.isEmpty()
                        ? null
                        : ErrorDetails.builder()
                        .violations(
                                violations
                        )
                        .build();

        if (!truncated) {
            return details;
        }

        return ErrorDetails.mergeTruncation(
                details,
                TruncationInfo.data()
        );
    }

    private static String parameterName(
            MethodParameter parameter
    ) {
        String name =
                parameter
                        .getParameterName();

        if (name == null
                || name.isBlank()) {

            return "argument"
                    + parameter
                    .getParameterIndex();
        }

        return name;
    }

    private static String
    validationReasonCode(
            String springCode
    ) {
        if (springCode == null) {
            return "INVALID";
        }

        if (springCode.contains("NotNull")
                || springCode.contains(
                "NotBlank"
        )
                || springCode.contains(
                "NotEmpty"
        )) {

            return "REQUIRED";
        }

        if (springCode.contains("Size")
                || springCode.contains(
                "Length"
        )) {

            return "INVALID_SIZE";
        }

        if (springCode.contains("Min")
                || springCode.contains(
                "DecimalMin"
        )
                || springCode.contains(
                "Positive"
        )) {

            return "TOO_SMALL";
        }

        if (springCode.contains("Max")
                || springCode.contains(
                "DecimalMax"
        )
                || springCode.contains(
                "Negative"
        )) {

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

    private PublicError publicErrorForStatus(
            int status
    ) {
        if (status < 400
                || status > 599) {

            return new PublicError(
                    500,
                    INTERNAL_ERROR_CODE,
                    unknownErrorMessage
            );
        }

        return switch (status) {

            case 400 ->
                    new PublicError(
                            400,
                            "INVALID_REQUEST",
                            "Некорректный запрос"
                    );

            case 401 ->
                    new PublicError(
                            401,
                            "AUTHENTICATION_REQUIRED",
                            "Требуется аутентификация"
                    );

            case 403 ->
                    new PublicError(
                            403,
                            "ACCESS_DENIED",
                            "Доступ запрещён"
                    );

            case 404 ->
                    new PublicError(
                            404,
                            "RESOURCE_NOT_FOUND",
                            "Ресурс не найден"
                    );

            case 405 ->
                    new PublicError(
                            405,
                            "METHOD_NOT_ALLOWED",
                            "HTTP-метод не поддерживается"
                    );

            case 406 ->
                    new PublicError(
                            406,
                            "NOT_ACCEPTABLE",
                            "Запрошенный формат ответа "
                                    + "не поддерживается"
                    );

            case 409 ->
                    new PublicError(
                            409,
                            "CONFLICT",
                            "Запрос конфликтует "
                                    + "с текущим "
                                    + "состоянием ресурса"
                    );

            case 413 ->
                    new PublicError(
                            413,
                            "PAYLOAD_TOO_LARGE",
                            "Размер запроса превышает "
                                    + "допустимый предел"
                    );

            case 415 ->
                    new PublicError(
                            415,
                            "UNSUPPORTED_MEDIA_TYPE",
                            "Формат тела запроса "
                                    + "не поддерживается"
                    );

            case 422 ->
                    new PublicError(
                            422,
                            "UNPROCESSABLE_CONTENT",
                            "Запрос не может "
                                    + "быть обработан"
                    );

            case 429 ->
                    new PublicError(
                            429,
                            "TOO_MANY_REQUESTS",
                            "Превышено допустимое "
                                    + "количество запросов"
                    );

            case 500 ->
                    new PublicError(
                            500,
                            INTERNAL_ERROR_CODE,
                            unknownErrorMessage
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
                        unknownErrorMessage
                );
            }
        };
    }

    private static String lastCode(
            String[] codes
    ) {
        if (codes == null
                || codes.length == 0) {

            return null;
        }

        return codes[
                codes.length - 1
                ];
    }

    private static String lastPathElement(
            String path
    ) {
        if (path == null
                || path.isBlank()) {

            return "request";
        }

        int separator =
                path.lastIndexOf('.');

        return separator >= 0
                ? path.substring(
                separator + 1
        )
                : path;
    }

    private static LimitedFieldName
    limitedFieldName(
            String field
    ) {
        if (field == null
                || field.isBlank()) {

            return new LimitedFieldName(
                    "request",
                    false
            );
        }

        String normalized =
                field
                        .replace(
                                '\r',
                                '_'
                        )
                        .replace(
                                '\n',
                                '_'
                        )
                        .replace(
                                '\t',
                                '_'
                        )
                        .trim();

        if (normalized.isEmpty()) {
            return new LimitedFieldName(
                    "request",
                    false
            );
        }

        if (normalized.length() > 160) {
            return new LimitedFieldName(
                    normalized.substring(
                            0,
                            160
                    ),
                    true
            );
        }

        return new LimitedFieldName(
                normalized,
                false
        );
    }

    private static String validateServiceName(
            String value
    ) {
        String normalized =
                value == null
                        || value.isBlank()
                        ? "application"
                        : value.trim();

        if (normalized.length() > 120) {
            throw new IllegalArgumentException(
                    "spring.application.name "
                            + "must not exceed "
                            + "120 characters"
            );
        }

        if (normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\t') >= 0) {

            throw new IllegalArgumentException(
                    "spring.application.name "
                            + "must not contain "
                            + "control characters"
            );
        }

        return normalized;
    }

    private static int validateMaxChainSize(
            int value
    ) {
        if (value < 1
                || value > 100) {

            throw new IllegalArgumentException(
                    "catchup.errors."
                            + "max-chain-size "
                            + "must be from 1 to 100"
            );
        }

        return value;
    }

    private record LimitedViolations(
            List<ErrorDetails.FieldViolation> values,
            boolean truncated
    ) {
    }

    private record LimitedFieldName(
            String value,
            boolean truncated
    ) {
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