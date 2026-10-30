package com.zdph.dksdk.enums;


public enum DataWorksOperationState {
    SUBMIT_ACCEPTED,
    PUBLISH_ACCEPTED,
    RUN_ACCEPTED,
    WAITING,
    SUBMIT_FAILED,
    CHECK_FAILED,
    STATUS_ONLY;

    public boolean isAccepted() {
        return this == SUBMIT_ACCEPTED || this == PUBLISH_ACCEPTED || this == RUN_ACCEPTED;
    }
}
