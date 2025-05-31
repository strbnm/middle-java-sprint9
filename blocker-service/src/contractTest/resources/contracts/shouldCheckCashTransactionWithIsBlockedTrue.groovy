package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/blocker/checkCashTransaction вызван, сервис должен проверить операцию типа cash и вернуть значение флага блокировки true"
    request {
        method 'POST'
        url '/api/v1/blocker/checkCashTransaction'
        body([
                transactionId: 2,
                currency: 'USD',
                amount: 2000.0,
                actionType: 'GET'
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
                "transactionId": 2,
                "isBlocked": true,
                "reason": "Превышена допустимая сумма снятия наличных"
        ])
    }
}