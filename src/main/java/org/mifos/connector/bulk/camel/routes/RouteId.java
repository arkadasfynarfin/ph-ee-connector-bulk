package org.mifos.connector.bulk.camel.routes;

public enum RouteId {
    INIT_BATCH_TRANSFER("direct:initBatchTransfer"),

    BATCH_SUMMARY("direct:batchSummary"),

    BATCH_DETAIL("direct:batchDetail");


    private final String value;


    private RouteId(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
