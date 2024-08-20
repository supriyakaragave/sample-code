package com.stellantis.jl.serviceappointment.api;


import com.stellantis.jl.serviceappointment.model.AppointmentStatusUpdateRequest;
import com.stellantis.jl.serviceappointment.model.CreateServiceAppointmentRequest;
import com.stellantis.jl.serviceappointment.model.Request;
import com.stellantis.jl.serviceappointment.model.VinValidationRequest;
import io.jsonwebtoken.Jwts;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.converter.crypto.CryptoDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;


@Component
public class Route extends BaseRouteBuilder {

    //Encryption and JWT token
    public static final String SALESFORCE_APPT_CREATE_API_URI = "salesforce-appt-create-api-uri";


    @Value("${jeeplife.status.update.secret.key}")
    private String secretKeySpec;

    @Value("${jeeplife.status.update.secret.iv}")
    private String iv;

    @Value("${jeeplife.status.update.secret.encSignKey}")
    private String encSignKeyStr;

    @Value("${jeeplife.status.update.secret.issuer}")
    private String issuer;

    @Override
    public void doConfigure() {

        Key key = new SecretKeySpec(secretKeySpec.getBytes(), "AES");

        CryptoDataFormat cryptoFormat = new CryptoDataFormat("AES/CBC/PKCS5Padding",key,"SunJCE");
        cryptoFormat.setInitializationVector(iv.getBytes());
        cryptoFormat.setShouldAppendHMAC(false);

        from(DIRECT_PROCESS_VIN_REQUEST)
            .id("processVinRequest")
            .streamCaching()
            .removeHeaders("*")

            .setHeader(LOG_MESSAGE).simple("VIN Validation Request received.")
            .to(DIRECT_LOG_MESSAGE)

            .process(exchange ->  {
                    exchange.getIn().setHeader("vin", exchange.getIn().getBody(VinValidationRequest.class).vin);
            })
            .marshal().json(JsonLibrary.Jackson, VinValidationRequest.class)
            .to(BEAN_VALIDATOR_X)
            .unmarshal().json(JsonLibrary.Jackson, VinValidationRequest.class)
            .setHeader("salesforce-api-uri", simple("{{salesforce.vin.validation.api.uri}}${header.vin}"))

            .to("direct:getAuthToken?exchangePattern=InOnly")

            .setBody().constant("")
            .setHeader(LOG_MESSAGE).simple(REQUEST_SENT_TO_SALESFORCE)
            .to(DIRECT_LOG_MESSAGE)

            .setHeader(HttpHeaders.CONTENT_TYPE).simple(MediaType.APPLICATION_JSON_VALUE)
            .setHeader(Exchange.HTTP_METHOD).constant(HttpMethod.GET)
            .setHeader(HttpHeaders.AUTHORIZATION).simple(BEARER_HEADERS_TOKEN)
            .toD(" ${headers.salesforce-api}${headers.salesforce-api-uri}").id("salesforceApiUrl")

            .unmarshal().json(JsonLibrary.Jackson)
            .removeHeaders("*")

            .setHeader(LOG_MESSAGE).simple("Response Received From Salesforce:${body}")
            .to(DIRECT_LOG_MESSAGE)
            .end();

        from(DIRECT_CREATE_APPT_SET_API)
                .streamCaching()

                .choice()
                .when(simple("${headers.CamelHttpUri} contains 'jeepbrandwebsite'"))
                    .setHeader(LOG_MESSAGE).simple("Appointment Request received from Jeep Brand Website.")
                    .to(DIRECT_LOG_MESSAGE)
                    .setHeader(SALESFORCE_APPT_CREATE_API_URI).simple("{{salesforce.appt.create.jeepbrandwebsite.api.uri}}")
                .endChoice()
                .when(simple("${headers.CamelHttpUri} contains 'voicebot'"))
                    .setHeader(LOG_MESSAGE).simple("Appointment Request received from VoiceBot.")
                    .to(DIRECT_LOG_MESSAGE)
                    .setHeader(SALESFORCE_APPT_CREATE_API_URI).simple("{{salesforce.appt.create.voicebot.api.uri}}")
                .endChoice()
                .when(simple("${headers.CamelHttpUri} contains 'jeeplife'"))
                    .setHeader(LOG_MESSAGE).simple("Appointment Request received JeepLife App.")
                    .to(DIRECT_LOG_MESSAGE)
                    .setHeader(SALESFORCE_APPT_CREATE_API_URI).simple("{{salesforce.appt.create.jeeplife.api.uri}}")
                .endChoice()
                .end()
                .end();

        from(DIRECT_CREATE_APPOINTMENT)
                .id("createAppointment")
                .streamCaching()
                .to(DIRECT_CREATE_APPT_SET_API)
                .removeHeaders("*","salesforce*")

                .marshal().json(JsonLibrary.Jackson, CreateServiceAppointmentRequest.class)
                .setProperty(REQUEST_BODY, simple(BODY))
                .to(BEAN_VALIDATOR_X)
                .unmarshal().json(JsonLibrary.Jackson, CreateServiceAppointmentRequest.class)

                .to("direct:getAuthToken?exchangePattern=InOnly")

                .setBody().exchangeProperty(REQUEST_BODY)
                .setHeader(LOG_MESSAGE).simple(REQUEST_SENT_TO_SALESFORCE)
                .to(DIRECT_LOG_MESSAGE)

                .setHeader(HttpHeaders.CONTENT_TYPE).simple(MediaType.APPLICATION_JSON_VALUE)
                .setHeader(Exchange.HTTP_METHOD).constant(HttpMethod.POST)
                .setHeader(HttpHeaders.AUTHORIZATION).simple(BEARER_HEADERS_TOKEN)
                .toD("${headers.salesforce-api}${headers.salesforce-appt-create-api-uri}?throwExceptionOnFailure=false").id("createApptApiUrl")

                .setHeader(LOG_MESSAGE).simple("Response Received From Salesforce:")
                .to(DIRECT_LOG_MESSAGE)

                .unmarshal().json(JsonLibrary.Jackson)
                .removeHeaders("*")
                .end()
                .end();

        from("direct:getAuthToken").id("getAuthToken")
            .streamCaching()
            .setBody().simple("")
            .setHeader(Exchange.HTTP_METHOD).constant(HttpMethod.POST)
            .setHeader(HttpHeaders.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON_VALUE))
            .toD("{{salesforce.oauth-token.url}}").id("salesforceTokenUrl")

            .setHeader(LOG_MESSAGE).simple("Salesforce token received to call Salesforce API.")
            .to(DIRECT_LOG_MESSAGE)

            .unmarshal().json()
            .setHeader("token").jsonpath("$.access_token")
            .setHeader("salesforce-api").jsonpath("$.instance_url")
            .end();


       from(DIRECT_PROCESS_STATUS_UPDATE)
           .streamCaching()
           .removeHeaders("*")
           .setHeader("CamelCryptoKey").constant(key)
           .process(exchange -> {
                    //Obtain JWT Auth token
                   byte[] encSignKey = Base64.getDecoder().decode(encSignKeyStr);
                   Key signKey = new SecretKeySpec(encSignKey, "HmacSHA256");

               String jwt = Jwts.builder()
                       .header().add("type","JWT").and()
                       .audience().add("https://test.salesforce.com").and()
                       .issuer(issuer)
                       .subject("skaragave")
                       .issuedAt(Date.from(Instant.now()))
                       .expiration(Date.from(Instant.now().plus(5L, ChronoUnit.MINUTES)))
                       .signWith(signKey)
                       .compact();

                   exchange.getIn().setHeader(HttpHeaders.AUTHORIZATION, "Bearer "+jwt);
           })
           .log(LoggingLevel.DEBUG,"JWT token : ${header.Authorization}")

           .setHeader(LOG_MESSAGE).simple("Token obtained for Jeeplife API.")
           .to(DIRECT_LOG_MESSAGE)

           .marshal().json(JsonLibrary.Jackson, AppointmentStatusUpdateRequest.class)
           .marshal(cryptoFormat)
           .log(LoggingLevel.DEBUG,"Encrypted Request : ${body}")
           .process(exchange -> {
                   String cryptoMsg = exchange.getIn().getBody(String.class);
                   exchange.getIn().setBody(Base64.getEncoder().encode(cryptoMsg.getBytes()));
           })
           .log(LoggingLevel.DEBUG,"Encoded Request : ${body}")
           .setBody(simple("{\"Data\":\"${body}\"}"))

           .log(LoggingLevel.INFO, COM_STELLANTIS_SERVICEAPPOINTMENT, "Appointment Status Update request sent to Jeeplife API.")

           .setHeader(Exchange.HTTP_METHOD).constant(HttpMethod.POST)
           .setHeader(HttpHeaders.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON_VALUE))
           .toD("{{jeeplife.status.update.url}}")

           .log(LoggingLevel.DEBUG,"Encoded Response : ${body}")
           .setHeader(LOG_MESSAGE).simple("Response received from Jeeplife API.")
           .to(DIRECT_LOG_MESSAGE)

           .process(exchange -> {
               String respBody = exchange.getIn().getBody(String.class).replace("{\"data\":\"", "").replace("\"}", "");
               exchange.getIn().setBody(Base64.getDecoder().decode(respBody.getBytes()));
           })

           .log(LoggingLevel.DEBUG,"Encrypted Response : ${body}")
           .unmarshal(cryptoFormat)

           .unmarshal().json()

           .setHeader(LOG_MESSAGE).simple("Response sent to Salesforce.")
           .to(DIRECT_LOG_MESSAGE)
           .end();

       from(DIRECT_APPT_RESCHEDULE_BOT)
            .id("processBotRescheduleRequest")
            .streamCaching()
            .removeHeaders("*")

            .setHeader(LOG_MESSAGE).simple("BOT Appointment Reschedule request received to call Salesforce API.")
            .to(DIRECT_LOG_MESSAGE)

            .marshal().json(JsonLibrary.Jackson, Request.class)
            .setProperty(REQUEST_BODY, simple(BODY))
            .to(BEAN_VALIDATOR_X)
            .unmarshal().json(JsonLibrary.Jackson, Request.class)

            .setBody().simple("")
            .setHeader(Exchange.HTTP_METHOD).constant(HttpMethod.POST)
            .setHeader(HttpHeaders.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON_VALUE))
            .toD("{{salesforce.bot.oauth-token.url}}").id("botApiSalesforceTokenUrl")

            .setHeader(LOG_MESSAGE).simple("Salesforce token received to call Salesforce API.")
            .to(DIRECT_LOG_MESSAGE)

            .unmarshal().json()
            .setHeader("token").jsonpath("$.access_token")
            .setHeader("salesforce-api").jsonpath("$.instance_url")

            .setBody().exchangeProperty(REQUEST_BODY)
            .setHeader(LOG_MESSAGE).simple(REQUEST_SENT_TO_SALESFORCE)
            .to(DIRECT_LOG_MESSAGE)

            .setHeader(HttpHeaders.CONTENT_TYPE).simple(MediaType.APPLICATION_JSON_VALUE)
            .setHeader(Exchange.HTTP_METHOD).constant(HttpMethod.PUT)
            .setHeader(HttpHeaders.AUTHORIZATION).simple(BEARER_HEADERS_TOKEN)
            .toD("${headers.salesforce-api}{{salesforce.appt.reschedule.api.uri}}").id("salesforceBotApiUrl")
            .removeHeaders("*")

            .setHeader(LOG_MESSAGE).simple("Response Received From Salesforce:")
            .to(DIRECT_LOG_MESSAGE)
            .convertBodyTo(String.class)
            .end();
    }
}