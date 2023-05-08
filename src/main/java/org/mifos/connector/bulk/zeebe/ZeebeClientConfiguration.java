package org.mifos.connector.bulk.zeebe;

import org.springframework.context.annotation.Configuration;

@Configuration
public class ZeebeClientConfiguration {

    private String zeebeBrokerContactPoint;

    private int zeebeClientMaxThreads;

}
