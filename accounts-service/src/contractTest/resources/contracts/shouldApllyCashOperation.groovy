package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/users/test_user1/cash вызван, сервис аккаунтов должен провести транзакцию " +
            "и вернуть ответ с кодом 200, статусом SUCCESS и пустым списком ошибок"
    request {
        method 'POST'
        url '/api/v1/users/test_user1/cash'
        body([
                currency: "RUB",
                amount: 1000.0,
                action: "PUT"
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