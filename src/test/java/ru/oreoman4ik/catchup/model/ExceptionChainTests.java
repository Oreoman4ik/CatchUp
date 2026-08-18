package ru.oreoman4ik.catchup.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExceptionChainTests {

    private static final Instant ERROR_TIME =
            Instant.parse(
                    "2026-07-31T10:20:30.123Z"
            );

    @Test
    void addsElementsInCorrectOrder() {
        ChainElement repository =
                element(
                        "service-c",
                        "Repository",
                        "load"
                );

        ChainElement service =
                element(
                        "service-b",
                        "Service",
                        "process"
                );

        ChainElement controller =
                element(
                        "service-a",
                        "Controller",
                        "get"
                );

        ExceptionChain original =
                ExceptionChain.of(
                        List.of(repository),
                        5
                );

        ExceptionChain updated =
                original
                        .add(service)
                        .add(controller);

        assertThat(
                original.getElements()
        ).containsExactly(
                repository
        );

        assertThat(
                updated.getElements()
        ).containsExactly(
                repository,
                service,
                controller
        );

        assertThat(updated.isTruncated())
                .isFalse();
    }

    @Test
    void reachingLimitAloneDoesNotMeanTruncation() {
        ExceptionChain chain =
                ExceptionChain.empty(2)
                        .add(
                                element(
                                        "service-a",
                                        "A",
                                        "first"
                                )
                        )
                        .add(
                                element(
                                        "service-b",
                                        "B",
                                        "second"
                                )
                        );

        assertThat(chain.size())
                .isEqualTo(2);

        assertThat(chain.isLimitReached())
                .isTrue();

        /*
         * Пока ничего не пытались отбросить,
         * данные не считаются сокращёнными.
         */
        assertThat(chain.isTruncated())
                .isFalse();
    }

    @Test
    void rejectedUniqueElementMarksChainAsTruncated() {
        ChainElement first =
                element(
                        "service-a",
                        "A",
                        "first"
                );

        ChainElement second =
                element(
                        "service-b",
                        "B",
                        "second"
                );

        ExceptionChain chain =
                ExceptionChain.empty(1)
                        .add(first);

        ExceptionChain truncated =
                chain.add(second);

        assertThat(truncated.size())
                .isEqualTo(1);

        assertThat(
                truncated.getElements()
        ).containsExactly(
                first
        );

        assertThat(truncated.isTruncated())
                .isTrue();

        assertThat(truncated.isLimitReached())
                .isTrue();
    }

    @Test
    void duplicateLevelDoesNotMarkChainAsTruncated() {
        ChainElement first =
                element(
                        "service-a",
                        "Service",
                        "load"
                );

        ChainElement duplicate =
                ChainElement.builder()
                        .service("service-a")
                        .component("Service")
                        .operation("load")
                        .errorCode(
                                "OTHER_ERROR"
                        )
                        .message(
                                "Другое сообщение"
                        )
                        .timestamp(
                                ERROR_TIME.plusSeconds(1)
                        )
                        .status(500)
                        .build();

        ExceptionChain original =
                ExceptionChain.empty(1)
                        .add(first);

        ExceptionChain updated =
                original.add(duplicate);

        assertThat(updated)
                .isSameAs(original);

        assertThat(updated.isTruncated())
                .isFalse();

        assertThat(updated.getElements())
                .containsExactly(first);
    }

    @Test
    void ofTruncatesOversizedSource() {
        ChainElement first =
                element(
                        "service-a",
                        "A",
                        "first"
                );

        ChainElement second =
                element(
                        "service-b",
                        "B",
                        "second"
                );

        ChainElement third =
                element(
                        "service-c",
                        "C",
                        "third"
                );

        ExceptionChain chain =
                ExceptionChain.of(
                        List.of(
                                first,
                                second,
                                third
                        ),
                        2
                );

        assertThat(chain.size())
                .isEqualTo(2);

        assertThat(chain.getElements())
                .containsExactly(
                        first,
                        second
                );

        assertThat(chain.isTruncated())
                .isTrue();

        assertThat(chain.getMaxSize())
                .isEqualTo(2);
    }

    @Test
    void repeatedCycleCannotGrowChain() {
        ChainElement first =
                element(
                        "service-a",
                        "ServiceA",
                        "callB"
                );

        ChainElement second =
                element(
                        "service-b",
                        "ServiceB",
                        "callA"
                );

        ExceptionChain chain =
                ExceptionChain.empty(10)
                        .add(first)
                        .add(second);

        for (int index = 0;
             index < 100;
             index++) {

            chain = chain
                    .add(first)
                    .add(second);
        }

        assertThat(chain.size())
                .isEqualTo(2);

        assertThat(chain.getElements())
                .containsExactly(
                        first,
                        second
                );

        assertThat(chain.isTruncated())
                .isFalse();
    }

    @Test
    void snapshotsSourceList() {
        List<ChainElement> source =
                new ArrayList<>();

        source.add(
                element(
                        "service-a",
                        "Repository",
                        "load"
                )
        );

        ExceptionChain chain =
                ExceptionChain.of(
                        source,
                        5
                );

        source.clear();

        assertThat(chain.getElements())
                .hasSize(1);

        assertThatThrownBy(
                () ->
                        chain.getElements()
                                .clear()
        )
                .isInstanceOf(
                        UnsupportedOperationException.class
                );
    }

    @Test
    void rejectsInvalidMaximumSize() {
        assertThatThrownBy(
                () -> ExceptionChain.empty(0)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "1 to 100"
                );

        assertThatThrownBy(
                () -> ExceptionChain.empty(101)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "1 to 100"
                );
    }

    private static ChainElement element(
            String service,
            String component,
            String operation
    ) {
        return ChainElement.builder()
                .service(service)
                .component(component)
                .operation(operation)
                .errorCode(
                        "INTERNAL_ERROR"
                )
                .message(
                        "Безопасное сообщение"
                )
                .timestamp(ERROR_TIME)
                .status(500)
                .build();
    }
}