package org.mifos.connector.bulk.camel.routes;

public enum RouteId {
    INIT_BATCH_TRANSFER("initBatchTransfer"),

    BATCH_SUMMARY("batchSummary"),

    BATCH_DETAIL("batchDetail");


    private final String value;


    private RouteId(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
