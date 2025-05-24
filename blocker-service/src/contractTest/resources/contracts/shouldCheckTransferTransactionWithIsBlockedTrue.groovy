package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/check_transaction вызван, сервис должен проверить операцию типа transfer и вернуть значение флага блокировки true"
    request {
        method 'POST'
        url '/api/v1/check_transaction'
        body([
                transactionId: 4,
                from: [
                        currencyCode: 'USD',
                        source      : 'account'
                ],
                to: [
                        currencyCode: 'USD',
                        target      : 'cash'
                ],
                amount       : 2000.0,
                operationType: 'transfer',
                isToYourself: false
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
                "transactionId": 4,
                "isBlocked": true,
                "reason": "Недопустимая операция для сервиса переводов"
        ])
    }
}