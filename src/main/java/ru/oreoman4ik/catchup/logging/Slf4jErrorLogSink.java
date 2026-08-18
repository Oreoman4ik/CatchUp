package ru.oreoman4ik.catchup.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Slf4jErrorLogSink
        implements ErrorLogSink {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    "ru.oreoman4ik.catchup.errors"
            );

    @Override
    public void write(
            ErrorLogLevel level,
            String message,
            Throwable throwable
    ) {
        switch (level) {
            case ERROR ->
                    LOGGER.error(
                            message,
                            throwable
                    );

            case WARN ->
                    LOGGER.warn(
                            message,
                            throwable
                    );
        }
    }
}