package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/transfer вызван, сервис переводов должен попробовать провести транзакцию, взаимодействуя с сервисами аккаунтов, конвертации, блокировок и уведомлений " +
            "и вернуть ответ с кодом 422, статусом FAILED и списком ошибок"
    request {
        method 'POST'
        url '/api/v1/transfer'
        body([
                fromLogin: "test_user1",
                fromCurrency: "RUB",
                toLogin: "test_user1",
                toCurrency: "RUB",
                amount: 1000.0,
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
                "errors": ["Перевести можно только между разными счетами"]
        ])
    }
}