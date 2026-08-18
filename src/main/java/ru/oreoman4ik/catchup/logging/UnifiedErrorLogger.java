package ru.oreoman4ik.catchup.logging;

import ru.oreoman4ik.catchup.model.ChainElement;
import ru.oreoman4ik.catchup.model.UnifiedErrorException;

import java.util.stream.Collectors;

public final class UnifiedErrorLogger {

    private final ErrorLogSink sink;

    public UnifiedErrorLogger(
            ErrorLogSink sink
    ) {
        if (sink == null) {
            throw new IllegalArgumentException(
                    "sink is required"
            );
        }

        this.sink = sink;
    }

    public void log(
            UnifiedErrorException error,
            String service
    ) {
        if (error == null) {
            throw new IllegalArgumentException(
                    "error is required"
            );
        }

        /*
         * Один errorId логируется один раз.
         */
        if (!error.tryMarkLogged()) {
            return;
        }

        ChainElement last =
                error.getChainElements()
                        .isEmpty()
                        ? null
                        : error
                        .getChainElements()
                        .getLast();

        String operation =
                last == null
                        ? "unknown"
                        : last.getOperation();

        String chain =
                error.getChainElements()
                        .stream()
                        .map(
                                UnifiedErrorLogger
                                        ::formatElement
                        )
                        .collect(
                                Collectors.joining(
                                        " -> "
                                )
                        );

        String message =
                "catchup_error"
                        + " errorId="
                        + error.getErrorId()
                        + " service="
                        + service
                        + " operation="
                        + operation
                        + " status="
                        + error.getStatus()
                        + " errorCode="
                        + error.getErrorCode()
                        + " chain=["
                        + chain
                        + "]";

        ErrorLogLevel level =
                error.getStatus() >= 500
                        ? ErrorLogLevel.ERROR
                        : ErrorLogLevel.WARN;

        /*
         * Именно originalCause передаётся logger-у:
         * backend запишет полный stack trace.
         */
        sink.write(
                level,
                message,
                error.getOriginalCause()
        );
    }

    private static String formatElement(
            ChainElement element
    ) {
        return element.getService()
                + "/"
                + element.getComponent()
                + "#"
                + element.getOperation()
                + "("
                + element.getStatus()
                + ","
                + element.getErrorCode()
                + ")";
    }
}