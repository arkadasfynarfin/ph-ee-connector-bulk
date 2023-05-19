package org.mifos.connector.bulk.zeebe.workers.implementation;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.bulk.camel.routes.RouteId;
import org.mifos.connector.bulk.zeebe.workers.BaseWorker;
import org.mifos.connector.bulk.zeebe.workers.Worker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.mifos.connector.bulk.zeebe.ZeebeVariables.*;

@Component
public class BatchTransferWorker extends BaseWorker {

    @Autowired
    private ProducerTemplate producerTemplate;

    @Override
    public void setup() {

        newWorker(Worker.INIT_BATCH_TRANSFER, (client, job) ->{
            Map<String, Object> variables = job.getVariablesAsMap();

            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(FILE_NAME, variables.get(FILE_NAME));
            exchange.setProperty(TENANT_ID, variables.get(TENANT_ID));
            exchange.setProperty(BATCH_ID, variables.get(BATCH_ID));
            exchange.setProperty(REQUEST_ID, variables.get(REQUEST_ID));
            exchange.setProperty(PURPOSE, variables.get(PURPOSE));

//            sendToCamelRoute("direct:initBatchTransfer", exchange);
            producerTemplate.send("direct:initBatchTransfer", exchange);

            variables.put(BATCH_ID, exchange.getProperty(BATCH_ID));

            client.newCompleteCommand(job.getKey()).variables(variables).send();
        });

    }
}
