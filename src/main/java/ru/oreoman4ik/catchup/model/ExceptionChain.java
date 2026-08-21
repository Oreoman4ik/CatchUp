package ru.oreoman4ik.catchup.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Неизменяемая цепочка прохождения ошибки.
 */
public final class ExceptionChain {

    private static final int ABSOLUTE_MAX_SIZE = 100;

    private final List<ChainElement> elements;

    private final int maxSize;

    /**
     * true означает, что хотя бы один уникальный
     * элемент цепочки был отброшен из-за лимита.
     */
    private final boolean truncated;

    private ExceptionChain(
            List<ChainElement> elements,
            int maxSize,
            boolean truncated
    ) {
        this.maxSize =
                validateMaxSize(maxSize);

        ErrorModelValidation.required(
                "elements",
                elements
        );

        this.elements =
                ErrorModelValidation
                        .immutableOptionalList(
                                "elements",
                                elements,
                                this.maxSize
                        );

        this.truncated = truncated;
    }

    public static ExceptionChain empty(
            int maxSize
    ) {
        return new ExceptionChain(
                List.of(),
                maxSize,
                false
        );
    }

    /**
     * Восстанавливает цепочку.
     *
     * <p>Если source больше maxSize,
     * сохраняются первые maxSize элементов.</p>
     */
    public static ExceptionChain of(
            List<ChainElement> elements,
            int maxSize
    ) {
        ErrorModelValidation.required(
                "elements",
                elements
        );

        int validatedMaxSize =
                validateMaxSize(maxSize);

        int retained =
                Math.min(
                        elements.size(),
                        validatedMaxSize
                );

        List<ChainElement> retainedElements =
                List.copyOf(
                        elements.subList(
                                0,
                                retained
                        )
                );

        return new ExceptionChain(
                retainedElements,
                validatedMaxSize,
                elements.size()
                        > validatedMaxSize
        );
    }

    /**
     * Внутренняя фабрика для восстановления цепочки,
     * когда вызывающий код уже знает факт truncation.
     */
    static ExceptionChain restored(
            List<ChainElement> elements,
            int maxSize,
            boolean truncated
    ) {
        return new ExceptionChain(
                elements,
                maxSize,
                truncated
        );
    }

    public ExceptionChain add(
            ChainElement element
    ) {
        ErrorModelValidation.required(
                "element",
                element
        );

        /*
         * Повтор одного уровня не является
         * потерей информации.
         */
        if (containsLevel(element)) {
            return this;
        }

        /*
         * Уникальный новый уровень не помещается.
         */
        if (isLimitReached()) {
            if (truncated) {
                return this;
            }

            return new ExceptionChain(
                    elements,
                    maxSize,
                    true
            );
        }

        List<ChainElement> updated =
                new ArrayList<>(
                        elements.size() + 1
                );

        updated.addAll(elements);
        updated.add(element);

        return new ExceptionChain(
                updated,
                maxSize,
                truncated
        );
    }

    public List<ChainElement> getElements() {
        return elements;
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public int getMaxSize() {
        return maxSize;
    }

    public boolean isLimitReached() {
        return elements.size() >= maxSize;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public boolean containsLevel(
            ChainElement candidate
    ) {
        ErrorModelValidation.required(
                "candidate",
                candidate
        );

        return elements.stream()
                .anyMatch(
                        existing ->
                                sameLevel(
                                        existing,
                                        candidate
                                )
                );
    }

    private static boolean sameLevel(
            ChainElement first,
            ChainElement second
    ) {
        return first.getService().equals(
                second.getService()
        )
                && first.getComponent().equals(
                second.getComponent()
        )
                && first.getOperation().equals(
                second.getOperation()
        );
    }

    private static int validateMaxSize(
            int maxSize
    ) {
        if (maxSize < 1
                || maxSize > ABSOLUTE_MAX_SIZE) {

            throw new IllegalArgumentException(
                    "maxSize must be from 1 to "
                            + ABSOLUTE_MAX_SIZE
            );
        }

        return maxSize;
    }

    @Override
    public boolean equals(
            Object object
    ) {
        if (this == object) {
            return true;
        }

        if (!(object
                instanceof ExceptionChain that)) {

            return false;
        }

        return maxSize == that.maxSize
                && truncated == that.truncated
                && elements.equals(
                that.elements
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                elements,
                maxSize,
                truncated
        );
    }

    @Override
    public String toString() {
        return "ExceptionChain{"
                + "elements="
                + elements
                + ", maxSize="
                + maxSize
                + ", truncated="
                + truncated
                + '}';
    }
}