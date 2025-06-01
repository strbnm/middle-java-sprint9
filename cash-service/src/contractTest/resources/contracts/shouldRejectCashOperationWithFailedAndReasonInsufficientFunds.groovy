package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/cash вызван, сервис обналичивания денег должен попробовать провести транзакцию, взаимодействуя с сервисами аккаунтов, блокировок и уведомлений " +
            "и вернуть ответ с кодом 422, статусом FAILED и списком ошибок"
    request {
        method 'POST'
        url '/api/v1/cash'
        body([
                login: "test_user1",
                currency: "RUB",
                amount: 100000.0,
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
                "errors": ["На счете недостаточно средств"]
        ])
    }
}