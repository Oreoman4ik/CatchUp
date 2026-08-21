# CatchUp Spring Boot Starter

`CatchUp` — Spring Boot starter для унифицированной обработки ошибок в Spring MVC-приложениях.

Стартер автоматически:

- формирует единый публичный JSON-контракт ошибок;
- сохраняет `errorId` и цепочку прохождения ошибки между слоями и сервисами;
- обрабатывает `BusinessException`, ошибки валидации, стандартные Spring MVC ошибки и неизвестные исключения;
- преобразует ошибки исходящих вызовов через `RestClient` и `RestTemplate`;
- восстанавливает структурированную ошибку другого сервиса, если удалённый сервис также использует контракт CatchUp;
- ограничивает размер error response удалённого сервиса до его полного буферизования Spring;
- поддерживает AOP-аннотацию `@ErrorContext` для добавления контекста слоя/операции;
- логирует одну логическую ошибку один раз с тем же `errorId`, который получает клиент;
- сообщает клиенту о потере данных через `details.truncation`;
- по умолчанию не публикует технические детали исключений.

## Требования

- Java 21+
- Spring Boot 4.1.x
- Servlet / Spring MVC приложение

WebFlux и `WebClient` в текущей версии не поддерживаются.

## Подключение

```xml
<dependency>
    <groupId>ru.oreoman4ik</groupId>
    <artifactId>catchup-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Проект собирается как обычный JAR. `spring-boot-maven-plugin` для repackaging не требуется, потому что стартер не является исполняемым приложением.

После подключения ручной `@Import` не нужен. Автоконфигурация регистрируется через:

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

и содержит:

```text
ru.oreoman4ik.catchup.autoconfigure.UnifiedErrorAutoConfiguration
```

## Быстрый старт

Достаточно задать имя приложения:

```yaml
spring:
  application:
    name: catalog-service
```

Контроллер:

```java
@RestController
@RequestMapping("/components")
public class ComponentController {

    @GetMapping("/{id}")
    public ComponentDto getById(
            @PathVariable UUID id
    ) {
        throw new BusinessException(
                404,
                "COMPONENT_NOT_FOUND",
                "Компонент не найден"
        );
    }
}
```

Клиент получит структурированный ответ вида:

```json
{
  "errorId": "7c12c42e-86ee-43b0-8324-9a56bf633ed4",
  "timestamp": "2026-07-30T10:42:15.018Z",
  "status": 404,
  "message": "Компонент не найден",
  "errorCode": "COMPONENT_NOT_FOUND",
  "currentService": "catalog-service",
  "chain": [
    {
      "service": "catalog-service",
      "component": "ComponentController",
      "operation": "getById",
      "errorCode": "COMPONENT_NOT_FOUND",
      "message": "Компонент не найден",
      "timestamp": "2026-07-30T10:42:15.018Z",
      "status": 404
    }
  ]
}
```

---

# Конфигурация

Все настройки имеют префикс `catchup.errors`.

```yaml
catchup:
  errors:
    enabled: true
    service-name: catalog-service
    max-chain-size: 10
    include-technical-details: false
    include-stack-trace: false
    unknown-error-message: "Внутренняя ошибка сервиса"
    max-remote-body-bytes: 2097152
    max-stack-trace-lines: 100
    max-technical-text-length: 1000
```

| Property | Значение по умолчанию | Ограничение | Назначение |
| --- | ---: | --- | --- |
| `catchup.errors.enabled` | `true` | boolean | Полностью включает/выключает автоконфигурацию CatchUp |
| `catchup.errors.service-name` | не задано | до 120 символов | Явное имя текущего сервиса |
| `catchup.errors.max-chain-size` | `10` | `1..100` | Максимальное число элементов цепочки |
| `catchup.errors.include-technical-details` | `false` | boolean | Разрешает локальные `details.technical` |
| `catchup.errors.include-stack-trace` | `false` | boolean | Добавляет stack trace в `details.technical` |
| `catchup.errors.unknown-error-message` | `Внутренняя ошибка сервиса` | до 500 символов | Публичное сообщение для неизвестной ошибки |
| `catchup.errors.max-remote-body-bytes` | `2097152` (2 MiB) | `1024..8388608` | Максимальный error body удалённого HTTP-ответа |
| `catchup.errors.max-stack-trace-lines` | `100` | `1..100` | Максимальное число строк stack trace в technical details |
| `catchup.errors.max-technical-text-length` | `1000` | `128..1000` | Максимальная длина одной технической строки |

`include-stack-trace=true` допускается только вместе с:

```yaml
catchup:
  errors:
    include-technical-details: true
    include-stack-trace: true
```

Иначе приложение завершит создание контекста с ошибкой конфигурации.

## Как определяется имя сервиса

Приоритет:

1. `catchup.errors.service-name`;
2. `spring.application.name`;
3. значение `application`.

Например:

```yaml
spring:
  application:
    name: catalog-service
```

достаточно, если отдельное имя для CatchUp не требуется.

## Полное отключение

```yaml
catchup:
  errors:
    enabled: false
```

В этом режиме starter beans не создаются.

---

# Автоконфигурация

`UnifiedErrorAutoConfiguration` активируется для Servlet web application и автоматически создаёт основные компоненты CatchUp:

- `CurrentServiceName`;
- `TechnicalDetailsFactory`;
- `RemoteBodyLimitingInterceptor`;
- `CatchUpRestClientCustomizer`;
- `CatchUpRestTemplateCustomizer`;
- `RemoteErrorResponseDecoder`;
- `OutgoingHttpExceptionMapper`;
- `ErrorContextAspect`;
- `ErrorLogSink`;
- `UnifiedErrorLogger`;
- `UnifiedGlobalExceptionHandler`.

Большинство компонентов создаются через `@ConditionalOnMissingBean`, поэтому приложение может подменить стандартную реализацию собственным bean.

Например, свой sink логирования:

```java
@Bean
ErrorLogSink errorLogSink() {
    return (level, message, throwable) -> {
        // собственная интеграция с логированием
    };
}
```

---

# Публичный JSON-контракт

Корневой объект `ErrorResponse` содержит:

| Поле | Тип | Обязательность | Назначение |
| --- | --- | --- | --- |
| `errorId` | `UUID` | обязательно | Идентификатор одной логической ошибки |
| `timestamp` | `Instant` | обязательно | Timestamp исходной ошибки |
| `status` | `int` | обязательно | HTTP error status `400..599` |
| `message` | `String` | обязательно | Безопасное публичное сообщение |
| `errorCode` | `String` | обязательно | Стабильный публичный код |
| `currentService` | `String` | обязательно | Сервис, формирующий текущий HTTP-ответ |
| `chain` | `List<ChainElement>` | обязательно | Непустая цепочка прохождения ошибки |
| `details` | `ErrorDetails` | необязательно | Дополнительные публичные/технические данные |

Java- и JSON-названия совпадают. Алиасы для `errorType`, `exceptionType`, `causeCode`, `httpStatus`, `publicMessage` и подобных старых имён не используются.

## Timestamp

Формат фиксирован:

```text
yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
```

Часовой пояс — UTC.

Пример:

```text
2026-07-30T10:42:15.018Z
```

## Публичные коды

`errorCode`, `reasonCode` и `resource` используют формат:

```text
[A-Z][A-Z0-9_]{1,63}
```

Примеры:

```text
COMPONENT_NOT_FOUND
VALIDATION_ERROR
REMOTE_TIMEOUT
INVALID_TYPE
```

Код не должен заканчиваться на `EXCEPTION`, чтобы в публичный контракт не попадали Java-типы исключений.

## Ограничение публичного сообщения

Публичное сообщение:

- обязательно там, где оно требуется моделью;
- не может быть пустым;
- не может содержать `\r`, `\n`, `\t`;
- автоматически ограничивается 500 символами.

Если строка была сокращена, информация об этом сохраняется в `details.truncation`.

---

# ErrorDetails

`ErrorDetails` содержит дополнительные сведения:

| Поле | Тип | Назначение |
| --- | --- | --- |
| `resource` | `String` | Публичный код ресурса |
| `violations` | `List<FieldViolation>` | Ошибки отдельных полей/параметров |
| `retryAfterSeconds` | `Long` | Через сколько секунд допустима повторная попытка |
| `technical` | `TechnicalDetails` | Локальные технические сведения, если явно включены |
| `truncation` | `TruncationInfo` | Признаки потери данных из-за лимитов |

Пустой `ErrorDetails` создать нельзя: хотя бы одно поле должно содержать значение.

Пример:

```java
ErrorDetails details = ErrorDetails.builder()
        .resource("COMPONENT")
        .retryAfterSeconds(30L)
        .build();
```

## FieldViolation

```json
{
  "field": "email",
  "reasonCode": "INVALID_EMAIL",
  "message": "Некорректный адрес электронной почты"
}
```

Ограничения:

- максимум 100 violations в одном `ErrorDetails`;
- `field` — максимум 160 символов;
- `message` — максимум 500 символов;
- `reasonCode` — публичный код.

Создание вручную:

```java
ErrorDetails.FieldViolation violation =
        ErrorDetails.FieldViolation.of(
                "email",
                "INVALID_EMAIL",
                "Некорректный адрес электронной почты"
        );
```

---

# TruncationInfo

`details.truncation` сообщает клиенту, что библиотеке пришлось отбросить или сократить часть данных.

Пример:

```json
{
  "details": {
    "truncation": {
      "chain": true,
      "message": true,
      "technicalDetails": true,
      "remoteBody": true,
      "data": true
    }
  }
}
```

В JSON выводятся только `true`-значения.

| Поле | Значение |
| --- | --- |
| `chain` | Один или несколько уникальных элементов цепочки не поместились в `max-chain-size` |
| `message` | Верхнеуровневое публичное сообщение было сокращено |
| `technicalDetails` | Технические данные были сокращены из-за настроенных лимитов |
| `remoteBody` | Error body удалённого сервиса превысил `max-remote-body-bytes` и не был разобран как структурированный CatchUp response |
| `data` | Были сокращены/отброшены другие публичные данные, например validation errors, имя поля или вложенное сообщение |

Пример: если Spring вернул 250 validation errors, клиент получит максимум 100 и:

```json
{
  "details": {
    "violations": [
      "... максимум 100 элементов ..."
    ],
    "truncation": {
      "data": true
    }
  }
}
```

Если `BusinessException` получает сообщение длиннее 500 символов, в ответе сохраняется:

```json
{
  "details": {
    "truncation": {
      "message": true
    }
  }
}
```

---

# ChainElement и ExceptionChain

`ChainElement` описывает один уровень прохождения ошибки:

```json
{
  "service": "catalog-service",
  "component": "ComponentService",
  "operation": "findComponent",
  "errorCode": "COMPONENT_NOT_FOUND",
  "message": "Компонент не найден",
  "timestamp": "2026-07-30T10:42:15.050Z",
  "status": 404
}
```

Создание:

```java
ChainElement context = ChainElement.builder()
        .service("catalog-service")
        .component("ComponentService")
        .operation("findComponent")
        .errorCode("COMPONENT_NOT_FOUND")
        .message("Компонент не найден")
        .timestamp(Instant.now())
        .status(404)
        .build();
```

Один логический уровень определяется сочетанием:

```text
service + component + operation
```

Повтор такого же уровня не добавляется второй раз.

`ExceptionChain` неизменяем. `add(...)` возвращает новый объект цепочки. Максимальный размер задаётся `max-chain-size` и не может превышать 100.

Если новый уникальный уровень уже не помещается, цепочка остаётся ограниченной, а `truncation.chain` становится `true`.

---

# BusinessException

Для ожидаемых бизнес-ошибок используется `BusinessException`:

```java
throw new BusinessException(
        409,
        "BOOK_ALREADY_EXISTS",
        "Книга уже существует"
);
```

С `details`:

```java
ErrorDetails details = ErrorDetails.builder()
        .resource("BOOK")
        .build();

throw new BusinessException(
        409,
        "BOOK_ALREADY_EXISTS",
        "Книга уже существует",
        details
);
```

С исходной технической причиной:

```java
throw new BusinessException(
        409,
        "BOOK_ALREADY_EXISTS",
        "Книга уже существует",
        details,
        cause
);
```

Не передавайте в публичное сообщение произвольный `exception.getMessage()`. Для клиента должны использоваться заранее определённые безопасные сообщения.

---

# UnifiedErrorException

`UnifiedErrorException` — внутреннее структурированное исключение, которое сохраняет:

- `errorId`;
- исходный timestamp;
- HTTP status;
- публичный `errorCode`;
- публичное сообщение;
- `ErrorDetails`;
- исходную техническую причину;
- `ExceptionChain`.

Создание:

```java
UnifiedErrorException error =
        UnifiedErrorException.from(
                cause,
                404,
                "COMPONENT_NOT_FOUND",
                "Компонент не найден",
                10
        );
```

Добавление контекста:

```java
error.addContext(
        ChainElement.builder()
                .service("catalog-service")
                .component("ComponentService")
                .operation("findComponent")
                .errorCode("COMPONENT_NOT_FOUND")
                .message("Компонент не найден")
                .timestamp(Instant.now())
                .status(404)
                .build()
);
```

Если `UnifiedErrorException.from(...)` получает уже существующий `UnifiedErrorException`, создаётся не новая логическая ошибка: существующий объект и его `errorId` сохраняются.

Для восстановления ошибки другого CatchUp-сервиса используется `fromResponse(...)`, но в обычном Spring Boot приложении это делает `OutgoingHttpExceptionMapper` автоматически.

---

# @ErrorContext

Аннотация `@ErrorContext` добавляет контекст операции к цепочке, если метод завершился исключением.

```java
@Service
public class ComponentService {

    @ErrorContext(
            operation = "findComponent",
            message = "Не удалось получить компонент"
    )
    public ComponentDto find(UUID id) {
        // ...
    }
}
```

Поля аннотации:

```java
String service() default "";
String operation() default "";
String message() default "";
```

Если `service` не задан, используется текущее имя приложения.

Если `operation` не задан, используется имя Java-метода.

`component` определяется по фактическому target class Spring bean.

Если `message` не задан:

- для уже структурированной ошибки используется её публичное сообщение;
- для `BusinessException` используется бизнес-сообщение;
- для неизвестной ошибки используется `catchup.errors.unknown-error-message`;
- для исходящего HTTP-вызова используется безопасное сообщение категории HTTP-ошибки.

Аспект перехватывает `Exception`, но намеренно не ловит JVM `Error`, например `OutOfMemoryError` или `StackOverflowError`.

---

# Глобальный Spring MVC handler

`UnifiedGlobalExceptionHandler` регистрируется автоматически как `@RestControllerAdvice`.

Он обрабатывает:

- `UnifiedErrorException`;
- `BusinessException`;
- `ConstraintViolationException`;
- `MethodArgumentNotValidException`;
- `HandlerMethodValidationException`;
- ошибки чтения JSON body;
- ошибки преобразования path/query параметров;
- `RestClientException`;
- стандартные Spring MVC ошибки;
- исключения с `@ResponseStatus`;
- неизвестные `Exception`.

Для неизвестной ошибки используется безопасный ответ:

```json
{
  "status": 500,
  "message": "Внутренняя ошибка сервиса",
  "errorCode": "INTERNAL_ERROR"
}
```

В полном JSON также будут `errorId`, `timestamp`, `currentService` и `chain`.

## Стандартные HTTP-коды

Основные mappings:

| HTTP | `errorCode` |
| ---: | --- |
| 400 | `INVALID_REQUEST` |
| 401 | `AUTHENTICATION_REQUIRED` |
| 403 | `ACCESS_DENIED` |
| 404 | `RESOURCE_NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 406 | `NOT_ACCEPTABLE` |
| 409 | `CONFLICT` |
| 413 | `PAYLOAD_TOO_LARGE` |
| 415 | `UNSUPPORTED_MEDIA_TYPE` |
| 422 | `UNPROCESSABLE_CONTENT` |
| 429 | `TOO_MANY_REQUESTS` |
| 500 | `INTERNAL_ERROR` |
| другой 4xx | `CLIENT_ERROR` |
| другой 5xx | `SERVER_ERROR` |

Spring headers, важные для конкретной ошибки, сохраняются, например `Allow`, `Accept` или `Retry-After`.

## Приоритет пользовательских handlers

CatchUp advice имеет:

```java
@Order(-1)
```

Локальный `@ExceptionHandler` внутри контроллера имеет приоритет перед global advice.

Обычный `@ControllerAdvice` без `@Order` не перекрывает CatchUp. Если приложению нужно полностью переопределить обработку определённой ошибки, задайте более высокий приоритет, например:

```java
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApplicationExceptionHandler {
}
```

---

# Валидация

CatchUp никогда не публикует rejected value из Spring validation error.

Пример ответа:

```json
{
  "status": 400,
  "errorCode": "VALIDATION_ERROR",
  "message": "Переданные данные некорректны",
  "details": {
    "violations": [
      {
        "field": "email",
        "reasonCode": "INVALID_EMAIL",
        "message": "Некорректный адрес электронной почты"
      }
    ]
  }
}
```

Поддерживаемые нормализованные reason codes:

| Ограничение | `reasonCode` |
| --- | --- |
| `NotNull`, `NotBlank`, `NotEmpty` | `REQUIRED` |
| `Size`, `Length` | `INVALID_SIZE` |
| `Min`, `DecimalMin`, `Positive` | `TOO_SMALL` |
| `Max`, `DecimalMax`, `Negative` | `TOO_LARGE` |
| `Email` | `INVALID_EMAIL` |
| `Pattern` | `INVALID_FORMAT` |
| остальное | `INVALID` |

Для некорректного типа path/query параметра используется:

```text
INVALID_PARAMETER
```

и violation с:

```text
INVALID_TYPE
```

Для некорректного JSON request body:

```text
INVALID_REQUEST_BODY
```

Для ошибки validation возвращаемого значения метода:

```text
RESPONSE_VALIDATION_ERROR
```

со статусом `500`.

Одновременно клиенту возвращается максимум 100 validation errors. Если реальных ошибок больше, выставляется:

```json
{
  "truncation": {
    "data": true
  }
}
```

---

# Исходящие HTTP-запросы

CatchUp интегрируется с синхронными Spring HTTP clients:

- `RestClient`;
- `RestTemplate`.

Стартер регистрирует `RestClientCustomizer` и `RestTemplateCustomizer`, которые добавляют `RemoteBodyLimitingInterceptor`.

## Ограничение remote error body

`catchup.errors.max-remote-body-bytes` ограничивает тело **до полного буферизования error response Spring**.

При настройке:

```yaml
catchup:
  errors:
    max-remote-body-bytes: 2097152
```

и удалённом error response размером 300 MiB сценарий выглядит так:

```text
HTTP error stream
    ↓
RemoteBodyLimitingInterceptor
    ↓
Spring может прочитать не больше 2 MiB + 1 byte
    ↓
RestClientResponseException содержит ограниченное тело
    ↓
RemoteErrorResponseDecoder определяет превышение лимита
    ↓
details.truncation.remoteBody = true
```

Дополнительный один байт нужен только для определения факта превышения лимита.

Успешные HTTP responses этим interceptor не ограничиваются.

### Важно

Автоматический customizer применяется к Spring Boot-managed `RestClient.Builder` / `RestTemplate`.

Если приложение создаёт клиент самостоятельно в обход Boot customizers, например напрямую через `RestClient.create()`, автоматическая установка CatchUp interceptor не гарантируется. В таком случае клиент должен быть настроен приложением явно.

## Восстановление CatchUp response другого сервиса

Если remote error является валидным `ErrorResponse`, CatchUp пытается восстановить исходную логическую ошибку.

Для восстановления необходимо, чтобы:

- body укладывался в `max-remote-body-bytes`;
- JSON успешно десериализовался как `ErrorResponse`;
- `status` внутри JSON совпадал с фактическим HTTP status.

При успешном восстановлении сохраняются:

- remote `errorId`;
- исходный timestamp;
- status;
- публичный `errorCode`;
- публичное message;
- публичные `details`;
- удалённая chain в пределах локального `max-chain-size`.

Затем добавляется контекст текущего сервиса.

Remote `technical` details намеренно не проксируются. Если локально включены technical details, они формируются из локального HTTP exception.

## Fallback mapping исходящих ошибок

Если remote body не является валидным CatchUp response, используется безопасная категоризация:

| Ситуация | HTTP | `errorCode` |
| --- | ---: | --- |
| Remote 4xx | исходный 4xx | `REMOTE_CLIENT_ERROR` |
| Remote 5xx | исходный 5xx | `REMOTE_SERVER_ERROR` |
| Timeout | 504 | `REMOTE_TIMEOUT` |
| Connect / DNS / no route | 503 | `REMOTE_UNAVAILABLE` |
| Ошибка сериализации request body | 500 | `OUTGOING_REQUEST_BODY_ERROR` |
| Ошибка десериализации remote response | 502 | `REMOTE_BODY_CONVERSION_ERROR` |
| Общая HTTP conversion error | 500 | `OUTGOING_HTTP_CONVERSION_ERROR` |
| Неожиданный EOF при чтении response | 502 | `REMOTE_RESPONSE_READ_ERROR` |
| Другая I/O/network error | 502 | `REMOTE_NETWORK_ERROR` |
| Другой `RestClientException` | 502 | `OUTGOING_HTTP_ERROR` |

Техническое сообщение HTTP-клиента и raw remote body клиенту не публикуются.

---

# Technical details

По умолчанию:

```yaml
catchup:
  errors:
    include-technical-details: false
    include-stack-trace: false
```

поэтому в HTTP response нет:

```json
{
  "details": {
    "technical": {}
  }
}
```

Если явно включить:

```yaml
catchup:
  errors:
    include-technical-details: true
```

CatchUp может добавить локальный класс исключения:

```json
{
  "details": {
    "technical": {
      "exceptionClass": "java.lang.IllegalStateException"
    }
  }
}
```

`Throwable.getMessage()` намеренно не публикуется автоматически даже при включённых technical details.

Для stack trace:

```yaml
catchup:
  errors:
    include-technical-details: true
    include-stack-trace: true
    max-stack-trace-lines: 50
    max-technical-text-length: 500
```

Если stack trace или технические строки пришлось сократить:

```json
{
  "details": {
    "truncation": {
      "technicalDetails": true
    }
  }
}
```

Remote technical details удаляются при проксировании ошибки между сервисами.

---

# Логирование

По умолчанию используется SLF4J logger:

```text
ru.oreoman4ik.catchup.errors
```

Уровень определяется по HTTP status:

- `4xx` → `WARN`;
- `5xx` → `ERROR`.

Формат сообщения содержит:

```text
catchup_error
errorId=...
service=...
operation=...
status=...
errorCode=...
chain=[...]
```

В logger передаётся `originalCause`, поэтому полный stack trace остаётся в server logs, даже если он не публикуется клиенту.

Один экземпляр `UnifiedErrorException` логируется только один раз. Это предотвращает повторную запись одной и той же логической ошибки при её прохождении через несколько слоёв.

Пример собственного sink:

```java
@Component
public class CustomErrorLogSink
        implements ErrorLogSink {

    @Override
    public void write(
            ErrorLogLevel level,
            String message,
            Throwable throwable
    ) {
        // отправка в собственную систему логирования
    }
}
```

---

# Безопасность

CatchUp разделяет публичные и технические данные.

По умолчанию клиенту не передаются:

- произвольный `exception.getMessage()` неизвестного исключения;
- raw stack trace;
- raw body ошибочного ответа удалённого сервиса;
- rejected value из Bean Validation;
- remote technical details;
- SQL/JDBC details только потому, что они оказались в сообщении исключения;
- токены, пароли и внутренние URI только потому, что они присутствуют в тексте технической ошибки.

Неизвестное исключение преобразуется в безопасный `INTERNAL_ERROR`.

При этом исходное исключение сохраняется как `originalCause` и доступно серверному логированию.

---

# JSON-совместимость

Публичные модели используют:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
```

Это позволяет новой версии сервиса добавлять необязательные поля без поломки старых клиентов.

Рекомендации для развития контракта:

1. новые поля добавлять как необязательные;
2. не менять тип опубликованного поля;
3. не менять смысл опубликованного поля;
4. сохранять каноническое имя каждого значения;
5. не переименовывать существующие поля без новой версии контракта;
6. старые клиенты должны игнорировать неизвестные поля.

---
