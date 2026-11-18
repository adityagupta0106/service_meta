package com.serviceplus.metadata.enums;

public enum MoveToProdStatus {

    DEFAULT(""),
    MOVED_TO_PRODUCTION("MP"),
    MODIFIED_AFTER_PRODUCTION("NP"),
    STOPPED_IN_PRODUCTION("SP");

    private final String value;

    MoveToProdStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MoveToProdStatus fromValue(String value) {
        for (MoveToProdStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid MoveToProdStatus: " + value);
    }
}