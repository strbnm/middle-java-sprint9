package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/transfer вызван, сервис переводов должен провести транзакцию, взаимодействую с сервисами аккаунтов, конвертации, блокировки и уведомлений " +
            "и вернуть ответ с кодом 200, статусом SUCCESS и пустым списком ошибок"
    request {
        method 'POST'
        url '/api/v1/transfer'
        body([
                fromLogin: "test_user1",
                fromCurrency: "CNY",
                toLogin: "test_user2",
                toCurrency: "CNY",
                amount: 1000.0,
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