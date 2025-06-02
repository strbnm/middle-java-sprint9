package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/transfer вызван, сервис обналичивания денег должен попробовать провести транзакцию, взаимодействуя с сервисами аккаунтов, блокировок и уведомлений " +
            "и вернуть ответ с кодом 422, статусом FAILED и списком ошибок"
    request {
        method 'POST'
        url '/api/v1/transfer'
        body([
                fromLogin: "test_user1",
                fromCurrency: "RUB",
                toLogin: "test_user2",
                toCurrency: "CNY",
                amount: 200000.0,
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