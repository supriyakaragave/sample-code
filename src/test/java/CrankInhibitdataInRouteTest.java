import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellantis.connectedcars.crankInhibitdataIn.CrankInhibitdataInApplication;
import com.stellantis.connectedcars.crankInhibitdataIn.model.CrankInhibitdataIn;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

//mock endpoints sample
//ExchangeBuilder
@CamelSpringBootTest
@SpringBootTest(classes = CrankInhibitdataInApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CrankInhibitdataInRouteTest {

    @Autowired
    private CamelContext context;
    @Autowired
    private TestRestTemplate template;

    @Autowired
    private ProducerTemplate producer;

    @EndpointInject("mock:httpEndpoint")
    private MockEndpoint mockEndpoint;

    @EndpointInject("mock:salesforceEndpoint")
    private MockEndpoint mockSalesforceEndpoint;

    @EndpointInject("mock:salesforceTokenEndpoint")
    private MockEndpoint mockSalesforceTokenEndpoint;

    @ParameterizedTest
    @CsvSource({"12345,MCARJ7ANXNFA99992,List has no rows for assignment to SObject,400 BAD_REQUEST"})
    void noRows_Success_testCase_NotMocked(String userId , String vin, String expectedBody, String expectedResponseCode) throws Exception {
        String content = Files.readString(Path.of("src/test/resources/request.json"));

        // Prepare request headers if needed
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Prepare request entity if needed
        HttpEntity<String> requestEntity = new HttpEntity<>(content, headers);

        // Start the Camel context
        context.start();

        // Make the request using exchange method
        ResponseEntity<String> responseEntity = template.exchange(
                "http://localhost:8080/api/users/"+userId+"/vehicles/" + vin + "/notification",
                HttpMethod.PUT, // or GET, PUT, DELETE, etc.
                requestEntity,   // Request body and headers
                String.class);   // Response type

        // Assert on the received message
        Assertions.assertNotNull(mockEndpoint.getExchanges());
        Assertions.assertTrue(mockEndpoint.getExchanges().isEmpty());
        Assertions.assertEquals(expectedBody,responseEntity.getBody() );
        Assertions.assertEquals(expectedResponseCode,responseEntity.getStatusCode().toString() );

    }

    @ParameterizedTest
    @CsvSource({"12345,MCARJ7ANXNFA99992,List has no rows for assignment to SObject"})
    void noRows_Success_testCase_Mocked(String userId , String vin, String expectedBody) throws Exception {
        CrankInhibitdataIn input = (CrankInhibitdataIn) createInput("src/test/resources/Request.json", CrankInhibitdataIn.class);

        // Mock the wireTap endpoint
        AdviceWith.adviceWith(context, "crankInhibitdataIn", a -> {
            a.weaveById("salesforceTokenUrl").replace().to(mockSalesforceTokenEndpoint);
            a.weaveById("salesforceApiUrl").replace().to(mockSalesforceEndpoint);
        });

        MockEndpoint mockTokenEP = context.getEndpoint(mockSalesforceTokenEndpoint.getEndpointUri(), MockEndpoint.class);
        mockTokenEP.whenAnyExchangeReceived(e -> e.getIn().setBody("{\"access_token\": \"token\",\"instance_url\": \"https://salesforce-api-url.com\"}"));

        MockEndpoint salesforceApiEndpoint = context.getEndpoint(mockSalesforceEndpoint.getEndpointUri(), MockEndpoint.class);
        salesforceApiEndpoint.whenAnyExchangeReceived(e -> e.getIn().setBody(expectedBody));

        // Send the test data to the route
        Exchange exchange = ExchangeBuilder.anExchange(context)
                .withBody(input)
                .withHeader("vin", vin)
                .withHeader("userId", userId)
                .build();
        producer.send("direct:crankInhibitdataIn", exchange);

        // Assertions
        mockTokenEP.expectedMessageCount(1);
        salesforceApiEndpoint.expectedMessageCount(1);
        salesforceApiEndpoint.message(0).body().isEqualTo(expectedBody);

        mockTokenEP.assertIsSatisfied();
        salesforceApiEndpoint.assertIsSatisfied();
    }

    public static Object createInput(String filePath, Class<?> targetClass) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Object example = objectMapper.readValue(new File(filePath), targetClass);
        return example;
    }
}