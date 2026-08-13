package ru.oreoman4ik.catchup.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Добавляет публичный контекст операции в цепочку ошибки,
 * если выполнение метода завершилось исключением.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ErrorContext {

    /**
     * Название сервиса.
     *
     * <p>Если не задано, используется spring.application.name.</p>
     */
    String service() default "";

    /**
     * Публичное название операции.
     *
     * <p>Если не задано, используется имя Java-метода.</p>
     */
    String operation() default "";

    /**
     * Необязательное безопасное публичное сообщение.
     *
     * <p>Техническое сообщение исходного исключения
     * не используется.</p>
     */
    String message() default "";
}