# Unified Error Library

Библиотека предоставляет единый формат ошибок для Spring MVC-приложений, поддерживает накопление цепочки прохождения ошибки и преобразует исключения REST-приложения в структурированный HTTP-ответ.

Текущая версия содержит:

* `ErrorResponse` — публичный JSON-ответ;
* `ChainElement` — один элемент цепочки ошибки;
* `ExceptionChain` — компонент управления цепочкой;
* `UnifiedErrorException` — базовое структурированное исключение;
* `BusinessException` — контролируемая бизнес-ошибка;
* `ErrorDetails` — типизированные дополнительные сведения;
* `ErrorDetails.FieldViolation` — ошибка отдельного поля;
* `UnifiedGlobalExceptionHandler` — глобальный обработчик Spring MVC;
* `ErrorModelValidation` — внутренняя валидация моделей.

## Требования

Минимальная версия Java:

```text
Java 21
```

Приложения, подключающие библиотеку, также должны использовать Java 21 или более новую версию.

## Maven-координаты

```xml
<dependency>
    <groupId>ru.oreoman4ik</groupId>
    <artifactId>unified-error-model</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Проект собирается как обычный JAR.

`spring-boot-maven-plugin` не используется, потому что библиотека не является исполняемым Spring Boot-приложением.

Основные зависимости:

```xml
<dependency>
    <groupId>tools.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webmvc</artifactId>
</dependency>

<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>

<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <scope>provided</scope>
</dependency>
```

Полный Spring Boot Web starter и встроенный сервер библиотека транзитивно не подключает.

---

# Публичный JSON-контракт

Java- и JSON-названия полей совпадают.

Корневой ответ содержит:

```text
errorId
timestamp
status
message
errorCode
currentService
chain
details
```

Элемент цепочки содержит:

```text
service
component
operation
errorCode
message
timestamp
status
```

Названия `errorType`, `exceptionType`, `causeCode`, `httpStatus` и `publicMessage` не используются.

JSON-алиасы отсутствуют. У каждого значения есть одно каноническое имя.

## Пример ответа

```json
{
  "errorId": "7c12c42e-86ee-43b0-8324-9a56bf633ed4",
  "timestamp": "2026-07-30T10:42:15.018Z",
  "status": 404,
  "message": "Компонент не найден",
  "errorCode": "COMPONENT_NOT_FOUND",
  "currentService": "service-a",
  "chain": [
    {
      "service": "service-b",
      "component": "ComponentRepository",
      "operation": "findById",
      "errorCode": "COMPONENT_NOT_FOUND",
      "message": "Компонент не найден",
      "timestamp": "2026-07-30T10:42:15.018Z",
      "status": 404
    },
    {
      "service": "service-b",
      "component": "ComponentService",
      "operation": "findComponent",
      "errorCode": "COMPONENT_NOT_FOUND",
      "message": "Компонент не найден",
      "timestamp": "2026-07-30T10:42:15.050Z",
      "status": 404
    },
    {
      "service": "service-a",
      "component": "ComponentGateway",
      "operation": "loadComponent",
      "errorCode": "UPSTREAM_ERROR",
      "message": "Не удалось получить компонент",
      "timestamp": "2026-07-30T10:42:15.119Z",
      "status": 404
    }
  ],
  "details": {
    "resource": "COMPONENT"
  }
}
```

Пример JSON хранится по пути:

```text
src/main/resources/json/error-response-example.json
```

---

# ErrorResponse

`ErrorResponse` — неизменяемая модель публичного ответа об ошибке.

## Поля

| Поле             | Java-тип             | Обязательность | Назначение                            |
| ---------------- | -------------------- | -------------- | ------------------------------------- |
| `errorId`        | `UUID`               | обязательно    | Идентификатор одной логической ошибки |
| `timestamp`      | `Instant`            | обязательно    | Время возникновения исходной ошибки   |
| `status`         | `int`                | обязательно    | HTTP-статус ошибки от 400 до 599      |
| `message`        | `String`             | обязательно    | Безопасное публичное сообщение        |
| `errorCode`      | `String`             | обязательно    | Стабильный публичный код              |
| `currentService` | `String`             | обязательно    | Сервис, сформировавший ответ          |
| `chain`          | `List<ChainElement>` | обязательно    | Непустая цепочка ошибки               |
| `details`        | `ErrorDetails`       | необязательно  | Дополнительные публичные сведения     |

Нельзя создать `ErrorResponse` без:

* `errorId`;
* `timestamp`;
* корректного `status`;
* непустого `message`;
* корректного `errorCode`;
* непустого `currentService`;
* непустой `chain`.

`details` может быть равен `null`, если дополнительных безопасных сведений нет.

## Создание

```java
ErrorResponse response = ErrorResponse.builder()
        .errorId(errorId)
        .timestamp(timestamp)
        .status(404)
        .message("Компонент не найден")
        .errorCode("COMPONENT_NOT_FOUND")
        .currentService("service-a")
        .chain(chainElements)
        .details(details)
        .build();
```

## Методы

```java
response.getErrorId();
response.getTimestamp();
response.getStatus();
response.getMessage();
response.getErrorCode();
response.getCurrentService();
response.getChain();
response.getDetails();
```

---

# ChainElement

`ChainElement` описывает один уровень прохождения ошибки через сервис, компонент или операцию.

## Поля

| Поле        | Java-тип  | Обязательность | Назначение                     |
| ----------- | --------- | -------------- | ------------------------------ |
| `service`   | `String`  | обязательно    | Название сервиса               |
| `component` | `String`  | обязательно    | Название компонента или класса |
| `operation` | `String`  | обязательно    | Название операции              |
| `errorCode` | `String`  | обязательно    | Публичный код ошибки           |
| `message`   | `String`  | обязательно    | Безопасное публичное сообщение |
| `timestamp` | `Instant` | обязательно    | Время добавления элемента      |
| `status`    | `Integer` | необязательно  | HTTP-статус, если он известен  |

`status == null` допустим, если на конкретном уровне HTTP-статус ещё не был определён.

Если статус указан, он должен находиться в диапазоне от 400 до 599.

## Создание

```java
ChainElement element = ChainElement.builder()
        .service("service-b")
        .component("ComponentRepository")
        .operation("findById")
        .errorCode("COMPONENT_NOT_FOUND")
        .message("Компонент не найден")
        .timestamp(Instant.now())
        .status(404)
        .build();
```

## Методы

```java
element.getService();
element.getComponent();
element.getOperation();
element.getErrorCode();
element.getMessage();
element.getTimestamp();
element.getStatus();
```

---

# Формат timestamp

Все timestamp сериализуются в UTC в формате:

```text
yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
```

Пример:

```text
2026-07-30T10:42:15.018Z
```

Формат закреплён через `@JsonFormat` непосредственно в моделях и не должен зависеть от глобальных настроек `JsonMapper`.

## Верхнеуровневый timestamp

`ErrorResponse.timestamp` и `UnifiedErrorException.timestamp` означают время возникновения исходной ошибки.

Timestamp создаётся один раз и сохраняется при передаче ошибки между уровнями приложения и микросервисами.

## Timestamp элемента цепочки

`ChainElement.timestamp` означает время добавления конкретного контекста.

Поэтому элементы одной цепочки могут иметь разные timestamp.

---

# HTTP-статусы

Модели предназначены только для ошибок.

Допустимый диапазон:

```text
400–599
```

Допустимые примеры:

```text
400
404
409
422
500
502
503
504
```

Недопустимые примеры:

```text
100
200
204
302
399
600
```

Попытка создать модель ошибки с успешным или redirect-статусом приводит к `IllegalArgumentException`.

---

# Публичные коды

Публичные коды используются в:

* `ErrorResponse.errorCode`;
* `ChainElement.errorCode`;
* `UnifiedErrorException.errorCode`;
* `BusinessException.errorCode`;
* `ErrorDetails.resource`;
* `FieldViolation.reasonCode`.

Формат:

```text
[A-Z][A-Z0-9_]{1,63}
```

Допустимые значения:

```text
COMPONENT_NOT_FOUND
VALIDATION_ERROR
UPSTREAM_ERROR
REMOTE_TIMEOUT
ACCESS_DENIED
REQUIRED
INVALID_SIZE
```

Недопустимые значения:

```text
component_not_found
NOT-FOUND
error code
NullPointerException
NULLPOINTEREXCEPTION
DATA_INTEGRITY_VIOLATION_EXCEPTION
org.springframework.dao.DataIntegrityViolationException
```

Значения, заканчивающиеся на `EXCEPTION`, отклоняются.

Нельзя формировать публичный код из имени Java-исключения:

```java
exception.getClass().getName();
exception.getClass().getSimpleName();
```

Нужно использовать заранее определённый публичный код:

```java
"COMPONENT_NOT_FOUND"
```

Для большого приложения рекомендуется хранить коды в enum или наборе констант.

---

# Безопасность публичных сообщений

Поле `message` предназначено для внешнего клиента.

Модель проверяет:

* значение не равно `null`;
* строка не пустая;
* длина не превышает 500 символов;
* отсутствуют `\r`, `\n` и `\t`.

Модель не выполняет автоматический поиск SQL, токенов, URL или stack trace с помощью blacklist-регулярных выражений.

Безопасность обеспечивается тем, что сообщения должны поступать из контролируемого каталога приложения.

Нельзя:

```java
.message(exception.getMessage())
```

Правильно:

```java
.message("Компонент не найден")
```

Техническое сообщение, причины и stack trace должны записываться в серверный лог.

`UnifiedErrorException.getMessage()` возвращает публичное сообщение.

Исходная техническая причина доступна через:

```java
exception.getOriginalCause();
exception.getCause();
```

---

# ErrorDetails

`ErrorDetails` содержит только заранее определённые дополнительные сведения.

Он заменяет небезопасный тип:

```java
Map<String, Object>
```

## Поля

| Поле                | Тип                    | Назначение              |
| ------------------- | ---------------------- | ----------------------- |
| `resource`          | `String`               | Публичный код ресурса   |
| `violations`        | `List<FieldViolation>` | Ошибки валидации        |
| `retryAfterSeconds` | `Long`                 | Задержка перед повтором |

Все поля по отдельности необязательны, но полностью пустой `ErrorDetails` создать нельзя.

Если дополнительных сведений нет, используется:

```java
details == null
```

## Создание

```java
ErrorDetails details = ErrorDetails.builder()
        .resource("COMPONENT")
        .retryAfterSeconds(30L)
        .build();
```

## Ограничения

* `retryAfterSeconds` не может быть отрицательным;
* список `violations` содержит не более 100 элементов;
* список не может содержать `null`;
* входной список копируется;
* возвращаемый список является неизменяемым.

---

# FieldViolation

`FieldViolation` описывает публичную ошибку конкретного поля.

## Поля

| Поле         | Тип      | Назначение            |
| ------------ | -------- | --------------------- |
| `field`      | `String` | Публичное имя поля    |
| `reasonCode` | `String` | Публичный код причины |
| `message`    | `String` | Безопасное сообщение  |

## Создание

```java
ErrorDetails.FieldViolation violation =
        ErrorDetails.FieldViolation.of(
                "name",
                "REQUIRED",
                "Название обязательно"
        );
```

Отклонённое значение поля намеренно не хранится.

Поле вроде `rejectedValue` может содержать пароль, токен, персональные или другие чувствительные данные.

---

# ExceptionChain

`ExceptionChain` хранит историю прохождения одной ошибки.

Объект является неизменяемым: `add()` возвращает новую цепочку либо тот же экземпляр, если добавление не требуется.

## Создание пустой цепочки

```java
ExceptionChain chain = ExceptionChain.empty(10);
```

Допустимый диапазон `maxSize`:

```text
1–100
```

## Восстановление существующей цепочки

```java
ExceptionChain chain = ExceptionChain.of(
        existingElements,
        10
);
```

Переданный список копируется.

Изменение исходного списка после создания объекта не влияет на цепочку.

## Добавление элемента

```java
chain = chain.add(element);
```

Элемент добавляется в конец цепочки.

Порядок:

1. место возникновения ошибки;
2. следующий слой обработки;
3. следующий сервис;
4. текущий внешний уровень.

## Защита от дубликатов

Повторным уровнем считается одинаковое сочетание:

```text
service + component + operation
```

Если такой уровень уже присутствует, новый элемент не добавляется.

Различия в `errorCode`, `message`, `timestamp` или `status` не создают новую запись, если сервис, компонент и операция совпадают.

## Ограничение размера

После достижения `maxSize` новые элементы не добавляются.

Проверка:

```java
chain.isLimitReached();
```

Существующие элементы не удаляются и не меняют порядок.

## Основные методы

```java
ExceptionChain.empty(maxSize);
ExceptionChain.of(elements, maxSize);

chain.add(element);
chain.getElements();
chain.size();
chain.isEmpty();
chain.getMaxSize();
chain.isLimitReached();
chain.containsLevel(element);
```

---

# UnifiedErrorException

`UnifiedErrorException` передаёт одну логическую структурированную ошибку между уровнями приложения.

Исключение хранит:

| Поле            | Тип              | Назначение                 |
| --------------- | ---------------- | -------------------------- |
| `errorId`       | `UUID`           | Неизменяемый идентификатор |
| `timestamp`     | `Instant`        | Время исходной ошибки      |
| `status`        | `int`            | HTTP-статус                |
| `errorCode`     | `String`         | Публичный код              |
| `message`       | `String`         | Публичное сообщение        |
| `details`       | `ErrorDetails`   | Дополнительные сведения    |
| `originalCause` | `Throwable`      | Исходная причина           |
| `chain`         | `ExceptionChain` | История прохождения        |

При добавлении нового контекста:

* используется тот же объект `UnifiedErrorException`;
* `errorId` остаётся неизменным;
* timestamp не изменяется;
* статус и код не изменяются;
* `details` не теряется;
* исходная причина сохраняется;
* дополняется только цепочка.

## Создание локальной ошибки

```java
UnifiedErrorException exception =
        UnifiedErrorException.from(
                cause,
                404,
                "COMPONENT_NOT_FOUND",
                "Компонент не найден",
                10
        );
```

В этом варианте:

* создаётся новый `errorId`;
* timestamp фиксируется через `Instant.now()`;
* `details` равен `null`;
* цепочка изначально пустая.

## Создание с полными данными

```java
UnifiedErrorException exception =
        UnifiedErrorException.from(
                cause,
                timestamp,
                404,
                "COMPONENT_NOT_FOUND",
                "Компонент не найден",
                details,
                10
        );
```

## Создание с первым контекстом

```java
UnifiedErrorException exception =
        UnifiedErrorException.from(
                cause,
                timestamp,
                404,
                "COMPONENT_NOT_FOUND",
                "Компонент не найден",
                details,
                initialContext,
                10
        );
```

## Повторная обработка

Если в `from(...)` передан уже существующий `UnifiedErrorException`, возвращается тот же экземпляр.

```java
UnifiedErrorException same =
        UnifiedErrorException.from(
                existing,
                500,
                "INTERNAL_ERROR",
                "Другое сообщение",
                10
        );
```

Новый `errorId` при этом не создаётся.

## Добавление контекста

```java
exception.addContext(
        ChainElement.builder()
                .service("service-a")
                .component("ComponentGateway")
                .operation("loadComponent")
                .errorCode("UPSTREAM_ERROR")
                .message("Не удалось получить компонент")
                .timestamp(Instant.now())
                .status(404)
                .build()
);
```

`addContext()` возвращает тот же экземпляр исключения.

## Восстановление из удалённого ответа

```java
UnifiedErrorException exception =
        UnifiedErrorException.fromResponse(
                remoteResponse,
                httpClientException,
                10
        );
```

Сохраняются:

* `errorId`;
* исходный timestamp;
* статус;
* публичное сообщение;
* `errorCode`;
* `details`;
* существующая цепочка.

После восстановления можно добавить локальный контекст:

```java
exception.addContext(localContext);
```

## Удалённая цепочка длиннее локального лимита

Если полученная цепочка уже длиннее локального лимита, существующие элементы сохраняются.

Эффективный лимит становится не меньше размера восстановленной цепочки.

Новые элементы после этого не добавляются.

Так библиотека не теряет полученную межсервисную историю и не увеличивает цепочку бесконтрольно.

## Преобразование в ErrorResponse

```java
ErrorResponse response =
        exception.toResponse("service-a");
```

В ответ переносятся:

* `errorId`;
* timestamp;
* статус;
* сообщение;
* `errorCode`;
* `details`;
* цепочка.

Перед преобразованием цепочка должна содержать хотя бы один элемент.

## Основные методы

```java
exception.getErrorId();
exception.getTimestamp();
exception.getStatus();
exception.getErrorCode();
exception.getMessage();
exception.getDetails();
exception.getOriginalCause();
exception.getCause();
exception.getChain();
exception.getChainElements();
exception.isChainLimitReached();

exception.addContext(context);
exception.toResponse(currentService);
```

---

# BusinessException

`BusinessException` представляет контролируемую бизнес-ошибку приложения.

Она хранит:

* HTTP-статус;
* публичный код;
* публичное сообщение;
* необязательные `details`;
* необязательную техническую причину.

## Создание

```java
throw new BusinessException(
        404,
        "COMPONENT_NOT_FOUND",
        "Компонент не найден"
);
```

С дополнительными сведениями:

```java
throw new BusinessException(
        409,
        "BOOK_ALREADY_EXISTS",
        "Книга уже существует",
        ErrorDetails.builder()
                .resource("BOOK")
                .build()
);
```

С технической причиной:

```java
throw new BusinessException(
        500,
        "BOOK_PROCESSING_ERROR",
        "Не удалось обработать книгу",
        null,
        technicalException
);
```

В публичное сообщение нельзя передавать `technicalException.getMessage()`.

---

# Глобальный обработчик

Библиотека предоставляет:

```java
UnifiedGlobalExceptionHandler
```

Класс помечен:

```java
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
```

Он преобразует исключения Spring MVC в единый `ErrorResponse`.

## Подключение

Если пакет библиотеки входит в component scan приложения, обработчик будет зарегистрирован как Spring bean.

Если пакет не входит в component scan, его можно импортировать явно:

```java
@Configuration
@Import(UnifiedGlobalExceptionHandler.class)
public class ErrorHandlingConfiguration {
}
```

## Настройки

Название текущего сервиса берётся из:

```yaml
spring:
  application:
    name: catalog-service
```

Если настройка отсутствует, используется:

```text
application
```

Максимальный размер цепочки:

```yaml
catchup:
  errors:
    max-chain-size: 10
```

Значение по умолчанию:

```text
10
```

Допустимый диапазон:

```text
1–100
```

## Поддерживаемые исключения

| Исключение или категория           |          HTTP-статус | Публичный код               |
| ---------------------------------- | -------------------: | --------------------------- |
| `UnifiedErrorException`            |          сохранённый | сохранённый                 |
| `BusinessException`                | заданный приложением | заданный приложением        |
| `MethodArgumentNotValidException`  |                  400 | `VALIDATION_ERROR`          |
| `ConstraintViolationException`     |                  400 | `VALIDATION_ERROR`          |
| входная method validation          |                  400 | `VALIDATION_ERROR`          |
| ошибка валидации результата метода |                  500 | `RESPONSE_VALIDATION_ERROR` |
| удалённый HTTP 4xx                 |         исходный 4xx | `REMOTE_CLIENT_ERROR`       |
| удалённый HTTP 5xx                 |         исходный 5xx | `REMOTE_SERVER_ERROR`       |
| таймаут HTTP-вызова                |                  504 | `REMOTE_TIMEOUT`            |
| недоступный удалённый сервис       |                  503 | `REMOTE_UNAVAILABLE`        |
| другая ошибка HTTP-клиента         |                  502 | `REMOTE_REQUEST_ERROR`      |
| стандартная ошибка Spring 400      |                  400 | `INVALID_REQUEST`           |
| стандартная ошибка Spring 401      |                  401 | `AUTHENTICATION_REQUIRED`   |
| стандартная ошибка Spring 403      |                  403 | `ACCESS_DENIED`             |
| стандартная ошибка Spring 404      |                  404 | `RESOURCE_NOT_FOUND`        |
| стандартная ошибка Spring 405      |                  405 | `METHOD_NOT_ALLOWED`        |
| стандартная ошибка Spring 409      |                  409 | `CONFLICT`                  |
| стандартная ошибка Spring 415      |                  415 | `UNSUPPORTED_MEDIA_TYPE`    |
| стандартная ошибка Spring 422      |                  422 | `UNPROCESSABLE_CONTENT`     |
| стандартная ошибка Spring 429      |                  429 | `TOO_MANY_REQUESTS`         |
| неизвестное исключение             |                  500 | `INTERNAL_ERROR`            |

---

# Обработка валидации

Для ошибок входных данных создаётся:

```json
{
  "status": 400,
  "message": "Переданные данные некорректны",
  "errorCode": "VALIDATION_ERROR",
  "details": {
    "violations": [
      {
        "field": "name",
        "reasonCode": "REQUIRED",
        "message": "Поле обязательно"
      }
    ]
  }
}
```

Поддерживаемые публичные коды нарушений:

| Ограничение                       | Код              |
| --------------------------------- | ---------------- |
| `NotNull`, `NotBlank`, `NotEmpty` | `REQUIRED`       |
| `Size`, `Length`                  | `INVALID_SIZE`   |
| `Min`, `DecimalMin`, `Positive`   | `TOO_SMALL`      |
| `Max`, `DecimalMax`, `Negative`   | `TOO_LARGE`      |
| `Email`                           | `INVALID_EMAIL`  |
| `Pattern`                         | `INVALID_FORMAT` |
| другие ограничения                | `INVALID`        |

Исходное сообщение Bean Validation наружу не передаётся.

Отклонённое значение поля также не включается в ответ.

---

# Исходящие HTTP-запросы

Глобальный обработчик учитывает исключения синхронных Spring HTTP-клиентов.

## Удалённый HTTP 4xx

Возвращается исходный статус удалённого сервиса:

```json
{
  "status": 404,
  "message": "Удалённый сервис отклонил запрос",
  "errorCode": "REMOTE_CLIENT_ERROR"
}
```

## Удалённый HTTP 5xx

Возвращается исходный статус:

```json
{
  "status": 503,
  "message": "Удалённый сервис завершил запрос с ошибкой",
  "errorCode": "REMOTE_SERVER_ERROR"
}
```

## Таймаут

```json
{
  "status": 504,
  "message": "Истекло время ожидания ответа удалённого сервиса",
  "errorCode": "REMOTE_TIMEOUT"
}
```

## Недоступный сервис

```json
{
  "status": 503,
  "message": "Удалённый сервис недоступен",
  "errorCode": "REMOTE_UNAVAILABLE"
}
```

## Ошибка преобразования ответа

```json
{
  "status": 502,
  "message": "Не удалось обработать ответ удалённого сервиса",
  "errorCode": "REMOTE_REQUEST_ERROR"
}
```

Тело удалённого ответа и техническое сообщение HTTP-клиента наружу не передаются.

Текущая версия глобального обработчика не восстанавливает `UnifiedErrorException` автоматически из JSON-тела удалённой ошибки. Для ручного восстановления используется:

```java
UnifiedErrorException.fromResponse(
        remoteResponse,
        clientException,
        maxChainSize
);
```

---

# Безопасность глобального обработчика

В публичный ответ не передаются:

* `exception.getMessage()` неизвестного исключения;
* stack trace;
* имя Java-класса исключения;
* тело ошибочного ответа удалённого сервиса;
* rejected value при ошибках валидации;
* SQL-запросы;
* JDBC URL;
* токены и пароли;
* внутренние пути файлов;
* URI запроса, который может содержать идентификаторы.

Для неизвестного исключения всегда используется безопасный ответ:

```json
{
  "status": 500,
  "message": "Внутренняя ошибка сервиса",
  "errorCode": "INTERNAL_ERROR"
}
```

Полный ответ также содержит `errorId`, timestamp, текущий сервис и цепочку.

---

# Пользовательские обработчики

Библиотека не должна блокировать пользовательские обработчики приложения.

Глобальный обработчик имеет минимальный приоритет:

```java
@Order(Ordered.LOWEST_PRECEDENCE)
```

Локальный обработчик контроллера:

```java
@ExceptionHandler(CustomApplicationException.class)
```

будет применён раньше глобального обработчика.

Приложение также может объявить собственный `@ControllerAdvice` с более высоким приоритетом:

```java
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApplicationExceptionHandler {
}
```

Такой обработчик сможет переопределить библиотечное поведение.

---

# Пример полного сценария

```java
Instant timestamp = Instant.now();

ErrorDetails details = ErrorDetails.builder()
        .resource("COMPONENT")
        .build();

ChainElement repositoryContext =
        ChainElement.builder()
                .service("catalog-service")
                .component("ComponentRepository")
                .operation("findById")
                .errorCode("COMPONENT_NOT_FOUND")
                .message("Компонент не найден")
                .timestamp(timestamp)
                .status(404)
                .build();

UnifiedErrorException exception =
        UnifiedErrorException.from(
                new IllegalStateException(
                        "Technical repository message"
                ),
                timestamp,
                404,
                "COMPONENT_NOT_FOUND",
                "Компонент не найден",
                details,
                repositoryContext,
                10
        );

exception.addContext(
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

throw exception;
```

`UnifiedGlobalExceptionHandler` добавит контекст REST-контроллера и сформирует `ErrorResponse`.

При этом сохраняются:

* один `errorId`;
* исходный timestamp;
* код ошибки;
* `details`;
* исходная техническая причина;
* все уникальные элементы цепочки.

---

# Неизменяемость

`ErrorResponse`, `ChainElement`, `ErrorDetails` и `FieldViolation`:

* объявлены как `final`;
* содержат `private final` поля;
* не имеют setters;
* копируют входные коллекции;
* не допускают `null` внутри коллекций;
* реализуют `equals()`, `hashCode()` и `toString()`.

`ExceptionChain` является неизменяемым value-объектом.

В `UnifiedErrorException` неизменяемы:

* `errorId`;
* timestamp;
* статус;
* код;
* `details`;
* исходная причина.

Изменяемой является только ссылка на текущий immutable-объект `ExceptionChain`.

---

# JSON-совместимость

Модели используют:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
```

Это позволяет читать JSON с дополнительными неизвестными полями.

Правила развития контракта:

1. Новые поля добавляются только как необязательные.
2. Тип существующего поля не изменяется.
3. Назначение существующего поля не изменяется.
4. Новая версия должна принимать отсутствие нового поля.
5. Старые клиенты должны игнорировать неизвестные поля.
6. Для одного значения используется одно каноническое имя.
7. `@JsonAlias` в первой версии не используется.

Переименование опубликованного JSON-поля является несовместимым изменением и требует новой версии контракта.

---

# Тестирование

Запуск:

```bash
./mvnw test
```

На Linux и macOS Maven Wrapper должен иметь право на выполнение:

```bash
chmod +x mvnw
```

## Тесты моделей

Проверяют:

* обязательность полей;
* диапазон HTTP-статусов;
* точный формат timestamp;
* публичные коды;
* запрет Java-типов исключений;
* неизменяемость списков;
* ограничения размера;
* value-семантику;
* чтение неизвестных полей.

## ExceptionChainTests

Проверяют:

* порядок элементов;
* сохранение существующих элементов;
* отсутствие дубликатов;
* ограничение размера;
* неизменяемость входной коллекции.

## UnifiedErrorExceptionTests

Проверяют:

* создание из обычного исключения;
* сохранение исходной причины;
* сохранение timestamp, кода и `details`;
* неизменность `errorId`;
* добавление контекста к тому же исключению;
* повторную обработку;
* восстановление из `ErrorResponse`;
* сохранение удалённой цепочки;
* поведение при превышении лимита;
* преобразование обратно в `ErrorResponse`.

## UnifiedGlobalExceptionHandlerTests

Проверяют:

* бизнес-ошибки;
* ошибки валидации;
* HTTP 4xx удалённого сервиса;
* HTTP 5xx удалённого сервиса;
* таймаут;
* стандартную Spring-ошибку 404;
* неизвестное исключение;
* отсутствие технических данных;
* приоритет локального пользовательского обработчика;
* минимальный приоритет глобального advice.

---

# Известные ограничения

Текущая версия пока не содержит:

* Spring Boot starter и автоконфигурацию;
* автоматическое логирование;
* AOP-аннотацию для добавления контекста;
* автоматическое восстановление структурированной ошибки из тела удалённого HTTP-ответа;
* конфигурирование сообщений через отдельный каталог;
* поддержку `WebClient`;
* поддержку реактивного Spring WebFlux;
* признак, сообщающий клиенту, что цепочка достигла лимита;
* автоматическую корреляцию ошибки между сервисами через HTTP-заголовок.

Эти возможности могут быть добавлены в следующих задачах поверх существующих моделей, `ExceptionChain`, `UnifiedErrorException` и `UnifiedGlobalExceptionHandler`.
