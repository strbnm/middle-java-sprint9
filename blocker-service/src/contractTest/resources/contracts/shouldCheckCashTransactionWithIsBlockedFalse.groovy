package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/check_transaction вызван, сервис должен проверить операцию типа cash и вернуть значение флага блокировки false"
    request {
        method 'POST'
        url '/api/v1/check_transaction'
        body([
                transactionId: 1,
                currencyCode: 'RUB',
                source: 'account',
                target: 'cash',
                amount: 1000.0,
                operationType: 'cash'
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
                "transactionId": 1,
                "isBlocked": false,
                "reason": null
        ])
    }
}