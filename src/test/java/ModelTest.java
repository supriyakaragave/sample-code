import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@CamelSpringBootTest
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ModelTest {
    @Test
    void noClaimRequestTest() throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        JsonSchema jsonSchema = factory.getSchema(ModelTest.class.getResourceAsStream("/SF_Json/SfRequest.json"));

        String content = new String(Files.readAllBytes(Paths.get("src/test/resources/SalesforceRequests/noClaimRequest.json")));

        JsonNode jsonNode = mapper.readTree(content);
        Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);
        assertThat(errors).isNotEmpty().asString().contains("$.claim: is missing but it is required");
    }

    @Test
    void testInvalidSfRequest() throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        JsonSchema jsonSchema = factory.getSchema(ModelTest.class.getResourceAsStream("/SF_Json/SfRequest.json"));

        String content = new String(Files.readAllBytes(Paths.get("src/test/resources/SalesforceRequests/noGcsKeyRequest.json")));

        JsonNode jsonNode = mapper.readTree(content);
        Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);
        assertThat(errors).isNotEmpty();
    }

    @Test
    void testValidSfRequest() throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        JsonSchema jsonSchema = factory.getSchema(ModelTest.class.getResourceAsStream("/SF_Json/SfRequest.json"));

        String content = new String(Files.readAllBytes(Paths.get("src/test/resources/SalesforceRequests/sampleRequest.json")));

        JsonNode jsonNode = mapper.readTree(content);
        Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);
        assertThat(errors).isEmpty();
    }

    //ParameterizedTest
    @ParameterizedTest
    @ValueSource(strings = {"src/test/resources/sampleRequestScenarios"})
    void testValidateSampleRequest(String folderPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        JsonSchema jsonSchema = factory.getSchema(ModelTest.class.getResourceAsStream("/SF_Json/SfRequest.json"));

        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        String content = null;

        Assertions.assertNotNull(files, "Folder is empty or does not exist");
        int counter = 0

        //Loop thru the files in folder
        for (File file : files) {
            counter++;
            System.out.println("***************************************************************");
            System.out.println(counter + " :: Reading file: " + file.getName());
            System.out.println("***************************************************************");

            content = new String(Files.readAllBytes(file.toPath()));
            JsonNode jsonNode = mapper.readTree(content);
            Set<ValidationMessage> errors = jsonSchema.validate(jsonNode);
            assertThat(errors).isEmpty();
        }
    }
}