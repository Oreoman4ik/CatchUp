package ru.oreoman4ik.catchup.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExceptionChainTests {

    private static final Instant ERROR_TIME = Instant.parse(
            "2026-07-31T10:20:30.123Z"
    );

    @Test
    void addsElementsInCorrectOrderAndPreservesExistingOnes() {
        ChainElement repository = element(
                "service-b",
                "ComponentRepository",
                "findById",
                "COMPONENT_NOT_FOUND"
        );

        ChainElement service = element(
                "service-b",
                "ComponentService",
                "findComponent",
                "COMPONENT_NOT_FOUND"
        );

        ChainElement controller = element(
                "service-a",
                "ComponentController",
                "getComponent",
                "UPSTREAM_ERROR"
        );

        ExceptionChain original = ExceptionChain.of(
                List.of(repository),
                5
        );

        ExceptionChain updated = original
                .add(service)
                .add(controller);

        assertThat(original.getElements())
                .containsExactly(repository);

        assertThat(updated.getElements())
                .containsExactly(
                        repository,
                        service,
                        controller
                );
    }

    @Test
    void doesNotGrowAfterReachingLimit() {
        ChainElement first = element(
                "service-b",
                "Repository",
                "load",
                "RESOURCE_NOT_FOUND"
        );

        ChainElement second = element(
                "service-b",
                "Service",
                "find",
                "RESOURCE_NOT_FOUND"
        );

        ChainElement third = element(
                "service-a",
                "Controller",
                "get",
                "UPSTREAM_ERROR"
        );

        ExceptionChain fullChain =
                ExceptionChain.empty(2)
                        .add(first)
                        .add(second);

        ExceptionChain afterExtraElement =
                fullChain.add(third);

        assertThat(fullChain.isLimitReached())
                .isTrue();

        assertThat(afterExtraElement)
                .isSameAs(fullChain);

        assertThat(afterExtraElement.getElements())
                .containsExactly(first, second);
    }

    @Test
    void doesNotAddDuplicateProcessingLevel() {
        ChainElement firstHandling = element(
                "service-a",
                "ComponentService",
                "findComponent",
                "COMPONENT_NOT_FOUND"
        );

        ChainElement repeatedHandling =
                ChainElement.builder()
                        .service("service-a")
                        .component("ComponentService")
                        .operation("findComponent")
                        .causeCode("UPSTREAM_ERROR")
                        .publicMessage(
                                "Повторная обработка"
                        )
                        .timestamp(
                                ERROR_TIME.plusSeconds(1)
                        )
                        .httpStatus(500)
                        .build();

        ExceptionChain original =
                ExceptionChain.empty(5)
                        .add(firstHandling);

        ExceptionChain updated =
                original.add(repeatedHandling);

        assertThat(updated).isSameAs(original);
        assertThat(updated.size()).isEqualTo(1);
        assertThat(updated.getElements())
                .containsExactly(firstHandling);
    }

    @Test
    void snapshotsSourceListAndReturnsUnmodifiableElements() {
        List<ChainElement> source =
                new ArrayList<>();

        source.add(
                element(
                        "service-b",
                        "Repository",
                        "load",
                        "RESOURCE_NOT_FOUND"
                )
        );

        ExceptionChain chain =
                ExceptionChain.of(source, 5);

        source.clear();

        assertThat(chain.getElements())
                .hasSize(1);

        assertThatThrownBy(
                () -> chain.getElements().clear()
        ).isInstanceOf(
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
                .hasMessageContaining("1 to 100");

        assertThatThrownBy(
                () -> ExceptionChain.empty(101)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining("1 to 100");
    }

    private static ChainElement element(
            String service,
            String component,
            String operation,
            String code
    ) {
        return ChainElement.builder()
                .service(service)
                .component(component)
                .operation(operation)
                .causeCode(code)
                .publicMessage("Безопасное сообщение")
                .timestamp(ERROR_TIME)
                .httpStatus(404)
                .build();
    }
}