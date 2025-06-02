package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/blocker/checkTransferTransaction вызван, сервис должен проверить операцию типа transfer и вернуть значение флага блокировки false"
    request {
        method 'POST'
        url '/api/v1/blocker/checkTransferTransaction'
        body([
                transactionId: anyPositiveInt(),
                fromCurrency: anyOf('RUB', 'CNY', 'USD'),
                toCurrency: anyOf('RUB', 'CNY', 'USD'),
                amount: 1000.0,
                isItself: true
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
                "transactionId": fromRequest().body('$.transactionId'),
                "isBlocked": false,
                "reason": null
        ])
    }
}