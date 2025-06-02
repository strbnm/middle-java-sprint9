package ru.strbnm.accounts_service;

import ru.strbnm.accounts_service.BaseContractTest;
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
	public void validate_shouldApllyCashOperation() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"currency\":\"RUB\",\"amount\":1000.0,\"action\":\"PUT\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/users/test_user1/cash");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("SUCCESS");
			assertThatJson(parsedJson).array("['errors']").isEmpty();
	}

	@Test
	public void validate_shouldApllyTransferOperation() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"fromCurrency\":\"CNY\",\"toCurrency\":\"CNY\",\"fromAmount\":1000.0,\"toAmount\":1000.0,\"toLogin\":\"test_user2\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/users/test_user1/transfer");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("SUCCESS");
			assertThatJson(parsedJson).array("['errors']").isEmpty();
	}

	@Test
	public void validate_shouldApllyTransferOperationRUB2UDSOther() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"fromCurrency\":\"RUB\",\"toCurrency\":\"USD\",\"fromAmount\":1000.0,\"toAmount\":12.0,\"toLogin\":\"test_user2\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/users/test_user1/transfer");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("SUCCESS");
			assertThatJson(parsedJson).array("['errors']").isEmpty();
	}

	@Test
	public void validate_shouldCreateUser() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"login\":\"test_user1\",\"password\":\"$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa\",\"name\":\"\u0418\u0432\u0430\u043D\u043E\u0432 \u0418\u0432\u0430\u043D\",\"email\":\"ivanov@example.ru\",\"birthdate\":\"2000-01-01\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/users");

		// then:
			assertThat(response.statusCode()).isEqualTo(201);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("SUCCESS");
			assertThatJson(parsedJson).array("['errors']").isEmpty();
	}

	@Test
	public void validate_shouldGetUserByLogin1() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Accept", "application/json");

		// when:
			WebTestClientResponse response = given().spec(request)
					.get("/api/v1/users/test_user1");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['login']").isEqualTo("test_user1");
			assertThatJson(parsedJson).field("['password']").isEqualTo("$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa");
			assertThatJson(parsedJson).field("['name']").isEqualTo("\u0418\u0432\u0430\u043D\u043E\u0432 \u0418\u0432\u0430\u043D");
			assertThatJson(parsedJson).field("['email']").isEqualTo("ivanov@example.ru");
			assertThatJson(parsedJson).field("['birthdate']").isEqualTo("2000-05-21");
			assertThatJson(parsedJson).array("['roles']").arrayField().isEqualTo("ROLE_CLIENT").value();
			assertThatJson(parsedJson).array("['accounts']").contains("['currency']").isEqualTo("RUB");
			assertThatJson(parsedJson).array("['accounts']").contains("['value']").isEqualTo(150000.0);
			assertThatJson(parsedJson).array("['accounts']").contains("['exists']").isEqualTo(true);
			assertThatJson(parsedJson).array("['accounts']").contains("['currency']").isEqualTo("CNY");
			assertThatJson(parsedJson).array("['accounts']").contains("['value']").isEqualTo(20000.0);
			assertThatJson(parsedJson).array("['accounts']").contains("['currency']").isEqualTo("USD");
			assertThatJson(parsedJson).array("['accounts']").contains("['value']").isEqualTo(0.0);
			assertThatJson(parsedJson).array("['accounts']").contains("['exists']").isEqualTo(false);
	}

	@Test
	public void validate_shouldGetUserByLogin2() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Accept", "application/json");

		// when:
			WebTestClientResponse response = given().spec(request)
					.get("/api/v1/users/test_user2");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['login']").isEqualTo("test_user2");
			assertThatJson(parsedJson).field("['password']").isEqualTo("$2a$12$8iuXDswC26EzrjL0qWNOiuzwZi5/zGuuJY7gaEkoPnaIPZodfm.xi");
			assertThatJson(parsedJson).field("['name']").isEqualTo("\u041F\u0435\u0442\u0440\u043E\u0432 \u041F\u0435\u0442\u0440");
			assertThatJson(parsedJson).field("['email']").isEqualTo("petrov@example.ru");
			assertThatJson(parsedJson).field("['birthdate']").isEqualTo("1990-05-21");
			assertThatJson(parsedJson).array("['roles']").arrayField().isEqualTo("ROLE_CLIENT").value();
			assertThatJson(parsedJson).array("['accounts']").contains("['currency']").isEqualTo("RUB");
			assertThatJson(parsedJson).array("['accounts']").contains("['value']").isEqualTo(0.0);
			assertThatJson(parsedJson).array("['accounts']").contains("['exists']").isEqualTo(false);
			assertThatJson(parsedJson).array("['accounts']").contains("['currency']").isEqualTo("CNY");
			assertThatJson(parsedJson).array("['accounts']").contains("['value']").isEqualTo(12000.0);
			assertThatJson(parsedJson).array("['accounts']").contains("['exists']").isEqualTo(true);
			assertThatJson(parsedJson).array("['accounts']").contains("['currency']").isEqualTo("USD");
			assertThatJson(parsedJson).array("['accounts']").contains("['value']").isEqualTo(1000.0);
	}

	@Test
	public void validate_shouldGetUserList() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Accept", "application/json");

		// when:
			WebTestClientResponse response = given().spec(request)
					.get("/api/v1/users");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).array().contains("['login']").isEqualTo("test_user1");
			assertThatJson(parsedJson).array().contains("['name']").isEqualTo("\u0418\u0432\u0430\u043D\u043E\u0432 \u0418\u0432\u0430\u043D");
			assertThatJson(parsedJson).array().contains("['login']").isEqualTo("test_user2");
			assertThatJson(parsedJson).array().contains("['name']").isEqualTo("\u041F\u0435\u0442\u0440\u043E\u0432 \u041F\u0435\u0442\u0440");
	}

	@Test
	public void validate_shouldReturn404WhenGetUserByLoginNotFound() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Accept", "application/json");

		// when:
			WebTestClientResponse response = given().spec(request)
					.get("/api/v1/users/test_user4");

		// then:
			assertThat(response.statusCode()).isEqualTo(404);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['status_code']").isEqualTo(404);
			assertThatJson(parsedJson).field("['message']").isEqualTo("\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C \u0441 \u043B\u043E\u0433\u0438\u043D\u043E\u043C test_user4 \u043D\u0435 \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u0435\u0442");
	}

	@Test
	public void validate_shouldReturn422WhenFailedApplyCashOperation() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"currency\":\"RUB\",\"amount\":100000.0,\"action\":\"GET\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/users/test_user1/cash");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u041D\u0430 \u0441\u0447\u0435\u0442\u0435 \u043D\u0435\u0434\u043E\u0441\u0442\u0430\u0442\u043E\u0447\u043D\u043E \u0441\u0440\u0435\u0434\u0441\u0442\u0432").value();
	}

	@Test
	public void validate_shouldReturn422WhenFailedApplyCashOperationWithMissingCurrencyAccount() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"currency\":\"USD\",\"amount\":1000.0,\"action\":\"GET\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/users/test_user1/cash");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u0423 \u0412\u0430\u0441 \u043E\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442 \u0441\u0447\u0435\u0442 \u0432 \u0432\u044B\u0431\u0440\u0430\u043D\u043D\u043E\u0439 \u0432\u0430\u043B\u044E\u0442\u0435").value();
	}

	@Test
	public void validate_shouldReturn422WhenFailedApplyTransferOperation() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"fromCurrency\":\"RUB\",\"toCurrency\":\"RUB\",\"fromAmount\":1000.0,\"toAmount\":1000.0,\"toLogin\":\"test_user1\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/users/test_user1/transfer");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u041F\u0435\u0440\u0435\u0432\u0435\u0441\u0442\u0438 \u043C\u043E\u0436\u043D\u043E \u0442\u043E\u043B\u044C\u043A\u043E \u043C\u0435\u0436\u0434\u0443 \u0440\u0430\u0437\u043D\u044B\u043C\u0438 \u0441\u0447\u0435\u0442\u0430\u043C\u0438").value();
	}

	@Test
	public void validate_shouldReturn422WhenFailedApplyTransferOperationWithInfluenceFunds() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"fromCurrency\":\"RUB\",\"toCurrency\":\"CNY\",\"fromAmount\":200000.0,\"toAmount\":22000.0,\"toLogin\":\"test_user2\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/users/test_user1/transfer");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u041D\u0430 \u0441\u0447\u0435\u0442\u0435 \u043D\u0435\u0434\u043E\u0441\u0442\u0430\u0442\u043E\u0447\u043D\u043E \u0441\u0440\u0435\u0434\u0441\u0442\u0432").value();
	}

	@Test
	public void validate_shouldReturn422WhenFailedApplyTransferOperationWithMissingCurrencyAccount() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"fromCurrency\":\"RUB\",\"toCurrency\":\"USD\",\"fromAmount\":1000.0,\"toAmount\":12.0,\"toLogin\":\"test_user1\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/users/test_user1/transfer");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u0423 \u0412\u0430\u0441 \u043E\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u0443\u0435\u0442 \u0441\u0447\u0435\u0442 \u0432 \u0432\u044B\u0431\u0440\u0430\u043D\u043D\u043E\u0439 \u0432\u0430\u043B\u044E\u0442\u0435").value();
	}

	@Test
	public void validate_shouldReturn422WhenFailedCreateUser() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"login\":\"test_user1\",\"password\":\"$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa\",\"name\":\"\u0418\u0432\u0430\u043D\u043E\u0432 \u0418\u0432\u0430\u043D\",\"email\":\"ivanov@example.ru\",\"birthdate\":\"2010-01-01\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.post("/api/v1/users");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u0412\u0430\u043C \u0434\u043E\u043B\u0436\u043D\u043E \u0431\u044B\u0442\u044C \u0431\u043E\u043B\u044C\u0448\u0435 18 \u043B\u0435\u0442").value();
	}

	@Test
	public void validate_shouldReturn422WhenFailedUpdateUser() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"login\":\"test_user1\",\"password\":\"$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa\",\"name\":\"\",\"email\":\"test@example.ru\",\"birthdate\":\"2020-01-01\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.put("/api/v1/users/test_user1");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u0417\u0430\u043F\u043E\u043B\u043D\u0438\u0442\u0435 \u043F\u043E\u043B\u0435 \u0424\u0430\u043C\u0438\u043B\u0438\u044F \u0418\u043C\u044F").value();
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u0412\u0430\u043C \u0434\u043E\u043B\u0436\u043D\u043E \u0431\u044B\u0442\u044C \u0431\u043E\u043B\u044C\u0448\u0435 18 \u043B\u0435\u0442").value();
	}

	@Test
	public void validate_shouldReturn422WhenFailedUpdateUserPassword() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"login\":\"test_user1\",\"newPassword\":\"R7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.patch("/api/v1/users/test_user1/password");

		// then:
			assertThat(response.statusCode()).isEqualTo(422);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("FAILED");
			assertThatJson(parsedJson).array("['errors']").arrayField().isEqualTo("\u041E\u0448\u0438\u0431\u043A\u0430 \u043F\u0440\u0438 \u0441\u043E\u0445\u0440\u0430\u043D\u0435\u043D\u0438\u0438 \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u0439 \u043F\u0430\u0440\u043E\u043B\u044F. \u041E\u043F\u0435\u0440\u0430\u0446\u0438\u044F \u043E\u0442\u043C\u0435\u043D\u0435\u043D\u0430").value();
	}

	@Test
	public void validate_shouldUpdateUser() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"login\":\"test_user1\",\"password\":\"$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa\",\"name\":\"\u0418\u0432\u0430\u043D\u043E\u0432 \u0418\u0432\u0430\u043D\",\"email\":\"test@example.ru\",\"birthdate\":\"1999-01-01\",\"accounts\":[\"RUB\",\"CNY\"]}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.put("/api/v1/users/test_user1");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("SUCCESS");
			assertThatJson(parsedJson).array("['errors']").isEmpty();
	}

	@Test
	public void validate_shouldUpdateUserPassword() throws Exception {
		// given:
			WebTestClientRequestSpecification request = given()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.body("{\"login\":\"test_user1\",\"newPassword\":\"$2a$12$DpyrJV1Ob2RR7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6\"}");

		// when:
			WebTestClientResponse response = given().spec(request)
					.patch("/api/v1/users/test_user1/password");

		// then:
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.header("Content-Type")).matches("application/json.*");

		// and:
			DocumentContext parsedJson = JsonPath.parse(response.getBody().asString());
			assertThatJson(parsedJson).field("['operationStatus']").isEqualTo("SUCCESS");
			assertThatJson(parsedJson).array("['errors']").isEmpty();
	}

}
