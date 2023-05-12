package org.mifos.connector.bulk.zeebe.workers.implementation;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.bulk.camel.routes.RouteId;
import org.mifos.connector.bulk.zeebe.workers.BaseWorker;
import org.mifos.connector.bulk.zeebe.workers.Worker;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.mifos.connector.bulk.camel.config.CamelProperties.BATCH_SUMMARY_SUCCESS;
import static org.mifos.connector.bulk.zeebe.ZeebeVariables.*;

@Component
public class BatchSummaryWorker extends BaseWorker {

    @Override
    public void setup() {
        newWorker(Worker.BATCH_SUMMARY, (client, job)->{
            Map<String, Object> variables = job.getVariablesAsMap();
            int currentRetryCount = (int) variables.getOrDefault(CURRENT_RETRY_COUNT, 0);

            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(BATCH_ID, variables.get(BATCH_ID));
            exchange.setProperty(TENANT_ID, variables.get(TENANT_ID));

            //sendToCamelRoute(RouteId.BATCH_SUMMARY, exchange);

            boolean isBatchSummarySuccess = true;

            if(!isBatchSummarySuccess){
                variables.put(ERROR_CODE, exchange.getProperty(ERROR_CODE));
                variables.put(ERROR_DESCRIPTION, exchange.getProperty(ERROR_DESCRIPTION));
                logger.info("Error: {}, {}", variables.get(ERROR_CODE), variables.get(ERROR_DESCRIPTION));
            }

            variables.put(CURRENT_RETRY_COUNT, 2);
            variables.put(TOTAL_RETRY_COUNT, 1);
            variables.put(ONGOING_TRANSACTION, exchange.getProperty(ONGOING_TRANSACTION));
            variables.put(FAILED_TRANSACTION, exchange.getProperty(FAILED_TRANSACTION));
            variables.put(TOTAL_TRANSACTION, exchange.getProperty(TOTAL_TRANSACTION));
            variables.put(COMPLETED_TRANSACTION, exchange.getProperty(COMPLETED_TRANSACTION));
            variables.put(ONGOING_AMOUNT, exchange.getProperty(ONGOING_AMOUNT));
            variables.put(FAILED_AMOUNT, exchange.getProperty(FAILED_AMOUNT));
            variables.put(COMPLETED_AMOUNT, exchange.getProperty(COMPLETED_AMOUNT));
            variables.put(TOTAL_AMOUNT, exchange.getProperty(TOTAL_AMOUNT));

            client.newCompleteCommand(job.getKey()).variables(variables).send();
        });
    }
}
