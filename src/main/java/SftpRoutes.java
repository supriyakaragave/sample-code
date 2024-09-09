package com.stellantis.parts.orderconfirmation.api;

import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SftpRoutes extends BaseRouteBuilder {

    @Value("${sftp_url}")
    private String sftp_url;

    @Value("${salesforce_oauth_token_url}")
    private String salesforce_oauth_token_url;

    @Override
    public void doConfigure() {

        // Load the property and split it by comma
        List<String> routeList = List.of(sftp_url.split(","));
        List<String> salesforce_oauth_token_url_list = List.of(salesforce_oauth_token_url.split("\\|"));

        // Create Camel routes based on the split string
        for (String route : routeList) {
        int index = routeList.indexOf(route);
            from(route)
                    .log(LoggingLevel.INFO, COM_STELLANTIS_PARTS_ORDERCONFIRMATION, REQUEST_LOG)
                    .setHeader(SALESFORCE_TOKEN_URL,constant(salesforce_oauth_token_url_list.get(index)))
                    .log("${headers}")
                    .wireTap(DIRECT_PARTS_ORDER_CONFIRMATION);
        }
    }
}