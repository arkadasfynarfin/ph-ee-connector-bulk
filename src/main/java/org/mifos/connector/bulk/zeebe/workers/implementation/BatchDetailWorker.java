package org.mifos.connector.bulk.zeebe.workers.implementation;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.bulk.camel.routes.RouteId;
import org.mifos.connector.bulk.zeebe.workers.BaseWorker;
import org.mifos.connector.bulk.zeebe.workers.Worker;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.mifos.connector.bulk.camel.config.CamelProperties.TRANSACTION_COUNT;
import static org.mifos.connector.bulk.zeebe.ZeebeVariables.*;

@Component
public class BatchDetailWorker extends BaseWorker {

    @Override
    public void setup() {

        newWorker(Worker.BATCH_DETAILS, (client, job) -> {
            Map<String, Object> variables = job.getVariablesAsMap();
            int pageNumber = (int) variables.getOrDefault(PAGE_NUMBER, 1);
            int pageSize = (int) variables.getOrDefault(PAGE_SIZE, 10);
            int currentTransactionCount = (int) variables.getOrDefault(CURRENT_TRANSACTION_COUNT, 0);

            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(BATCH_ID, variables.get(BATCH_ID));
            exchange.setProperty(PAGE_NUMBER, pageNumber);
            exchange.setProperty(PAGE_SIZE, pageSize);
            exchange.setProperty(TENANT_ID, variables.get(TENANT_ID));

            sendToCamelRoute(RouteId.BATCH_DETAIL, exchange);

            boolean isReconciliationSuccess = (boolean) exchange.getProperty(RECONCILIATION_SUCCESS);

            if (!isReconciliationSuccess) {
                variables.put(ERROR_CODE, exchange.getProperty(ERROR_CODE));
                variables.put(ERROR_DESCRIPTION, exchange.getProperty(ERROR_DESCRIPTION));
            }

            currentTransactionCount += (int) exchange.getProperty(TRANSACTION_COUNT);
            variables.put(RECONCILIATION_SUCCESS, isReconciliationSuccess);
            variables.put(CURRENT_TRANSACTION_COUNT, currentTransactionCount);
            variables.put(PAGE_NUMBER, ++pageNumber);

            client.newCompleteCommand(job.getKey()).variables(variables).send();
        });

    }
}
