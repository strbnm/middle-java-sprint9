package ru.strbnm.notifications_service.contractTest;

import ru.strbnm.notifications_service.contractTest.BaseContractTest;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.restassured.module.webtestclient.specification.WebTestClientRequestSpecification;
import io.restassured.module.webtestclient.response.WebTestClientResponse;

import static org.springframework.cloud.contract.verifier.assertion.SpringCloudContractAssertions.assertThat;
import static org.springframework.cloud.contract.verifier.util.ContractVerifierUtil.*;
import static com.toomuchcoding.jsonassert.JsonAssertion.assertThatJson;
import static io.restassured.module.webtestclient.RestAssuredWebTestClient.*;

@SuppressWarnings("rawtypes")
public class ContractVerifierTest extends BaseContractTest {

	@Test
	public void validate_shouldCreateNotification() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"email\":\"test@example.ru\",\"message\":\"\u041F\u043E\u043F\u043E\u043B\u043D\u0435\u043D\u0438\u0435 \u0441\u0447\u0451\u0442\u0430 RUB \u043D\u0430 \u0441\u0443\u043C\u043C\u0443 300.00 \u0440\u0443\u0431.\",\"application\":\"accounts-service\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/notifications");

		// then:
			assertThat(response.statusCode()).isEqualTo(201);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			String responseBody = response.getBody().asString();
			assertThat(responseBody).isEqualTo("Success");
	}

	@Test
	public void validate_shouldReturnBadRequest() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"email\":\"test@example.ru\",\"message\":\"\u041F\u043E\u043F\u043E\u043B\u043D\u0435\u043D\u0438\u0435 \u0441\u0447\u0451\u0442\u0430 RUB \u043D\u0430 \u0441\u0443\u043C\u043C\u0443 300.00 \u0440\u0443\u0431.\",\"application\":\"some-service\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/notifications");

		// then:
			assertThat(response.statusCode()).isEqualTo(400);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['status_code']").isEqualTo(400);
			assertThatJson(parsedJson).field("['message']").isEqualTo("Unexpected value 'some-service'");
	}

}
