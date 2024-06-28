import com.stellantis.warranty.globalclaimsystem.model.sf.SfRequest;
import com.stellantis.warranty.globalclaimsystem.util.Util;
import jakarta.xml.bind.ValidationException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;


@CamelSpringBootTest
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CustomValidationTest {

    @InjectMocks
    private ClaimValidator claimValidator;

    @ParameterizedTest
    //ParameterizedTest CSV
    @CsvSource({"src/test/resources/customValidations/ownerLastNameTest.json,OwnersLastName is required for Claim Type \"F\".",
            "src/test/resources/customValidations/ClaimSubmissionTypeTest.json,Invalid ClaimContinuationIdentifier for Claim Submission Type.",
            "src/test/resources/customValidations/ConditionTaxInformationTest.json,ConditionTaxInformation is required for Claim Type \"F\".",
            "src/test/resources/customValidations/serviceAdvisorIdTest.json,ServiceAdviserID is required for this Claim Type \"M\"."})

    void CustomValidationTest(String testFilePath,String validatiomMessage) throws Exception {
        // Mock the input and expected output
        SfRequest input = (SfRequest) Util.readJsonString(testFilePath,SfRequest.class);

        // Set up mock behavior for exchange and request
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());

        Message message = exchange.getIn();
        message.setHeader("Content-Type", "application/json");
        message.setBody(input, SfRequest.class);

        ValidationException exception = Assertions.assertThrows(ValidationException.class, () -> {
            claimValidator.process(exchange);
        });

        // Assert the result
        Assertions.assertEquals(validatiomMessage, exception.getMessage());

    }
}