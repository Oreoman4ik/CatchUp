package ru.oreoman4ik.catchup.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrorDataLimiterTests {

    @Test
    void shortPublicMessageIsUnchanged() {
        ErrorDataLimiter.LimitedText result =
                ErrorDataLimiter.publicMessage(
                        "Безопасное сообщение"
                );

        assertThat(result.value())
                .isEqualTo(
                        "Безопасное сообщение"
                );

        assertThat(result.truncated())
                .isFalse();
    }

    @Test
    void longPublicMessageIsLimitedTo500Characters() {
        ErrorDataLimiter.LimitedText result =
                ErrorDataLimiter.publicMessage(
                        "x".repeat(1000)
                );

        assertThat(result.value())
                .hasSize(500);

        assertThat(result.truncated())
                .isTrue();
    }

    @Test
    void publicMessageRejectsControlCharacters() {
        assertThatThrownBy(
                () ->
                        ErrorDataLimiter
                                .publicMessage(
                                        "secret\nmessage"
                                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "control characters"
                );
    }

    @Test
    void publicMessageRejectsBlankValue() {
        assertThatThrownBy(
                () ->
                        ErrorDataLimiter
                                .publicMessage("   ")
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }

    @Test
    void technicalTextNormalizesControlCharacters() {
        ErrorDataLimiter.LimitedText result =
                ErrorDataLimiter.technicalText(
                        "line1\nline2\tline3",
                        100
                );

        assertThat(result.value())
                .isEqualTo(
                        "line1 line2 line3"
                );

        assertThat(result.truncated())
                .isFalse();
    }

    @Test
    void technicalTextIsLimited() {
        ErrorDataLimiter.LimitedText result =
                ErrorDataLimiter.technicalText(
                        "x".repeat(500),
                        128
                );

        assertThat(result.value())
                .hasSize(128);

        assertThat(result.truncated())
                .isTrue();
    }
}