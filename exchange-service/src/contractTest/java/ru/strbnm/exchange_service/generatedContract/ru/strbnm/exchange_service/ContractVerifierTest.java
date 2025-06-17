package ru.strbnm.exchange_service;

import ru.strbnm.exchange_service.BaseContractTest;
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
	public void validate_shouldConvertAmount() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"from\":\"RUB\",\"to\":\"USD\",\"amount\":1000.0}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/convert");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['amount']").isEqualTo(12.0);
	}

	@Test
	public void validate_shouldConvertAmountCNY2CNY() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"from\":\"CNY\",\"to\":\"CNY\",\"amount\":1000.0}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/convert");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['amount']").isEqualTo(1000.0);
	}

	@Test
	public void validate_shouldConvertAmountRUB2CNY() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"from\":\"RUB\",\"to\":\"CNY\",\"amount\":200000.0}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/convert");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['amount']").isEqualTo(22000.0);
	}

	@Test
	public void validate_shouldConvertAmountRUB2RUB() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"from\":\"USD\",\"to\":\"USD\",\"amount\":1000.0}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/convert");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['amount']").isEqualTo(1000.0);
	}

	@Test
	public void validate_shouldConvertAmountUDS2USD() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"from\":\"RUB\",\"to\":\"RUB\",\"amount\":1000.0}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/convert");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['amount']").isEqualTo(1000.0);
	}

	@Test
	public void validate_shouldReturnRates() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json");

		// when:
			WebTestClientResponse response = given().spec(request)
					.get("/api/v1/rates");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).array().contains("['title']").isEqualTo("\u042E\u0430\u043D\u044C");
			assertThatJson(parsedJson).array().contains("['name']").isEqualTo("CNY");
			assertThatJson(parsedJson).array().contains("['value']").isEqualTo(0.11);
			assertThatJson(parsedJson).array().contains("['title']").isEqualTo("\u0420\u043E\u0441\u0441\u0438\u0439\u0441\u043A\u0438\u0439 \u0440\u0443\u0431\u043B\u044C");
			assertThatJson(parsedJson).array().contains("['name']").isEqualTo("RUB");
			assertThatJson(parsedJson).array().contains("['value']").isEqualTo(1.0);
			assertThatJson(parsedJson).array().contains("['title']").isEqualTo("\u0410\u043C\u0435\u0440\u0438\u043A\u0430\u043D\u0441\u043A\u0438\u0439 \u0434\u043E\u043B\u043B\u0430\u0440");
			assertThatJson(parsedJson).array().contains("['name']").isEqualTo("USD");
			assertThatJson(parsedJson).array().contains("['value']").isEqualTo(0.012);
	}

}
