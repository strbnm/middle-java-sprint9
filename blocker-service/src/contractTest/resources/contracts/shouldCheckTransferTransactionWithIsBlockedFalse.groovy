package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/blocker/checkTransferTransaction вызван, сервис должен проверить операцию типа transfer и вернуть значение флага блокировки false"
    request {
        method 'POST'
        url '/api/v1/blocker/checkTransferTransaction'
        body([
                transactionId: 3,
                fromCurrency: 'RUB',
                toCurrency: 'RUB',
                amount: 1000.0,
                isItself: false
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
                "transactionId": 3,
                "isBlocked": false,
                "reason": null
        ])
    }
}