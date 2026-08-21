package ru.oreoman4ik.catchup.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("catchup.errors")
public final class UnifiedErrorProperties
        implements InitializingBean {

    private static final int MAX_CHAIN_SIZE = 100;

    private static final int MAX_SERVICE_NAME_LENGTH = 120;

    private static final int MAX_MESSAGE_LENGTH = 500;

    private static final int MIN_REMOTE_BODY_BYTES = 1024;

    private static final int MAX_REMOTE_BODY_BYTES =
            8 * 1024 * 1024;

    private static final int MAX_STACK_TRACE_LINES = 100;

    private static final int MIN_TECHNICAL_TEXT_LENGTH = 128;

    private static final int MAX_TECHNICAL_TEXT_LENGTH = 1000;

    private boolean enabled = true;

    private String serviceName;

    private int maxChainSize = 10;

    private boolean includeTechnicalDetails = false;

    private boolean includeStackTrace = false;

    private String unknownErrorMessage =
            "Внутренняя ошибка сервиса";

    private int maxRemoteBodyBytes =
            2 * 1024 * 1024;

    private int maxStackTraceLines = 100;

    private int maxTechnicalTextLength = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(
            boolean enabled
    ) {
        this.enabled = enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(
            String serviceName
    ) {
        if (serviceName == null
                || serviceName.isBlank()) {

            this.serviceName = null;
            return;
        }

        this.serviceName =
                validateText(
                        "catchup.errors.service-name",
                        serviceName,
                        MAX_SERVICE_NAME_LENGTH
                );
    }

    public int getMaxChainSize() {
        return maxChainSize;
    }

    public void setMaxChainSize(
            int maxChainSize
    ) {
        if (maxChainSize < 1
                || maxChainSize > MAX_CHAIN_SIZE) {

            throw new IllegalArgumentException(
                    "catchup.errors.max-chain-size "
                            + "must be from 1 to "
                            + MAX_CHAIN_SIZE
            );
        }

        this.maxChainSize = maxChainSize;
    }

    public boolean isIncludeTechnicalDetails() {
        return includeTechnicalDetails;
    }

    public void setIncludeTechnicalDetails(
            boolean includeTechnicalDetails
    ) {
        this.includeTechnicalDetails =
                includeTechnicalDetails;
    }

    public boolean isIncludeStackTrace() {
        return includeStackTrace;
    }

    public void setIncludeStackTrace(
            boolean includeStackTrace
    ) {
        this.includeStackTrace =
                includeStackTrace;
    }

    public String getUnknownErrorMessage() {
        return unknownErrorMessage;
    }

    public void setUnknownErrorMessage(
            String unknownErrorMessage
    ) {
        this.unknownErrorMessage =
                validateText(
                        "catchup.errors."
                                + "unknown-error-message",
                        unknownErrorMessage,
                        MAX_MESSAGE_LENGTH
                );
    }

    public int getMaxRemoteBodyBytes() {
        return maxRemoteBodyBytes;
    }

    public void setMaxRemoteBodyBytes(
            int maxRemoteBodyBytes
    ) {
        if (maxRemoteBodyBytes
                < MIN_REMOTE_BODY_BYTES
                || maxRemoteBodyBytes
                > MAX_REMOTE_BODY_BYTES) {

            throw new IllegalArgumentException(
                    "catchup.errors."
                            + "max-remote-body-bytes "
                            + "must be from "
                            + MIN_REMOTE_BODY_BYTES
                            + " to "
                            + MAX_REMOTE_BODY_BYTES
            );
        }

        this.maxRemoteBodyBytes =
                maxRemoteBodyBytes;
    }

    public int getMaxStackTraceLines() {
        return maxStackTraceLines;
    }

    public void setMaxStackTraceLines(
            int maxStackTraceLines
    ) {
        if (maxStackTraceLines < 1
                || maxStackTraceLines
                > MAX_STACK_TRACE_LINES) {

            throw new IllegalArgumentException(
                    "catchup.errors."
                            + "max-stack-trace-lines "
                            + "must be from 1 to "
                            + MAX_STACK_TRACE_LINES
            );
        }

        this.maxStackTraceLines =
                maxStackTraceLines;
    }

    public int getMaxTechnicalTextLength() {
        return maxTechnicalTextLength;
    }

    public void setMaxTechnicalTextLength(
            int maxTechnicalTextLength
    ) {
        if (maxTechnicalTextLength
                < MIN_TECHNICAL_TEXT_LENGTH
                || maxTechnicalTextLength
                > MAX_TECHNICAL_TEXT_LENGTH) {

            throw new IllegalArgumentException(
                    "catchup.errors."
                            + "max-technical-text-length "
                            + "must be from "
                            + MIN_TECHNICAL_TEXT_LENGTH
                            + " to "
                            + MAX_TECHNICAL_TEXT_LENGTH
            );
        }

        this.maxTechnicalTextLength =
                maxTechnicalTextLength;
    }

    @Override
    public void afterPropertiesSet() {
        if (includeStackTrace
                && !includeTechnicalDetails) {

            throw new IllegalArgumentException(
                    "catchup.errors."
                            + "include-stack-trace=true "
                            + "requires "
                            + "catchup.errors."
                            + "include-technical-details=true"
            );
        }
    }

    private static String validateText(
            String property,
            String value,
            int maxLength
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    property + " is required"
            );
        }

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    property + " must not be blank"
            );
        }

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    property
                            + " must not exceed "
                            + maxLength
                            + " characters"
            );
        }

        if (normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\t') >= 0) {

            throw new IllegalArgumentException(
                    property
                            + " must not contain "
                            + "control characters"
            );
        }

        return normalized;
    }
}