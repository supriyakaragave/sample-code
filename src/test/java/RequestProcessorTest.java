import com.stellantis.warranty.globalclaimsystem.model.dcid.request.GCS;
import com.stellantis.warranty.globalclaimsystem.model.sf.SfRequest;
import com.stellantis.warranty.globalclaimsystem.util.Util;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;

@CamelSpringBootTest
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RequestProcessorTest {
    @InjectMocks
    private RequestProcessor requestProcessor;

    @ParameterizedTest
    @ValueSource(strings = {"src/test/resources/sampleRequestScenarios"})
    void testValidateSampleRequest(String folderPath) throws Exception {

        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

        Assertions.assertNotNull(files, "Folder is empty or does not exist");
        int counter = 0;
        for (File file : files) {
            counter++;
            System.out.println("***************************************************************");
            System.out.println(counter + " :: Processing file: " + file.getName());
            System.out.println("***************************************************************");

            // Load the XML input data
            SfRequest input = (SfRequest) Util.readJsonString(file.getAbsolutePath(), SfRequest.class);
            // Define the expected output
            GCS expectedOutput = (GCS) Util.readXmlString("src/test/resources/dcidRequests/"+ (file.getName().replace("json", "xml")), GCS.class);

            // Set up mock behavior for exchange and request
            Exchange exchange = new DefaultExchange(new DefaultCamelContext());

            Message message = exchange.getIn();
            message.setHeader("Content-Type", "application/json");
            message.setBody(input, SfRequest.class);

            requestProcessor.process(exchange);

            Assertions.assertNotNull(exchange.getIn().getBody());

            GCS response = exchange.getIn().getBody(GCS.class);

            assertThat(response)
                    .usingRecursiveComparison()
                    //.ignoringFieldsOfTypes(StatusInformation.class)
                    .ignoringExpectedNullFields()
                    .isEqualTo(expectedOutput);
        }
    }
}
