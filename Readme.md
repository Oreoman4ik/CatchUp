# Unified Error Model

Небольшая библиотека с неизменяемыми DTO для публичного JSON-ответа об ошибке.

Текущая версия содержит только модели, их валидацию и JSON-тесты. Глобальный обработчик исключений, Spring Boot starter и межсервисная обработка в этот модуль пока не входят.

Минимальная версия Java — **21**. Приложения, подключающие библиотеку, также должны использовать Java 21 или новее.

## Maven-координаты

```xml
<dependency>
    <groupId>ru.oreoman4ik</groupId>
    <artifactId>unified-error-model</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Проект собирается как обычный JAR. `spring-boot-maven-plugin` не используется, поэтому библиотека не переупаковывается в исполняемое Spring Boot-приложение.

Основная compile-зависимость — `tools.jackson.core:jackson-databind`. Полный Spring MVC starter и встроенный сервер библиотеке моделей не нужны.

## Публичный формат ошибки

```json
{
  "errorId": "7c12c42e-86ee-43b0-8324-9a56bf633ed4",
  "timestamp": "2026-07-30T10:42:15.018Z",
  "status": 404,
  "message": "Компонент не найден",
  "errorType": "COMPONENT_NOT_FOUND",
  "currentService": "service-a",
  "chain": [
    {
      "service": "service-b",
      "component": "ComponentCatalog",
      "operation": "findComponentById",
      "exceptionType": "COMPONENT_NOT_FOUND",
      "message": "Компонент не найден",
      "timestamp": "2026-07-30T10:42:15.018Z",
      "status": 404
    }
  ],
  "details": {
    "resource": "COMPONENT"
  }
}
```

Полный пример находится в:

```text
src/main/resources/json/error-response-example.json
```

## ErrorResponse

| JSON-поле        | Java-тип             | Обязательность | Назначение                                     |
| ---------------- | -------------------- | -------------- | ---------------------------------------------- |
| `errorId`        | `UUID`               | обязательно    | Идентификатор ошибки для поиска записи в логах |
| `timestamp`      | `Instant`            | обязательно    | Время возникновения исходной ошибки            |
| `status`         | `int`                | обязательно    | Итоговый HTTP-статус ошибки от 400 до 599      |
| `message`        | `String`             | обязательно    | Контролируемое сообщение для клиента           |
| `errorType`      | `String`             | обязательно    | Стабильный публичный код ошибки                |
| `currentService` | `String`             | обязательно    | Сервис, сформировавший текущий ответ           |
| `chain`          | `List<ChainElement>` | обязательно    | Непустая цепочка ошибки                        |
| `details`        | `ErrorDetails`       | необязательно  | Типизированные дополнительные сведения         |

`details == null` допустим, когда безопасных дополнительных сведений нет. В таком случае поле не включается в JSON.

Цепочка содержит не более 100 элементов. Элементы располагаются от места возникновения ошибки к текущему сервису.

## ChainElement

| JSON-поле       | Java-тип  | Обязательность | Назначение                         |
| --------------- | --------- | -------------- | ---------------------------------- |
| `service`       | `String`  | обязательно    | Сервис, добавивший элемент         |
| `component`     | `String`  | обязательно    | Публичное имя компонента           |
| `operation`     | `String`  | обязательно    | Публичное имя операции             |
| `exceptionType` | `String`  | обязательно    | Стабильный публичный код причины   |
| `message`       | `String`  | обязательно    | Контролируемое публичное сообщение |
| `timestamp`     | `Instant` | обязательно    | Время добавления элемента          |
| `status`        | `Integer` | необязательно  | HTTP-статус ошибки от 400 до 599   |

`ChainElement.status == null` допустим, если на конкретном промежуточном шаге HTTP-статус ещё не был определён.

## Формат timestamp

Оба timestamp всегда сериализуются в UTC по точному шаблону:

```text
yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
```

Пример:

```text
2026-07-30T10:42:15.018Z
```

Формат закреплён через `@JsonFormat` непосредственно в моделях, поэтому не зависит от глобальной настройки `JsonMapper`, включающей числовые timestamp.

Верхнеуровневый `timestamp` означает время возникновения исходной ошибки.

Timestamp внутри `ChainElement` означает время добавления конкретного элемента в цепочку.

## HTTP-статусы

Модели описывают только ошибки. Допустимы значения:

```text
400–599
```

Статусы 1xx, 2xx и 3xx отклоняются. Создать `ErrorResponse` со статусом `200`, `204` или `302` нельзя.

## Публичные коды

`errorType`, `exceptionType`, `resource` и `reasonCode` должны соответствовать формату:

```text
[A-Z][A-Z0-9_]{1,63}
```

Допустимые примеры:

```text
COMPONENT_NOT_FOUND
VALIDATION_ERROR
UPSTREAM_ERROR
REQUIRED
```

Недопустимые примеры:

```text
NullPointerException
DataIntegrityViolationException
NULLPOINTEREXCEPTION
DATA_INTEGRITY_VIOLATION_EXCEPTION
org.springframework.dao.DataIntegrityViolationException
```

Валидатор не переводит произвольную строку в верхний регистр. Разработчик обязан передать уже определённый публичный код.

Значения, заканчивающиеся на `EXCEPTION`, отклоняются как технические Java-типы.

Несмотря на исторические JSON-имена `errorType` и `exceptionType`, их смысл — публичный код, а не имя исключения.

## Безопасность message

Модель проверяет только структурные свойства сообщения:

* значение обязательно;
* строка не может быть пустой;
* максимальная длина — 500 символов;
* запрещены `\r`, `\n` и `\t`.

Модель **не пытается очищать** SQL, URL, токены или stack trace с помощью blacklist-регулярных выражений. Такие проверки неполны и не гарантируют безопасность.

Сообщение должно поступать только из контролируемого каталога приложения или из явно подготовленного безопасного текста.

Нельзя:

```java
.publicMessage(exception.getMessage())
```

Правильно:

```java
.publicMessage("Компонент не найден")
```

Техническое исключение, его сообщение и stack trace должны записываться в серверный лог вместе с `errorId`.

## ErrorDetails

`ErrorDetails` заменяет произвольный `Map<String, Object>` и допускает только:

| JSON-поле           | Тип                    | Назначение                              |
| ------------------- | ---------------------- | --------------------------------------- |
| `resource`          | `String`               | Публичный код ресурса                   |
| `violations`        | `List<FieldViolation>` | Ошибки валидации                        |
| `retryAfterSeconds` | `Long`                 | Неотрицательное число секунд до повтора |

Пустой `ErrorDetails` недопустим. Если дополнительных данных нет, поле `details` нужно оставить равным `null`.

Список `violations` содержит не более 100 элементов.

## FieldViolation

`FieldViolation` можно создать из прикладного Java-кода через публичную фабрику:

```java
ErrorDetails.FieldViolation violation =
        ErrorDetails.FieldViolation.of(
                "name",
                "REQUIRED",
                "Название обязательно"
        );
```

Объект содержит только:

* публичное имя поля;
* публичный код причины;
* безопасное сообщение.

Отклонённое значение поля намеренно отсутствует, поскольку оно может содержать пароль, токен, персональные или другие чувствительные данные.

## Неизменяемость

Все модели:

* объявлены `final`;
* содержат только `private final` поля;
* не имеют setters;
* копируют входные списки через `List.copyOf`;
* не допускают `null` внутри списков;
* реализуют `equals()`, `hashCode()` и `toString()`.

Изменение исходной коллекции после `build()` не меняет созданный объект:

```java
List<ChainElement> source = new ArrayList<>();
source.add(element);

ErrorResponse response = ErrorResponse.builder()
        .errorId(UUID.randomUUID())
        .timestamp(Instant.now())
        .httpStatus(404)
        .publicMessage("Компонент не найден")
        .errorCode("COMPONENT_NOT_FOUND")
        .currentService("service-a")
        .chain(source)
        .build();

source.clear();

// response.getChain() по-прежнему содержит element.
```

## Обязательные поля и null

Для `ErrorResponse` обязательны:

* `errorId`;
* `timestamp`;
* `status`;
* `message`;
* `errorType`;
* `currentService`;
* непустая `chain`.

Необязателен только `details`.

Для `ChainElement` обязательны:

* `service`;
* `component`;
* `operation`;
* `exceptionType`;
* `message`;
* `timestamp`.

Необязателен только `status`, потому что он может быть неизвестен на промежуточном шаге.

В `ErrorDetails` все отдельные поля необязательны, но должен присутствовать хотя бы один из них.

## JSON-совместимость

Модели используют:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
```

Новая версия может читать JSON с дополнительными неизвестными полями.

Новые поля контракта следует добавлять только как необязательные. Нельзя менять тип или смысл уже опубликованного поля.

Ранее использовавшиеся имена поддерживаются только при чтении через `@JsonAlias`:

* `error_id`;
* `httpStatus`;
* `publicMessage`;
* `type_exception`;
* `errorCode`;
* `current_service`;
* `service`;
* `exception_type`;
* `causeCode`;
* `fieldErrors`;
* `retry_after_seconds`;
* `code`.

При сериализации всегда используются канонические имена из таблиц выше.

Старый клиент сможет обработать новые необязательные поля только при условии, что он сам игнорирует неизвестные JSON-поля.

## Создание ответа

```java
ChainElement chainElement = ChainElement.builder()
        .service("service-b")
        .component("ComponentCatalog")
        .operation("findComponentById")
        .causeCode("COMPONENT_NOT_FOUND")
        .publicMessage("Компонент не найден")
        .timestamp(Instant.now())
        .httpStatus(404)
        .build();

ErrorResponse response = ErrorResponse.builder()
        .errorId(UUID.randomUUID())
        .timestamp(Instant.now())
        .httpStatus(404)
        .publicMessage("Компонент не найден")
        .errorCode("COMPONENT_NOT_FOUND")
        .currentService("service-a")
        .chain(List.of(chainElement))
        .details(
                ErrorDetails.builder()
                        .resource("COMPONENT")
                        .build()
        )
        .build();
```

## Тестирование

Запуск:

```bash
./mvnw test
```

Тесты проверяют:

* точный формат timestamp;
* независимость timestamp от числовой настройки `JsonMapper`;
* обязательные поля;
* только ошибочные HTTP-статусы;
* запрет Java-типов исключений в публичных кодах;
* создание `FieldViolation` из Java-кода;
* фактическую неизменяемость списков;
* ограничение размера коллекций;
* чтение неизвестных полей и прежних aliases;
* value-семантику после JSON round-trip.
