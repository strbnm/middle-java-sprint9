package ru.strbnm.cash_service;

import ru.strbnm.cash_service.BaseContractTest;
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
	public void validate_shouldApllyCashOperationSuccess() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"login\":\"test_user1\",\"currency\":\"RUB\",\"amount\":1000.0,\"action\":\"PUT\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/cash");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("SUCCESS");
			assertThatJson(parsedJson).array("['errors']").isEmpty();
	}

	@Test
	public void validate_shouldRejectCashOperationWithFailedAndReasonBlocked() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"login\":\"test_user1\",\"currency\":\"USD\",\"amount\":2000.0,\"action\":\"GET\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/cash");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u041F\u0440\u0435\u0432\u044B\u0448\u0435\u043D\u0430 \u0434\u043E\u043F\u0443\u0441\u0442\u0438\u043C\u0430\u044F \u0441\u0443\u043C\u043C\u0430 \u0441\u043D\u044F\u0442\u0438\u044F \u043D\u0430\u043B\u0438\u0447\u043D\u044B\u0445").value();
	}

	@Test
	public void validate_shouldRejectCashOperationWithFailedAndReasonInsufficientFunds() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"login\":\"test_user1\",\"currency\":\"RUB\",\"amount\":100000.0,\"action\":\"GET\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/cash");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u041D\u0430 \u0441\u0447\u0435\u0442\u0435 \u043D\u0435\u0434\u043E\u0441\u0442\u0430\u0442\u043E\u0447\u043D\u043E \u0441\u0440\u0435\u0434\u0441\u0442\u0432").value();
	}

	@Test
	public void validate_shouldRejectCashOperationWithFailedAndReasonMissingCurrencyAccount() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"login\":\"test_user1\",\"currency\":\"USD\",\"amount\":1000.0,\"action\":\"GET\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/cash");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u0423 \u0412\u0430\u0441 \u043E\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442 \u0441\u0447\u0435\u0442 \u0432 \u0432\u044B\u0431\u0440\u0430\u043D\u043D\u043E\u0439 \u0432\u0430\u043B\u044E\u0442\u0435").value();
	}

}
