package org.mifos.connector.bulk.camel.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.bulk.schema.BatchDTO;
import org.mifos.connector.bulk.schema.BatchDetailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.mifos.connector.bulk.zeebe.ZeebeVariables.*;

@Component
public class BatchDetailRoute extends BaseRouteBuilder {


    private static final String PAGE_NO = "pageNo";
    @Value("${config.completion-threshold-check.completion-threshold}")
    private int completionThreshold;

    private static final String OPS_APP_ACCESS_TOKEN = "opsAppAccessToken";

    @Override
    public void configure() throws Exception {

        from(RouteId.BATCH_DETAIL.getValue())
                .id(RouteId.BATCH_DETAIL.getValue())
                .log("Starting route " + RouteId.BATCH_DETAIL.name())
                .to("direct:get-access-token")
                .choice()
                .when(exchange -> exchange.getProperty(OPS_APP_ACCESS_TOKEN, String.class) != null)
                .log(LoggingLevel.INFO, "Got access token, moving on to API call")
                .to("direct:batch-detail")
                .to("direct:batch-detail-response-handler")
                .otherwise()
                .log(LoggingLevel.INFO, "Authentication failed.")
                .endChoice();

        getBaseExternalApiRequestRouteDefinition("batch-detail", HttpRequestMethod.GET)
                .setHeader(Exchange.REST_HTTP_QUERY, simple("batchId=${exchangeProperty." + BATCH_ID + "}"))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+OPS_APP_ACCESS_TOKEN+"}"))
                .setHeader("Platform-TenantId", simple("${exchangeProperty." + TENANT_ID + "}"))
                .setHeader("pageNo", simple("${exchangeProperty." + PAGE_NO + "}"))
                .setHeader("pageSize", simple("${exchangeProperty." + PAGE_SIZE + "}"))
                .process(exchange -> {
                    logger.info(exchange.getIn().getHeaders().toString());
                })
                .toD(operationsAppConfig.batchDetailUrl + "?bridgeEndpoint=true")
                .log(LoggingLevel.INFO, "Batch detail API response: \n\n ${body}");

        from("direct:batch-detail-response-handler")
                .id("direct:batch-detail-response-handler")
                .log("Starting route direct:batch-detail-response-handler")
                //.setBody(exchange -> exchange.getIn().getBody(String.class))
                .choice()
                .when(header("CamelHttpResponseCode").isEqualTo("200"))
                .log(LoggingLevel.INFO, "Batch detail request successful")
                .unmarshal().json(JsonLibrary.Jackson, BatchDetailResponse.class)
                .process(exchange -> {
                    BatchDetailResponse response = exchange.getIn().getBody(BatchDetailResponse.class);

                    int pageNo = Integer.parseInt(exchange.getProperty(PAGE_NO, String.class));
                    int currentTransferCount = Integer.parseInt(exchange.getProperty(CURRENT_TRANSACTION_COUNT, String.class));
                    int completedTransferCount = Integer.parseInt(exchange.getProperty(COMPLETED_TRANSACTION_COUNT, String.class));
                    int failedTransferCount = Integer.parseInt(exchange.getProperty(FAILED_TRANSACTION_COUNT, String.class));
                    int ongoingTransferCount = Integer.parseInt(exchange.getProperty(ONGOING_TRANSACTION_COUNT, String.class));
                    int totalTransferCount = Integer.parseInt(exchange.getProperty(TOTAL_TRANSACTION_COUNT, String.class));
                    // fetch details based on response

                    List<Object> transfers = null;
                    int completedTransferCountPerPage = 0;
                    int ongoingTransferCountPerPage = 0;
                    int failedTransferCountPerPage = 0;
                    int transferCountPerPage = 0;

                    for(Object transfer : transfers){
//                        String transferStatus = transfer.getStatus();
                        String transferStatus = "";
                        if(("COMPLETED").equals(transferStatus)){
                            completedTransferCountPerPage++;
                        }
                        else if(("FAILED").equals(transferStatus)){
                            failedTransferCountPerPage++;
                        }
                        else if(("IN_PROGRESS").equals(transferStatus)){
                            ongoingTransferCountPerPage++;
                        }
                        transferCountPerPage++;
                    }

                    currentTransferCount += transferCountPerPage;
                    completedTransferCount += completedTransferCountPerPage;
                    failedTransferCount += failedTransferCountPerPage;
                    ongoingTransferCount += ongoingTransferCountPerPage;

                    exchange.setProperty(CURRENT_TRANSACTION_COUNT, currentTransferCount);
                    exchange.setProperty(COMPLETED_TRANSACTION_COUNT, completedTransferCount);
                    exchange.setProperty(FAILED_TRANSACTION_COUNT, failedTransferCount);
                    exchange.setProperty(ONGOING_TRANSACTION_COUNT, ongoingTransferCount);

                    if(currentTransferCount>=totalTransferCount){
                        exchange.setProperty(BATCH_DETAIL_SUCCESS, true);
                    }
                    else {
                        exchange.setProperty(BATCH_DETAIL_SUCCESS, false);
                    }

                })
                .otherwise()
                .log(LoggingLevel.ERROR, "Batch detail request unsuccessful")
                .process(exchange -> {
                    exchange.setProperty(BATCH_DETAIL_SUCCESS, false);
                    exchange.setProperty(ERROR_DESCRIPTION, exchange.getIn().getBody(String.class));
                    exchange.setProperty(ERROR_CODE, exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
                });

    }
}
