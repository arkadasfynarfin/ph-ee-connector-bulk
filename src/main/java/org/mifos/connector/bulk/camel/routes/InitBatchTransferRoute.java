package org.mifos.connector.bulk.camel.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.mifos.connector.bulk.config.PaymentModeConfiguration;
import org.mifos.connector.bulk.config.PaymentModeMapping;
import org.mifos.connector.bulk.config.PaymentModeType;
import org.mifos.connector.bulk.schema.Transaction;
import org.mifos.connector.bulk.schema.TransactionResult;
import org.mifos.connector.bulk.utils.Utils;
import org.mifos.connector.bulk.zeebe.ZeebeProcessStarter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.mifos.connector.bulk.zeebe.ZeebeVariables.*;

@Component
public class InitBatchTransferRoute extends BaseRouteBuilder {

    private static final String ZEEBE_VARIABLE = "zeebeVariable";
    private static final String IS_PAYMENT_MODE_VALID = "isPaymentModeValid";
    private static final String PAYMENT_MODE_TYPE = "paymentModeType";
    private static final String RESULT_TRANSACTION_LIST = "resultTransactionList";
    private static final String LOCAL_FILE_PATH = "localFilePath";
    private static final String OVERRIDE_HEADER = "overrideHeader";

    @Value("${payment-mode.default}")
    private String defaultPaymentMode;

    @Value("${payment-mode.default}")
    private String bulkProcessorContactPoint;

    @Value("${payment-mode.default}")
    private String bulkProcessorEndPoint;

    @Autowired
    private PaymentModeConfiguration paymentModeConfiguration;

    @Autowired
    private ZeebeProcessStarter zeebeProcessStarter;

    @Override
    public void configure() throws Exception {

        from("direct:initBatchTransfer")
                .id("direct:initBatchTransfer")
                .log("Starting route: " + RouteId.INIT_BATCH_TRANSFER.getValue())
                .to("direct:download-file")
                .to("direct:get-transaction-array")
                .to("direct:start-workflow-1");

//        from("direct:start-workflow")
//                .id("direct:start-workflow")
//                .log("Starting route: direct:start-workflow");

        from("direct:start-workflow-step-1")
                .id("direct:start-workflow-step-1")
                .log("Starting route: direct:start-workflow-step-1")
                .process(exchange -> {
                    List<Transaction> transactionList = exchange.getProperty(TRANSACTION_LIST, List.class);

                    Map<String, Object> variables = new HashMap<>();
                    variables.put(BATCH_ID, exchange.getProperty(BATCH_ID));
                    variables.put(FILE_NAME, exchange.getProperty(FILE_NAME));
                    variables.put(REQUEST_ID, exchange.getProperty(REQUEST_ID));
                    variables.put(PURPOSE, exchange.getProperty(PURPOSE));
                    variables.put(TOTAL_AMOUNT, exchange.getProperty(TOTAL_AMOUNT));
                    variables.put(ONGOING_AMOUNT, exchange.getProperty(ONGOING_AMOUNT));
                    variables.put(FAILED_AMOUNT, exchange.getProperty(FAILED_AMOUNT));
                    variables.put(COMPLETED_AMOUNT, exchange.getProperty(COMPLETED_AMOUNT));
                    variables.put(RESULT_FILE, String.format("Result_%s",
                            exchange.getProperty(FILE_NAME)));

                    exchange.setProperty(ZEEBE_VARIABLE, variables);
                    exchange.setProperty(PAYMENT_MODE, transactionList.get(0).getPaymentMode());


                })
                .to("direct:start-workflow-step-2");

        from("direct:start-workflow-step-2")
                .id("direct:start-workflow-step-2")
                .log("Starting route: direct:start-workflow-step-2")
                .to("direct:validate-payment-mode")
                .choice()
                // if invalid payment mode
                .when(exchangeProperty(IS_PAYMENT_MODE_VALID).isEqualTo(false))
                .to("direct:payment-mode-missing")
                .setProperty(INIT_BATCH_TRANSFER_SUCCESS, constant(false))
                // else
                .otherwise()
                .to("direct:start-workflow-step-3")
                .endChoice();

        from("direct:validate-payment-mode")
                .id("direct:validate-payment-mode")
                .log("Starting route direct:validate-payment-mode")
                .process(exchange -> {
                    String paymentMode = exchange.getProperty(PAYMENT_MODE, String.class);
                    PaymentModeMapping mapping = paymentModeConfiguration.getByMode(paymentMode);
                    if (mapping == null) {
                        exchange.setProperty(IS_PAYMENT_MODE_VALID, false);
                    } else {
                        exchange.setProperty(IS_PAYMENT_MODE_VALID, true);
                        exchange.setProperty(PAYMENT_MODE_TYPE, mapping.getType());
                    }
                });

        from("direct:payment-mode-missing")
                .id("direct:payment-mode-missing")
                .log("Starting route direct:payment-mode-missing")
                .process(exchange -> {
                    String serverFileName = exchange.getProperty(FILE_NAME, String.class);
                    String resultFile = String.format("Result_%s", serverFileName);

                    List<Transaction> transactionList = exchange.getProperty(TRANSACTION_LIST, List.class);
                    List<TransactionResult> transactionResultList = updateTransactionStatusToFailed(transactionList);
                    exchange.setProperty(RESULT_TRANSACTION_LIST, transactionResultList);
                    exchange.setProperty(RESULT_FILE, resultFile);
                })
                // setting localfilepath as result file to make sure result file is uploaded
                .setProperty(LOCAL_FILE_PATH, exchangeProperty(RESULT_FILE))
                .setProperty(OVERRIDE_HEADER, constant(true))
                .process(exchange -> {
                    logger.info("A1 {}", exchange.getProperty(RESULT_FILE));
                    logger.info("A2 {}", exchange.getProperty(LOCAL_FILE_PATH));
                    logger.info("A3 {}", exchange.getProperty(OVERRIDE_HEADER));
                })
                .to("direct:update-result-file")
                .to("direct:upload-file");


//        from("direct:start-workflow-step-3")
//                .id("direct:start-workflow-step-3")
//                .log("Starting route direct:start-workflow-step-3")
//                .choice()
//                // if type of payment mode is bulk
//                .when(exchangeProperty(PAYMENT_MODE_TYPE).isEqualTo(PaymentModeType.BULK))
//                .process(exchange -> {
//                    String paymentMode = exchange.getProperty(PAYMENT_MODE, String.class);
//                    PaymentModeMapping mapping = paymentModeConfiguration.getByMode(paymentMode);
//
//                    String tenantName = exchange.getProperty(TENANT_NAME, String.class);
//                    Map<String, Object> variables = exchange.getProperty(ZEEBE_VARIABLE, Map.class);
//                    variables.put(PAYMENT_MODE, paymentMode);
//                    zeebeProcessStarter.startZeebeWorkflow(
//                            Utils.getBulkConnectorBpmnName(mapping.getEndpoint(), mapping.getId().toLowerCase(), tenantName),
//                            variables);
//                    exchange.setProperty(INIT_BATCH_TRANSFER_SUCCESS, true);
//                });

        from("direct:start-workflow-step-3")
                .id("direct:start-workflow-step-3")
                .log("Starting route direct:start-workflow-step-3")
                .to("direct:update-payment-mode")
                .to("direct:batch-transaction")
                .to("direct:batch-transaction-response-handler");

//                .choice()
//                // if type of payment mode is bulk
//                .when(exchangeProperty(PAYMENT_MODE_TYPE).isEqualTo(PaymentModeType.BULK))
//                .process(exchange -> {
//                    String paymentMode = exchange.getProperty(PAYMENT_MODE, String.class);
//                    PaymentModeMapping mapping = paymentModeConfiguration.getByMode(paymentMode);
//
//                    String tenantName = exchange.getProperty(TENANT_NAME, String.class);
//                    Map<String, Object> variables = exchange.getProperty(ZEEBE_VARIABLE, Map.class);
//                    variables.put(PAYMENT_MODE, paymentMode);
//                    zeebeProcessStarter.startZeebeWorkflow(
//                            Utils.getBulkConnectorBpmnName(mapping.getEndpoint(), mapping.getId().toLowerCase(), tenantName),
//                            variables);
//                    exchange.setProperty(INIT_BATCH_TRANSFER_SUCCESS, true);
//                });

        from("direct:update-payment-mode")
                .id("direct:update-payment-mode")
                .log("Starting route direct:update-payment-mode")
                .process(exchange ->{
                    List<Transaction> transactions = exchange.getProperty(TRANSACTION_LIST, List.class);
                    List<Transaction> updatedTransactions= transactions.stream()
                            .map(transaction -> {transaction.setPaymentMode(defaultPaymentMode);
                            return transaction;
                    }).collect(Collectors.toList());
                });

        from("direct:batch-transaction")
                .id("direct:batch-transaction")
                .log("Starting route: " + "direct:batch-transaction")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpRequestMethod.POST))
                .setHeader("X-Date", simple(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )))
                .setHeader("Accept", constant("application/json, text/plain, */*"))
                .setHeader(Exchange.REST_HTTP_QUERY, simple("type=csv"))
                .setHeader("Purpose", simple("test"))
                .setHeader("filename", simple("${exchangeProperty." + FILE_NAME + "}"))
                .setHeader("Platform-TenantId", simple("${exchangeProperty." + TENANT_ID + "}"))
                .process(exchange -> {
                    logger.info(exchange.getIn().getHeaders().toString());
                })
                .marshal().mimeMultipart()
                .setHeader(Exchange.CONTENT_TYPE, constant("multipart/form-data"))
//                .toD(bulkProcessorContactPoint + bulkProcessorEndPoint + "?bridgeEndpoint=true&throwExceptionOnFailure=false")
                .toD("" + "?bridgeEndpoint=true&throwExceptionOnFailure=false&multipart=true")
                .log(LoggingLevel.INFO, "Batch transaction API response: \n\n ${body}");

        from("direct:batch-transaction-response-handler")
                .id("direct:batch-transaction-response-handler")
                .log("Starting route direct:batch-transaction-response-handler")
                .choice()
                .when(header("CamelHttpResponseCode").isEqualTo(200))
                .process(exchange -> {
                    logger.info("reached here");
                    exchange.setProperty(INIT_BATCH_TRANSFER_SUCCESS, true);})
                .otherwise()
                .process(exchange -> {
                    exchange.setProperty(INIT_BATCH_TRANSFER_SUCCESS, false);
                })
                .endChoice();
    }

    private List<TransactionResult> updateTransactionStatusToFailed(List<Transaction> transactionList) {
        List<TransactionResult> transactionResultList = new ArrayList<>();
        for (Transaction transaction : transactionList) {
            TransactionResult transactionResult = Utils.mapToResultDTO(transaction);
            transactionResult.setErrorCode("404");
            transactionResult.setErrorDescription("Payment mode not configured");
            transactionResult.setStatus("Failed");
            transactionResultList.add(transactionResult);
        }
        return transactionResultList;
    }


}