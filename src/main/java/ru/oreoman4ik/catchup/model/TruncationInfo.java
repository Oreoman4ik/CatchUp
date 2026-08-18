package ru.oreoman4ik.catchup.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public final class TruncationInfo {

    private final boolean chain;

    private final boolean message;

    private final boolean technicalDetails;

    private final boolean remoteBody;

    @JsonCreator
    public TruncationInfo(
            @JsonProperty("chain")
            boolean chain,

            @JsonProperty("message")
            boolean message,

            @JsonProperty("technicalDetails")
            boolean technicalDetails,

            @JsonProperty("remoteBody")
            boolean remoteBody
    ) {
        this.chain = chain;
        this.message = message;
        this.technicalDetails =
                technicalDetails;
        this.remoteBody = remoteBody;
    }

    public boolean isChain() {
        return chain;
    }

    public boolean isMessage() {
        return message;
    }

    public boolean isTechnicalDetails() {
        return technicalDetails;
    }

    public boolean isRemoteBody() {
        return remoteBody;
    }

    public boolean isAny() {
        return chain
                || message
                || technicalDetails
                || remoteBody;
    }

    public TruncationInfo merge(
            TruncationInfo other
    ) {
        if (other == null) {
            return this;
        }

        return new TruncationInfo(
                chain || other.chain,
                message || other.message,
                technicalDetails
                        || other.technicalDetails,
                remoteBody
                        || other.remoteBody
        );
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object
                instanceof TruncationInfo that)) {

            return false;
        }

        return chain == that.chain
                && message == that.message
                && technicalDetails
                == that.technicalDetails
                && remoteBody
                == that.remoteBody;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                chain,
                message,
                technicalDetails,
                remoteBody
        );
    }

    @Override
    public String toString() {
        return "TruncationInfo{"
                + "chain=" + chain
                + ", message=" + message
                + ", technicalDetails="
                + technicalDetails
                + ", remoteBody=" + remoteBody
                + '}';
    }
}