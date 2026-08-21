package ru.oreoman4ik.catchup.logging;

public interface ErrorLogSink {

    void write(
            ErrorLogLevel level,
            String message,
            Throwable throwable
    );
}