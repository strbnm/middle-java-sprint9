package ru.strbnm.transfer_service;

import ru.strbnm.transfer_service.BaseContractTest;
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
	public void validate_shouldApllyTransferOperationSuccess() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"fromLogin\":\"test_user1\",\"fromCurrency\":\"CNY\",\"toLogin\":\"test_user2\",\"toCurrency\":\"CNY\",\"amount\":1000.0}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/transfer");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("SUCCESS");
			assertThatJson(parsedJson).array("['errors']").isEmpty();
	}

	@Test
	public void validate_shouldApllyTransferOperationSuccessWithExchange() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"fromLogin\":\"test_user1\",\"fromCurrency\":\"RUB\",\"toLogin\":\"test_user2\",\"toCurrency\":\"USD\",\"amount\":1000.0}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/transfer");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("SUCCESS");
			assertThatJson(parsedJson).array("['errors']").isEmpty();
	}

	@Test
	public void validate_shouldRejectCashOperationWithFailedAndReasonInsufficientFunds() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"fromLogin\":\"test_user1\",\"fromCurrency\":\"RUB\",\"toLogin\":\"test_user2\",\"toCurrency\":\"CNY\",\"amount\":200000.0}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/transfer");

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
					.body("{\"fromLogin\":\"test_user1\",\"fromCurrency\":\"RUB\",\"toLogin\":\"test_user1\",\"toCurrency\":\"USD\",\"amount\":1000.0}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/transfer");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u0423 \u0412\u0430\u0441 \u043E\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442 \u0441\u0447\u0435\u0442 \u0432 \u0432\u044B\u0431\u0440\u0430\u043D\u043D\u043E\u0439 \u0432\u0430\u043B\u044E\u0442\u0435").value();
	}

	@Test
	public void validate_shouldRejectTransferOperationWithFailedAndReasonSameAccounts() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"fromLogin\":\"test_user1\",\"fromCurrency\":\"RUB\",\"toLogin\":\"test_user1\",\"toCurrency\":\"RUB\",\"amount\":1000.0}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/transfer");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u041F\u0435\u0440\u0435\u0432\u0435\u0441\u0442\u0438 \u043C\u043E\u0436\u043D\u043E \u0442\u043E\u043B\u044C\u043A\u043E \u043C\u0435\u0436\u0434\u0443 \u0440\u0430\u0437\u043D\u044B\u043C\u0438 \u0441\u0447\u0435\u0442\u0430\u043C\u0438").value();
	}

}
