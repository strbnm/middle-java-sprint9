package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/users/test_user1/transfer вызван, сервис аккаунтов должен провести транзакцию " +
            "и вернуть ответ с кодом 200, статусом SUCCESS и пустым списком ошибок"
    request {
        method 'POST'
        url '/api/v1/users/test_user1/transfer'
        body([
                fromCurrency: "RUB",
                toCurrency: "USD",
                fromAmount: 1000.0,
                toAmount: 12.0,
                toLogin: "test_user2"
        ])

        headers {
            contentType('application/json')
            accept('application/json')
        }
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body([
                "operationStatus": "SUCCESS",
                "errors": []
        ])
    }
}