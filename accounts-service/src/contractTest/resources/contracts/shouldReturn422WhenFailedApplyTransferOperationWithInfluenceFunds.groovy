package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/users/test_user1/transfer вызван и есть ошибки при попытке проведения транзакции, сервис аккаунтов должен вернуть" +
            " ответ с кодом 422, статусом FAILED и списком ошибок"
    request {
        method 'POST'
        url '/api/v1/users/test_user1/transfer'
        body([
                fromCurrency: "RUB",
                toCurrency: "CNY",
                fromAmount: 200000.0,
                toAmount: 22000.0,
                toLogin: "test_user2"
        ])

        headers {
            contentType('application/json')
            accept('application/json')
        }
    }
    response {
        status 422
        headers {
            contentType(applicationJson())
        }
        body([
                "operationStatus": "FAILED",
                "errors": ["На счете недостаточно средств"]
        ])
    }
}