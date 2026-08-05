package ru.oreoman4ik.catchup.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Неизменяемая цепочка прохождения ошибки через сервисы,
 * компоненты и операции.
 *
 * <p>Новые элементы добавляются в конец списка, поэтому порядок
 * соответствует направлению от места возникновения ошибки
 * к текущему уровню обработки.</p>
 */
public final class ExceptionChain {

    private static final int ABSOLUTE_MAX_SIZE = 100;

    private final List<ChainElement> elements;
    private final int maxSize;

    private ExceptionChain(
            List<ChainElement> elements,
            int maxSize
    ) {
        this.maxSize = validateMaxSize(maxSize);

        ErrorModelValidation.required(
                "elements",
                elements
        );

        this.elements =
                ErrorModelValidation.immutableOptionalList(
                        "elements",
                        elements,
                        this.maxSize
                );
    }

    /**
     * Создаёт пустую цепочку с указанным максимальным размером.
     */
    public static ExceptionChain empty(int maxSize) {
        return new ExceptionChain(
                List.of(),
                maxSize
        );
    }

    /**
     * Восстанавливает цепочку из существующих элементов.
     *
     * <p>Переданный список копируется. Его последующее изменение
     * не повлияет на созданную цепочку.</p>
     */
    public static ExceptionChain of(
            List<ChainElement> elements,
            int maxSize
    ) {
        return new ExceptionChain(
                elements,
                maxSize
        );
    }

    /**
     * Добавляет новый элемент в конец цепочки.
     *
     * <p>Если тот же сервис, компонент и операция уже присутствуют,
     * элемент считается повторной обработкой одного уровня и не
     * добавляется.</p>
     *
     * <p>При достижении лимита цепочка остаётся неизменной.</p>
     *
     * @return новая цепочка либо текущий объект, если добавление
     * не требуется
     */
    public ExceptionChain add(ChainElement element) {
        ErrorModelValidation.required(
                "element",
                element
        );

        if (isLimitReached() || containsLevel(element)) {
            return this;
        }

        List<ChainElement> updated =
                new ArrayList<>(elements.size() + 1);

        updated.addAll(elements);
        updated.add(element);

        return new ExceptionChain(
                updated,
                maxSize
        );
    }

    /**
     * Возвращает неизменяемые элементы в порядке добавления.
     */
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

    /**
     * @return {@code true}, если достигнут максимальный размер
     */
    public boolean isLimitReached() {
        return elements.size() >= maxSize;
    }

    /**
     * Проверяет, присутствует ли в цепочке указанный уровень.
     *
     * <p>Уровень определяется сочетанием:</p>
     *
     * <ul>
     *     <li>service;</li>
     *     <li>component;</li>
     *     <li>operation.</li>
     * </ul>
     */
    public boolean containsLevel(
            ChainElement candidate
    ) {
        ErrorModelValidation.required(
                "candidate",
                candidate
        );

        return elements.stream()
                .anyMatch(existing -> sameLevel(
                        existing,
                        candidate
                ));
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

    private static int validateMaxSize(int maxSize) {
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
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ExceptionChain that)) {
            return false;
        }

        return maxSize == that.maxSize
                && elements.equals(that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                elements,
                maxSize
        );
    }

    @Override
    public String toString() {
        return "ExceptionChain{"
                + "elements=" + elements
                + ", maxSize=" + maxSize
                + '}';
    }
}