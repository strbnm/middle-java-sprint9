package ru.strbnm.blocker_service;

import ru.strbnm.blocker_service.BaseContractTest;
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
	public void validate_shouldCheckCashTransactionOn100000RUBWithIsBlockedFalse() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"transactionId\":117948294,\"currency\":\"RUB\",\"amount\":100000.0,\"actionType\":\"GET\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/blocker/checkCashTransaction");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['transactionId']").isEqualTo(117948294);
			assertThatJson(parsedJson).field("['isBlocked']").isEqualTo(false);
			assertThatJson(parsedJson).field("['reason']").isNull();
	}

	@Test
	public void validate_shouldCheckCashTransactionWithIsBlockedFalse() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"transactionId\":1632306614,\"currency\":\"USD\",\"amount\":1000.0,\"actionType\":\"GET\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/blocker/checkCashTransaction");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['transactionId']").isEqualTo(1632306614);
			assertThatJson(parsedJson).field("['isBlocked']").isEqualTo(false);
			assertThatJson(parsedJson).field("['reason']").isNull();
	}

	@Test
	public void validate_shouldCheckCashTransactionWithIsBlockedTrue() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"transactionId\":1307642438,\"currency\":\"USD\",\"amount\":2000.0,\"actionType\":\"GET\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/blocker/checkCashTransaction");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['transactionId']").isEqualTo(1307642438);
			assertThatJson(parsedJson).field("['isBlocked']").isEqualTo(true);
			assertThatJson(parsedJson).field("['reason']").isEqualTo("\u041F\u0440\u0435\u0432\u044B\u0448\u0435\u043D\u0430 \u0434\u043E\u043F\u0443\u0441\u0442\u0438\u043C\u0430\u044F \u0441\u0443\u043C\u043C\u0430 \u0441\u043D\u044F\u0442\u0438\u044F \u043D\u0430\u043B\u0438\u0447\u043D\u044B\u0445");
	}

	@Test
	public void validate_shouldCheckTransferTransactionItselfWithIsBlockedFalse() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"transactionId\":1834476868,\"fromCurrency\":\"USD\",\"toCurrency\":\"RUB\",\"amount\":1000.0,\"isItself\":true}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/blocker/checkTransferTransaction");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['transactionId']").isEqualTo(1834476868);
			assertThatJson(parsedJson).field("['isBlocked']").isEqualTo(false);
			assertThatJson(parsedJson).field("['reason']").isNull();
	}

	@Test
	public void validate_shouldCheckTransferTransactionOtherRUB2CNYWithIsBlockedFalse() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"transactionId\":1557316972,\"fromCurrency\":\"RUB\",\"toCurrency\":\"CNY\",\"amount\":200000.0,\"isItself\":false}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/blocker/checkTransferTransaction");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['transactionId']").isEqualTo(1557316972);
			assertThatJson(parsedJson).field("['isBlocked']").isEqualTo(false);
			assertThatJson(parsedJson).field("['reason']").isNull();
	}

	@Test
	public void validate_shouldCheckTransferTransactionOtherWithIsBlockedFalse() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"transactionId\":1737890521,\"fromCurrency\":\"CNY\",\"toCurrency\":\"RUB\",\"amount\":1000.0,\"isItself\":false}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/blocker/checkTransferTransaction");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['transactionId']").isEqualTo(1737890521);
			assertThatJson(parsedJson).field("['isBlocked']").isEqualTo(false);
			assertThatJson(parsedJson).field("['reason']").isNull();
	}

	@Test
	public void validate_shouldCheckTransferTransactionOtherWithIsBlockedTrue() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"transactionId\":1793800712,\"fromCurrency\":\"USD\",\"toCurrency\":\"RUB\",\"amount\":6001.0,\"isItself\":false}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/blocker/checkTransferTransaction");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['transactionId']").isEqualTo(1793800712);
			assertThatJson(parsedJson).field("['isBlocked']").isEqualTo(true);
			assertThatJson(parsedJson).field("['reason']").isEqualTo("\u041F\u0440\u0435\u0432\u044B\u0448\u0435\u043D\u0430 \u0434\u043E\u043F\u0443\u0441\u0442\u0438\u043C\u0430\u044F \u0441\u0443\u043C\u043C\u0430 \u043F\u0435\u0440\u0435\u0432\u043E\u0434\u0430 \u0434\u0440\u0443\u0433\u0438\u043C \u043B\u0438\u0446\u0430\u043C");
	}

}
