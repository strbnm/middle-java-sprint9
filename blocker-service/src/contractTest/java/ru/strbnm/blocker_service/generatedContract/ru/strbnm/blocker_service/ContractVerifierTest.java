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
	public void validate_shouldCheckCashTransactionWithIsBlockedFalse() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"transactionId\":1,\"from\":{\"currencyCode\":\"RUB\",\"source\":\"account\"},\"to\":{\"currencyCode\":\"USD\",\"target\":\"cash\"},\"amount\":1000.0,\"operationType\":\"cash\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/check_transaction");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['transactionId']").isEqualTo(1);
			assertThatJson(parsedJson).field("['isBlocked']").isEqualTo(false);
			assertThatJson(parsedJson).field("['reason']").isNull();
	}

	@Test
	public void validate_shouldCheckCashTransactionWithIsBlockedTrue() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"transactionId\":2,\"from\":{\"currencyCode\":\"USD\",\"source\":\"account\"},\"to\":{\"currencyCode\":\"USD\",\"target\":\"cash\"},\"amount\":2000.0,\"operationType\":\"cash\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/check_transaction");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['transactionId']").isEqualTo(2);
			assertThatJson(parsedJson).field("['isBlocked']").isEqualTo(true);
			assertThatJson(parsedJson).field("['reason']").isEqualTo("\u041F\u0440\u0435\u0432\u044B\u0448\u0435\u043D\u0430 \u0434\u043E\u043F\u0443\u0441\u0442\u0438\u043C\u0430\u044F \u0441\u0443\u043C\u043C\u0430 \u0441\u043D\u044F\u0442\u0438\u044F \u043D\u0430\u043B\u0438\u0447\u043D\u044B\u0445");
	}

	@Test
	public void validate_shouldCheckTransferTransactionWithIsBlockedFalse() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"transactionId\":3,\"from\":{\"currencyCode\":\"RUB\",\"source\":\"account\"},\"to\":{\"currencyCode\":\"USD\",\"target\":\"account\"},\"amount\":1000.0,\"operationType\":\"transfer\",\"isToYourself\":false}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/check_transaction");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['transactionId']").isEqualTo(3);
			assertThatJson(parsedJson).field("['isBlocked']").isEqualTo(false);
			assertThatJson(parsedJson).field("['reason']").isNull();
	}

	@Test
	public void validate_shouldCheckTransferTransactionWithIsBlockedTrue() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"transactionId\":4,\"from\":{\"currencyCode\":\"USD\",\"source\":\"account\"},\"to\":{\"currencyCode\":\"USD\",\"target\":\"cash\"},\"amount\":2000.0,\"operationType\":\"transfer\",\"isToYourself\":false}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/check_transaction");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['transactionId']").isEqualTo(4);
			assertThatJson(parsedJson).field("['isBlocked']").isEqualTo(true);
			assertThatJson(parsedJson).field("['reason']").isEqualTo("\u041D\u0435\u0434\u043E\u043F\u0443\u0441\u0442\u0438\u043C\u0430\u044F \u043E\u043F\u0435\u0440\u0430\u0446\u0438\u044F \u0434\u043B\u044F \u0441\u0435\u0440\u0432\u0438\u0441\u0430 \u043F\u0435\u0440\u0435\u0432\u043E\u0434\u043E\u0432");
	}

}
