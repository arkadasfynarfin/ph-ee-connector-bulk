package org.mifos.connector.bulk.camel.routes;

import org.mifos.connector.bulk.config.PaymentModeConfiguration;
import org.mifos.connector.bulk.config.PaymentModeMapping;
import org.mifos.connector.bulk.config.PaymentModeType;
import org.mifos.connector.bulk.schema.Transaction;
import org.mifos.connector.bulk.schema.TransactionResult;
import org.mifos.connector.bulk.utils.Utils;
import org.mifos.connector.bulk.zeebe.ZeebeProcessStarter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

//import static org.mifos.connector.bulk.camel.routes.FileRoute.LOCAL_FILE_PATH;

//import static org.mifos.connector.bulk.camel.routes.FileProcessingRoute.TRANSACTION_LIST;

@Component
public class InitBatchTransferRoute extends BaseRouteBuilder {

    private static final String TRANSACTION_LIST = "transactionList";
    private static final String BATCH_ID = "batchId";
    private static final String FILE_NAME = "fileName";
    private static final String REQUEST_ID = "requestId";
    private static final String PURPOSE = "purpose";
    private static final String TOTAL_AMOUNT = "totalAmount";
    private static final String FAILED_AMOUNT = "failedAmount";
    private static final String ONGOING_AMOUNT = "ongoingAmount";
    private static final String RESULT_FILE = "resultFile";
    private static final String ZEEBE_VARIABLE = "zeebeVariable";
    private static final String PAYMENT_MODE = "paymentMode";
    private static final String COMPLETED_AMOUNT = "completedAmount";
    private static final String IS_PAYMENT_MODE_VALID = "isPaymentModeValid";
    private static final String PAYMENT_MODE_TYPE = "paymentModeType";
    private static final String INIT_BATCH_TRANSFER_FAILED = "initBatchTransferFailed";
    private static final String RESULT_TRANSACTION_LIST = "resultTransactionList";
    private static final String LOCAL_FILE_PATH = "localFilePath";
    private static final String OVERRIDE_HEADER = "overrideHeader";
    private static final String TENANT_NAME = "tenantName";

    @Autowired
    private PaymentModeConfiguration paymentModeConfiguration;

    @Autowired
    private ZeebeProcessStarter zeebeProcessStarter;

    @Override
    public void configure() throws Exception {

        from(RouteId.INIT_BATCH_TRANSFER.toString())
                .id(RouteId.INIT_BATCH_TRANSFER.toString())
                .log("Starting route: " + RouteId.INIT_BATCH_TRANSFER.getValue())
                .to("direct:download-file")
                .to("direct:get-transaction-array")
                .to("direct:start-workflow");

        from("direct:start-workflow")
                .id("direct:start-workflow")
                .log("Starting route: direct:start-workflow-1");

        from("direct:start-workflow-1")
                .id("direct:start-flow-step1")
                .log("Starting route direct:start-flow-step1")
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
                .to("direct:start-workflow-step2");

        from("direct:start-workflow-step2")
                .id("direct:start-flow-step2")
                .log("Starting route direct:start-flow-step2")
                .to("direct:validate-payment-mode")
                .choice()
                // if invalid payment mode
                .when(exchangeProperty(IS_PAYMENT_MODE_VALID).isEqualTo(false))
                .to("direct:payment-mode-missing")
                .setProperty(INIT_BATCH_TRANSFER_FAILED, constant(true))
                // else
                .otherwise()
                .to("direct:start-workflow-step3")
                .endChoice();

        from("direct:validate-payment-mode")
                .id("direct:validate-payment-mode")
                .log("Starting route direct:validate-payment-mode")
                .process(exchange -> {
                    String paymentMde = exchange.getProperty(PAYMENT_MODE, String.class);
                    PaymentModeMapping mapping = paymentModeConfiguration.getByMode(paymentMde);
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


        from("direct:start-workflow-step3")
                .id("direct:start-flow-step3")
                .log("Starting route direct:start-flow-step3")
                .choice()
                // if type of payment mode is bulk
                .when(exchangeProperty(PAYMENT_MODE_TYPE).isEqualTo(PaymentModeType.BULK))
                .process(exchange -> {
                    String paymentMode = exchange.getProperty(PAYMENT_MODE, String.class);
                    PaymentModeMapping mapping = paymentModeConfiguration.getByMode(paymentMode);

                    String tenantName = exchange.getProperty(TENANT_NAME, String.class);
                    Map<String, Object> variables = exchange.getProperty(ZEEBE_VARIABLE, Map.class);
                    variables.put(PAYMENT_MODE, paymentMode);
                    zeebeProcessStarter.startZeebeWorkflow(
                            Utils.getBulkConnectorBpmnName(mapping.getEndpoint(), mapping.getId().toLowerCase(), tenantName),
                            variables);
                    exchange.setProperty(INIT_BATCH_TRANSFER_FAILED, false);
                });
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