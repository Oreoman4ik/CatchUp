# Unified Error Model

Библиотека предоставляет модели для унифицированного представления ошибок, хранения цепочки прохождения ошибки и передачи структурированной ошибки между уровнями приложения.

Текущая версия содержит:

* `ErrorResponse` — публичный JSON-ответ;
* `ChainElement` — один элемент цепочки ошибки;
* `ExceptionChain` — компонент управления цепочкой;
* `UnifiedErrorException` — базовое структурированное исключение;
* `ErrorDetails` — типизированные дополнительные сведения;
* `ErrorDetails.FieldViolation` — описание ошибки отдельного поля;
* `ErrorModelValidation` — внутренняя валидация моделей.

Текущий проект является библиотекой моделей и исключений. Он пока не содержит Spring Boot starter, глобальный `ControllerAdvice`, AOP и автоматическую обработку HTTP-клиентов.

## Требования

Минимальная версия Java:

```text
Java 21
```

Приложения, которые подключают библиотеку, также должны использовать Java 21 или более новую версию.

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

Основная compile-зависимость — Jackson:

```xml
<dependency>
    <groupId>tools.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

Полный Spring MVC starter, встроенный сервер и MVC test starter для текущих моделей не требуются.

---

# Публичный JSON-контракт

Java- и JSON-названия полей совпадают.

Для корневого ответа используются:

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

Для элемента цепочки используются:

```text
service
component
operation
errorCode
message
timestamp
status
```

Названия `errorType`, `exceptionType`, `causeCode`, `httpStatus` и `publicMessage` в публичном контракте не используются.

JSON-алиасы отсутствуют. Каждое поле имеет одно каноническое имя.

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

Полный пример JSON рекомендуется хранить по пути:

```text
src/main/resources/json/error-response-example.json
```

---

# ErrorResponse

`ErrorResponse` — неизменяемая модель публичного ответа об ошибке.

Класс содержит полное представление ошибки, которое может быть передано внешнему клиенту или восстановлено из ответа другого сервиса.

## Поля

| Поле             | Java-тип             | Обязательность | Назначение                             |
| ---------------- | -------------------- | -------------- | -------------------------------------- |
| `errorId`        | `UUID`               | обязательно    | Идентификатор одной логической ошибки  |
| `timestamp`      | `Instant`            | обязательно    | Время возникновения исходной ошибки    |
| `status`         | `int`                | обязательно    | HTTP-статус ошибки от 400 до 599       |
| `message`        | `String`             | обязательно    | Безопасное публичное сообщение         |
| `errorCode`      | `String`             | обязательно    | Стабильный публичный код ошибки        |
| `currentService` | `String`             | обязательно    | Сервис, сформировавший текущий ответ   |
| `chain`          | `List<ChainElement>` | обязательно    | Непустая цепочка прохождения ошибки    |
| `details`        | `ErrorDetails`       | необязательно  | Типизированные дополнительные сведения |

## Обязательные поля

Нельзя создать `ErrorResponse` без:

* `errorId`;
* `timestamp`;
* корректного `status`;
* непустого `message`;
* корректного `errorCode`;
* непустого `currentService`;
* непустой `chain`.

`details` может быть равен `null`, если дополнительных безопасных сведений нет.

При `details == null` поле не включается в JSON.

## Создание ответа

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

### `builder()`

Создаёт новый builder.

### `getErrorId()`

Возвращает идентификатор логической ошибки.

### `getTimestamp()`

Возвращает время возникновения исходной ошибки.

### `getStatus()`

Возвращает итоговый HTTP-статус.

### `getMessage()`

Возвращает безопасное публичное сообщение.

### `getErrorCode()`

Возвращает стабильный публичный код ошибки.

### `getCurrentService()`

Возвращает название сервиса, сформировавшего ответ.

### `getChain()`

Возвращает неизменяемую непустую цепочку.

### `getDetails()`

Возвращает дополнительные сведения или `null`.

---

# ChainElement

`ChainElement` описывает один уровень прохождения ошибки.

Один элемент содержит контекст конкретного сервиса, компонента и операции.

## Поля

| Поле        | Java-тип  | Обязательность | Назначение                          |
| ----------- | --------- | -------------- | ----------------------------------- |
| `service`   | `String`  | обязательно    | Название сервиса                    |
| `component` | `String`  | обязательно    | Название компонента или класса      |
| `operation` | `String`  | обязательно    | Название операции                   |
| `errorCode` | `String`  | обязательно    | Публичный код ошибки на этом уровне |
| `message`   | `String`  | обязательно    | Безопасное публичное сообщение      |
| `timestamp` | `Instant` | обязательно    | Время добавления элемента           |
| `status`    | `Integer` | необязательно  | HTTP-статус, если он известен       |

`status == null` допустим, если на конкретном уровне обработки HTTP-статус ещё не был определён.

Если статус задан, он должен находиться в диапазоне от 400 до 599.

## Создание элемента

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

### `getService()`

Возвращает название сервиса.

### `getComponent()`

Возвращает название компонента или класса.

### `getOperation()`

Возвращает название операции.

### `getErrorCode()`

Возвращает публичный код ошибки.

### `getMessage()`

Возвращает безопасное публичное сообщение.

### `getTimestamp()`

Возвращает время добавления контекста.

### `getStatus()`

Возвращает HTTP-статус или `null`.

---

# Формат timestamp

Все timestamp сериализуются в UTC в точном формате:

```text
yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
```

Пример:

```text
2026-07-30T10:42:15.018Z
```

Формат закреплён в модели через `@JsonFormat`.

Он не должен зависеть от глобальной настройки `JsonMapper`, которая может включать числовое представление времени.

## Верхнеуровневый timestamp

`ErrorResponse.timestamp` и `UnifiedErrorException.timestamp` означают время возникновения исходной ошибки.

Это значение создаётся один раз и не изменяется при прохождении ошибки через следующие уровни или сервисы.

При восстановлении ошибки из ответа удалённого сервиса исходный timestamp сохраняется.

## Timestamp элемента цепочки

`ChainElement.timestamp` означает время добавления конкретного контекста.

Поэтому разные элементы одной цепочки могут иметь разные timestamp.

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

# Публичные коды ошибок

Публичные коды используются в:

* `ErrorResponse.errorCode`;
* `ChainElement.errorCode`;
* `ErrorDetails.resource`;
* `FieldViolation.reasonCode`;
* `UnifiedErrorException.errorCode`.

Формат кода:

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

Код не должен создаваться из имени Java-класса:

```java
exception.getClass().getName()
```

или:

```java
exception.getClass().getSimpleName()
```

Правильно использовать заранее определённый код:

```java
"COMPONENT_NOT_FOUND"
```

Для большого проекта рекомендуется хранить коды в enum или наборе констант.

---

# Безопасность публичного сообщения

Поле `message` предназначено для внешнего клиента.

Модель проверяет:

* значение не равно `null`;
* строка не пустая;
* длина не превышает 500 символов;
* строка не содержит `\r`, `\n` и `\t`.

Модель не выполняет автоматический поиск SQL, токенов, URL и stack trace по blacklist-регулярным выражениям.

Безопасность обеспечивается тем, что публичный текст должен поступать из контролируемого каталога сообщений.

Нельзя:

```java
.message(exception.getMessage())
```

Правильно:

```java
.message("Компонент не найден")
```

Техническое сообщение исключения, его причины и stack trace должны передаваться в систему логирования, а не в публичный ответ.

`UnifiedErrorException.getMessage()` возвращает именно публичное сообщение.

Исходное техническое исключение доступно отдельно:

```java
exception.getOriginalCause()
```

и через стандартный метод:

```java
exception.getCause()
```

---

# ErrorDetails

`ErrorDetails` содержит только заранее определённые дополнительные сведения.

Он заменяет небезопасный:

```java
Map<String, Object>
```

## Поля

| Поле                | Тип                    | Назначение                        |
| ------------------- | ---------------------- | --------------------------------- |
| `resource`          | `String`               | Публичный код ресурса             |
| `violations`        | `List<FieldViolation>` | Список ошибок валидации           |
| `retryAfterSeconds` | `Long`                 | Задержка перед повторной попыткой |

Все отдельные поля необязательны, но полностью пустой `ErrorDetails` создать нельзя.

Если дополнительных сведений нет, в `ErrorResponse` и `UnifiedErrorException` нужно использовать `details == null`.

## Создание

```java
ErrorDetails details = ErrorDetails.builder()
        .resource("COMPONENT")
        .retryAfterSeconds(30L)
        .build();
```

## Ограничения

* `retryAfterSeconds` не может быть отрицательным;
* `violations` содержит не более 100 элементов;
* список не может содержать `null`;
* входной список копируется;
* возвращаемый список нельзя изменить.

---

# FieldViolation

`FieldViolation` описывает ошибку конкретного поля.

## Поля

| Поле         | Тип      | Назначение              |
| ------------ | -------- | ----------------------- |
| `field`      | `String` | Публичное название поля |
| `reasonCode` | `String` | Публичный код причины   |
| `message`    | `String` | Безопасное сообщение    |

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

Например, модель не содержит `rejectedValue`, потому что значение может быть паролем, токеном или персональной информацией.

---

# ExceptionChain

`ExceptionChain` управляет историей прохождения одной ошибки через сервисы, компоненты и операции.

Объект цепочки является неизменяемым: метод `add()` возвращает новую цепочку либо тот же экземпляр, если изменение не требуется.

## Создание пустой цепочки

```java
ExceptionChain chain = ExceptionChain.empty(10);
```

Число `10` — максимальный размер цепочки.

Допустимый диапазон максимального размера:

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

Изменение `existingElements` после создания цепочки не влияет на `ExceptionChain`.

## Добавление элемента

```java
chain = chain.add(element);
```

Новый элемент добавляется в конец.

Порядок элементов:

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

Это предотвращает появление дубликатов при повторной обработке одного уровня.

## Ограничение размера

После достижения `maxSize` новые элементы не добавляются.

Метод:

```java
chain.isLimitReached()
```

возвращает `true`, если цепочка достигла максимального размера.

Существующие элементы при этом не удаляются и не переставляются.

## Методы

### `empty(int maxSize)`

Создаёт пустую цепочку.

### `of(List<ChainElement> elements, int maxSize)`

Создаёт цепочку из существующих элементов.

### `add(ChainElement element)`

Добавляет новый уникальный уровень, если лимит ещё не достигнут.

### `getElements()`

Возвращает неизменяемые элементы в порядке добавления.

### `size()`

Возвращает текущее количество элементов.

### `isEmpty()`

Проверяет, пуста ли цепочка.

### `getMaxSize()`

Возвращает установленный лимит.

### `isLimitReached()`

Проверяет достижение лимита.

### `containsLevel(ChainElement candidate)`

Проверяет наличие уровня с тем же сервисом, компонентом и операцией.

---

# UnifiedErrorException

`UnifiedErrorException` — базовое исключение библиотеки для передачи одной структурированной ошибки между уровнями приложения.

Оно хранит:

| Поле            | Тип              | Назначение                          |
| --------------- | ---------------- | ----------------------------------- |
| `errorId`       | `UUID`           | Неизменяемый идентификатор ошибки   |
| `timestamp`     | `Instant`        | Время возникновения исходной ошибки |
| `status`        | `int`            | HTTP-статус ошибки                  |
| `errorCode`     | `String`         | Публичный код ошибки                |
| `message`       | `String`         | Публичное сообщение                 |
| `details`       | `ErrorDetails`   | Дополнительные сведения             |
| `originalCause` | `Throwable`      | Исходная техническая причина        |
| `chain`         | `ExceptionChain` | Цепочка прохождения ошибки          |

При добавлении контекста не создаётся новая независимая ошибка:

* используется тот же экземпляр `UnifiedErrorException`;
* `errorId` остаётся прежним;
* `timestamp` остаётся прежним;
* `errorCode` остаётся прежним;
* `details` не теряется;
* исходная причина не меняется;
* обновляется только цепочка.

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

## Создание с полным набором данных

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

Этот вариант используется, когда время возникновения и дополнительные сведения уже известны.

## Создание с первым контекстом

```java
UnifiedErrorException exception =
        UnifiedErrorException.from(
                cause,
                404,
                "COMPONENT_NOT_FOUND",
                "Компонент не найден",
                initialContext,
                10
        );
```

Или с полным набором данных:

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

## Повторная обработка UnifiedErrorException

Если в `from(...)` передан объект, который уже является `UnifiedErrorException`, возвращается тот же экземпляр:

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

В этом случае новый `errorId` не создаётся и существующая структурированная информация не заменяется.

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

`addContext()` возвращает тот же экземпляр `UnifiedErrorException`.

Метод синхронизирован, но исключение предназначено прежде всего для обработки одного запроса и не должно использоваться как общий глобальный объект между независимыми потоками.

## Восстановление из ответа другого сервиса

```java
UnifiedErrorException exception =
        UnifiedErrorException.fromResponse(
                remoteResponse,
                httpClientException,
                10
        );
```

При восстановлении сохраняются:

* `errorId`;
* исходный `timestamp`;
* `status`;
* `message`;
* `errorCode`;
* `details`;
* существующая цепочка.

В качестве `originalCause` сохраняется локальное исключение, возникшее при HTTP-вызове или разборе удалённого ответа.

После восстановления можно добавить контекст вызывающего сервиса:

```java
exception.addContext(localContext);
```

При этом удалённые элементы не теряются.

## Удалённая цепочка длиннее локального лимита

Если полученная цепочка уже длиннее локального `maxChainSize`, существующие удалённые элементы сохраняются.

Эффективный лимит становится не меньше размера восстановленной цепочки.

Цепочка считается достигшей лимита, и новые элементы больше не добавляются.

Это позволяет не терять уже полученную межсервисную историю и одновременно не увеличивать ответ бесконтрольно.

## Преобразование в ErrorResponse

Перед передачей глобальному обработчику исключение можно преобразовать в публичный ответ:

```java
ErrorResponse response =
        exception.toResponse("service-a");
```

В ответ переносятся без изменений:

* `errorId`;
* `timestamp`;
* `status`;
* `message`;
* `errorCode`;
* `details`;
* цепочка.

Перед вызовом `toResponse()` цепочка должна содержать хотя бы один элемент.

Если цепочка пуста, будет выброшен `IllegalStateException`.

## Методы UnifiedErrorException

### `getErrorId()`

Возвращает неизменяемый идентификатор ошибки.

### `getTimestamp()`

Возвращает время возникновения исходной ошибки.

### `getStatus()`

Возвращает HTTP-статус.

### `getErrorCode()`

Возвращает публичный код ошибки.

### `getMessage()`

Возвращает публичное сообщение. Метод наследуется от `RuntimeException`.

### `getDetails()`

Возвращает дополнительные сведения или `null`.

### `getOriginalCause()`

Возвращает исходную техническую причину.

### `getCause()`

Стандартный метод `Throwable`, который также возвращает исходную причину.

### `getChain()`

Возвращает объект `ExceptionChain`.

### `getChainElements()`

Возвращает неизменяемый список элементов.

### `isChainLimitReached()`

Проверяет достижение лимита цепочки.

### `addContext(ChainElement context)`

Добавляет новый уникальный уровень к той же логической ошибке.

### `toResponse(String currentService)`

Преобразует исключение в `ErrorResponse`.

---

# Полный пример локальной ошибки

```java
Instant timestamp = Instant.now();

ErrorDetails details = ErrorDetails.builder()
        .resource("COMPONENT")
        .build();

ChainElement repositoryContext =
        ChainElement.builder()
                .service("service-b")
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
                .service("service-b")
                .component("ComponentService")
                .operation("findComponent")
                .errorCode("COMPONENT_NOT_FOUND")
                .message("Компонент не найден")
                .timestamp(Instant.now())
                .status(404)
                .build()
);

ErrorResponse response =
        exception.toResponse("service-b");
```

---

# Пример межсервисного восстановления

```java
ErrorResponse remoteResponse =
        jsonMapper.readValue(
                remoteBody,
                ErrorResponse.class
        );

UnifiedErrorException exception =
        UnifiedErrorException.fromResponse(
                remoteResponse,
                httpClientException,
                10
        );

exception.addContext(
        ChainElement.builder()
                .service("service-a")
                .component("ComponentGateway")
                .operation("loadComponent")
                .errorCode("UPSTREAM_ERROR")
                .message("Не удалось получить компонент")
                .timestamp(Instant.now())
                .status(remoteResponse.getStatus())
                .build()
);

throw exception;
```

После такой обработки:

* `errorId` удалённого ответа сохраняется;
* timestamp исходной ошибки сохраняется;
* `details` сохраняется;
* исходная цепочка сохраняется;
* локальный контекст добавляется в конец;
* глобальный обработчик может вызвать `toResponse()`.

---

# Неизменяемость

`ErrorResponse`, `ChainElement`, `ErrorDetails` и `FieldViolation`:

* объявлены как `final`;
* содержат `private final` поля;
* не имеют setters;
* копируют входные коллекции;
* реализуют `equals()`, `hashCode()` и `toString()`.

`ExceptionChain` также является неизменяемым value-объектом.

`UnifiedErrorException` сохраняет неизменяемыми все основные структурированные поля. Изменяемой является только ссылка на текущую неизменяемую `ExceptionChain`, которая заменяется при успешном добавлении контекста.

---

# JSON-совместимость

Модели используют:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
```

Это позволяет новой версии читать дополнительные неизвестные поля.

Правила развития контракта:

1. Новые поля добавляются только как необязательные.
2. Тип существующего поля не изменяется.
3. Значение и назначение существующего поля не изменяются.
4. Новая версия должна принимать отсутствие нового необязательного поля.
5. Старый клиент должен игнорировать неизвестные поля.
6. Для одного значения используется одно каноническое JSON-имя.
7. `@JsonAlias` в первой версии контракта не используется.

Переименование опубликованного поля является несовместимым изменением и должно сопровождаться новой версией контракта.

---

# Тестирование

Запуск тестов:

```bash
./mvnw test
```

На Linux и macOS Maven Wrapper должен иметь право на выполнение:

```bash
chmod +x mvnw
```

Тесты моделей проверяют:

* точный формат timestamp;
* обязательность полей;
* диапазон HTTP-статусов;
* формат публичных кодов;
* запрет Java-типов исключений;
* неизменяемость списков;
* ограничение размера списков;
* value-семантику;
* чтение неизвестных JSON-полей.

`ExceptionChainTests` проверяет:

* правильный порядок элементов;
* сохранение существующих элементов;
* неизменяемость;
* ограничение размера;
* отсутствие дублей одного уровня.

`UnifiedErrorExceptionTests` проверяет:

* создание из обычного исключения;
* сохранение исходной причины;
* сохранение timestamp, кода и details;
* неизменность `errorId`;
* добавление контекста к тому же экземпляру;
* повторную обработку уже структурированного исключения;
* восстановление из ответа другого сервиса;
* сохранение удалённой цепочки;
* поведение при превышении локального лимита;
* отсутствие дублирующихся уровней;
* преобразование обратно в `ErrorResponse` без потери данных.

---

# Известные ограничения текущей версии

Текущая версия не реализует:

* автоматическую Spring Boot автоконфигурацию;
* глобальный обработчик REST-исключений;
* автоматическое логирование;
* AOP-аннотацию для добавления контекста;
* автоматический разбор HTTP-ошибок удалённых сервисов;
* поддержку `RestClient`, `WebClient` или `RestTemplate`;
* конфигурирование через `application.yaml`;
* признак того, что часть цепочки была отброшена.

Эти возможности могут быть добавлены в следующих задачах поверх существующих моделей, `ExceptionChain` и `UnifiedErrorException`.
