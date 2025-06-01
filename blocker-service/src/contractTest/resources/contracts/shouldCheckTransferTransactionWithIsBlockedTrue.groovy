package contracts

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Когда POST /api/v1/blocker/checkTransferTransaction вызван, сервис должен проверить операцию типа transfer и вернуть значение флага блокировки true"
    request {
        method 'POST'
        url '/api/v1/blocker/checkTransferTransaction'
        body([
                transactionId: anyPositiveInt(),
                fromCurrency: 'USD',
                toCurrency: 'RUB',
                amount: 6001.0,
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
                "transactionId": fromRequest().body('$.transactionId'),
                "isBlocked": true,
                "reason": "Превышена допустимая сумма перевода другим лицам"
        ])
    }
}