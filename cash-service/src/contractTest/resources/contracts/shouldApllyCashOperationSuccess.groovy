package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/cash вызван, сервис обналичивания денег должен провести транзакцию, взаимодействую с сервисами аккаунтов, блокировки и уведомлений " +
            "и вернуть ответ с кодом 200, статусом SUCCESS и пустым списком ошибок"
    request {
        method 'POST'
        url '/api/v1/cash'
        body([
                login: "test_user1",
                currency: "RUB",
                amount: 1000.0,
                action: anyOf("PUT", "GET")
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