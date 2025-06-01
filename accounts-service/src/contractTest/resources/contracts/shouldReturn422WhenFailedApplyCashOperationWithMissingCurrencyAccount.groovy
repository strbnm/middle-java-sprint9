package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/users/test_user1/cash вызван и есть ошибки при попытке проведения транзакции, сервис аккаунтов должен вернуть" +
            " ответ с кодом 422, статусом FAILED и списком ошибок"
    request {
        method 'POST'
        url '/api/v1/users/test_user1/cash'
        body([
                currency: "USD",
                amount: 1000.0,
                action: "GET"
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
                "errors": ["У Вас отсутствует счет в выбранной валюте"]
        ])
    }
}