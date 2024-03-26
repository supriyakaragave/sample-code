package org.stellantis.quicklop.api;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.springframework.stereotype.Component;
import org.stellantis.quicklop.model.ql.QuickLop;
import org.stellantis.quicklop.model.sf.SFLopResponse;
import org.stellantis.quicklop.model.sf.SFSchemaRequest;
import org.stellantis.quicklop.processor.LopDetailResponseProcessor;
import org.stellantis.quicklop.processor.LopListResponseProcessor;
import org.stellantis.quicklop.processor.LopSearchResponseProcessor;
import org.stellantis.quicklop.processor.RequestProcessor;

import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.GetMethod;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;


import java.util.Arrays;

import static org.stellantis.quicklop.config.Constants.*;

@Component
public class QuickLopRoute extends BaseRouteBuilder {
    @Override
    public void doConfigure() {

        // Configure HTTP Component
        HttpComponent httpComponent = getContext().getComponent("http", HttpComponent.class);

        // Configure HTTP Client with Cookie Policy
        httpComponent.setHttpClientConfigurer(httpClientBuilder -> httpClientBuilder.setDefaultCookieStore(new BasicCookieStore()));


        // Enable Jackson JSON type converter for more types.
        getContext().getGlobalOptions().put("CamelJacksonEnableTypeConverter", "true");
        // Allow Jackson JSON to convert to pojo types also
        // (by default, Jackson only converts to String and other simple types)
        getContext().getGlobalOptions().put("CamelJacksonTypeConverterToPojo", "true");

        JacksonDataFormat jsonSFLopResponse = new JacksonDataFormat(SFLopResponse.class);

        from(DIRECT_PROCESS_REQUEST)
                .streamCaching()
                .removeHeaders("*")
                .marshal().json(JsonLibrary.Jackson, SFSchemaRequest.class)
                .to("json-validator:jsonSchema/SF_Schema_Request.json")
                .unmarshal().json(JsonLibrary.Jackson, SFSchemaRequest.class)
                .process(new RequestProcessor())
                .marshal().jacksonxml(QuickLop.class, null, true)

                .setHeader(LOG_MESSAGE).simple("QuickLOP Request posted.")
                .wireTap(DIRECT_LOG_MESSAGE)

                .setHeader("CamelHttpMethod").constant(HttpMethod.POST)
                .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_XHTML_XML))
                .setHeader("Accept-Encoding", constant("gzip"))
                .setHeader("vendorinterfacetype", constant("5"))
                .to("direct:getCookies")
                .removeHeader("logMessage").log("LOG1 ==> ${headers}").log("LOG2 ==> ${body}")
                .toD("{{quicklop.search.endpoint}}")
                .setHeader(LOG_MESSAGE).simple("QuickLOP Response Received.")
                .wireTap(DIRECT_LOG_MESSAGE)
                .to(DIRECT_PROCESS_RESPONSE)
                .removeHeaders("*")
                .end();

        from("direct:getCookies")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        /* init client */
                        HttpClient http = null;
                        CookieStore httpCookieStore = new BasicCookieStore();
                        HttpClientBuilder builder = HttpClientBuilder.create().setDefaultCookieStore(httpCookieStore);

                        http = builder.build();

                        /* do stuff */
                        HttpPost cookieRequest = new HttpPost("https://testdcid.extra.chrysler.com/siteminderagent/forms/devices.fcc");
                        cookieRequest.setEntity(new StringEntity("USER=IAPTest&PASSWORD=12345678&DEALERCODE=99970&TARGET=/dcid/DCIDServlet"));
                        cookieRequest.addHeader("Content-Type","application/x-www-form-urlencoded");

                        HttpResponse httpResponse = null;
                        try {httpResponse = http.execute(cookieRequest);} catch (Throwable error) {throw new RuntimeException(error);}

                        /* check cookies */
                        exchange.getIn().setHeader("Cookie",httpCookieStore.getCookies().get(0).getName() + "=" +httpCookieStore.getCookies().get(0).getValue());

                        // Set the request body
                        String xmlDeclaration = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><!DOCTYPE QuickLop SYSTEM \"quickLop.dtd\">";
                        String requestBody = xmlDeclaration+exchange.getIn().getBody(String.class);
                        exchange.getIn().setBody(requestBody);
                    }
                })
                .end();

        from(DIRECT_PROCESS_RESPONSE)
                .streamCaching()
                //.convertBodyTo(QuickLop.class)
                //.log("LOG1 ==>> ${body}")
                .choice()
                .when().xpath("QuickLop/LopResponse/LopDetailResponse")
                .process(new LopDetailResponseProcessor())
                .convertBodyTo(SFLopResponse.class)
                .setHeader(LOG_MESSAGE).simple("Final Lop Detail Response.")
                .wireTap(DIRECT_LOG_MESSAGE)
                .endChoice()
                .when().xpath("QuickLop/LopResponse/LopListResponse")
                .process(new LopListResponseProcessor())
                .convertBodyTo(SFLopResponse.class)
                .setHeader(LOG_MESSAGE).simple("Final Lop Detail Response.")
                .wireTap(DIRECT_LOG_MESSAGE)
                .endChoice()
                .when().xpath("QuickLop/LopResponse/LopSearchResponse")
                .process(new LopSearchResponseProcessor()).endChoice()
                .end();
    }
}
